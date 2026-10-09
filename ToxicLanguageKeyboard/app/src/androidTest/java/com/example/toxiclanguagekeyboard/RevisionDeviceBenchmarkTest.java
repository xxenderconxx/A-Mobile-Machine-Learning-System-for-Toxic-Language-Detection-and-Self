package com.example.toxiclanguagekeyboard;

import android.content.Context;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Same benchmark as the remote test screen. */
public class RevisionDeviceBenchmarkTest {
    @Test public void benchmark() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONObject report = DeviceBenchmark.run(context);
        assertEquals(600, report.getInt("recorded_trials"));
        assertEquals(100, report.getInt("warmup_trials"));
        assertEquals("revision-core-v1", report.getString("protocol"));
        assertEquals(64, report.getString("model_sha256").length());
        Log.i("RevisionBenchmark", report.toString());
    }
}
