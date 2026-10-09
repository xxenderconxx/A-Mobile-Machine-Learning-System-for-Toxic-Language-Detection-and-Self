package com.example.toxiclanguagekeyboard;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteBenchmarkTest {
    @Test public void screenRunsAndSharesReadableTimingFile() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        // MIUI blocks activity launches from a background test process; foreground the app first.
        try (android.os.ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(
                "am start -W -n " + context.getPackageName() + "/.MainActivity");
             java.io.FileInputStream output = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[4096];
            while (output.read(buffer) != -1) { }
        }
        instrumentation.waitForIdleSync();
        BenchmarkActivity activity = (BenchmarkActivity) instrumentation.startActivitySync(
                new Intent(context, BenchmarkActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(() -> activity.findViewById(R.id.btn_run_benchmark).performClick());
            AtomicBoolean complete = new AtomicBoolean(false);
            long deadline = System.nanoTime() + 60_000_000_000L;
            while (System.nanoTime() < deadline && !complete.get()) {
                instrumentation.runOnMainSync(() -> complete.set(
                        ((Button) activity.findViewById(R.id.btn_share_benchmark)).isEnabled()));
                if (!complete.get()) Thread.sleep(100);
            }
            assertTrue("Benchmark screen did not finish", complete.get());
            Intent sharing = BenchmarkActivity.shareIntent(activity);
            assertEquals(Intent.ACTION_SEND, sharing.getAction());
            assertEquals("application/json", sharing.getType());
            assertTrue((sharing.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            Uri uri = sharing.getParcelableExtra(Intent.EXTRA_STREAM);
            assertEquals("content", uri.getScheme());
            assertEquals(uri, sharing.getClipData().getItemAt(0).getUri());
            String exported;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer,0,count);
                exported = output.toString(StandardCharsets.UTF_8.name());
            }
            JSONObject report = new JSONObject(exported);
            assertEquals("revision-core-v1", report.getString("protocol"));
            assertEquals(BuildConfig.VERSION_NAME, report.getString("app_version"));
            assertEquals(600, report.getInt("recorded_trials"));
            assertEquals(100, report.getInt("warmup_trials"));
            assertEquals("f0eeee70d671abe9e6c1e3c95306391178b830a7d3e1eac3c07e12ede53de16a",report.getString("model_sha256"));
            assertTrue(report.getJSONObject("classifier").getDouble("median_ms") > 0);
            assertTrue(report.getJSONObject("core_detection").getDouble("p95_ms") > 0);
            assertFalse(exported.contains("stupid"));
            assertFalse(report.has("messages"));
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
