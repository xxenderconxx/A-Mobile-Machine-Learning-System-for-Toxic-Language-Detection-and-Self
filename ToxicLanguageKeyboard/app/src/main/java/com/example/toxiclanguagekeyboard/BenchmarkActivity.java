package com.example.toxiclanguagekeyboard;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Does not require keyboard setup, overlay permission or network access. */
public class BenchmarkActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Button run, share;
    private TextView result;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benchmark);
        run = findViewById(R.id.btn_run_benchmark);
        share = findViewById(R.id.btn_share_benchmark);
        result = findViewById(R.id.benchmark_result);
        showSavedResult();
        run.setOnClickListener(view -> runBenchmark());
        share.setOnClickListener(view -> {
            try {
                startActivity(Intent.createChooser(shareIntent(this), getString(R.string.benchmark_share)));
            } catch (Exception error) {
                Toast.makeText(this, R.string.benchmark_share_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showSavedResult() {
        try {
            JSONObject report = new JSONObject(new String(Files.readAllBytes(
                    new File(getFilesDir(), DeviceBenchmark.RESULT_FILE).toPath()), StandardCharsets.UTF_8));
            // Earlier instrumentation files lack the protocol/version needed for remote comparisons.
            if (!"revision-core-v1".equals(report.optString("protocol"))) return;
            result.setText(getString(R.string.benchmark_result,
                    report.getString("device"), report.getString("android"),
                    report.getJSONObject("classifier").getDouble("median_ms"),
                    report.getJSONObject("core_detection").getDouble("median_ms"),
                    report.getJSONObject("core_detection").getDouble("p95_ms")));
            share.setEnabled(true);
        } catch (Exception ignored) {
            share.setEnabled(false);
        }
    }

    private void runBenchmark() {
        run.setEnabled(false);
        share.setEnabled(false);
        result.setText(R.string.benchmark_running);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Context context = getApplicationContext();
        worker.execute(() -> {
            boolean success;
            try {
                DeviceBenchmark.run(context);
                success = true;
            } catch (Exception error) {
                success = false;
            }
            final boolean completed = success;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                run.setEnabled(true);
                if (completed) showSavedResult();
                else result.setText(R.string.benchmark_failed);
            });
        });
    }

    static Intent shareIntent(Context context) throws Exception {
        File saved = new File(context.getFilesDir(), DeviceBenchmark.RESULT_FILE);
        JSONObject report = new JSONObject(new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8));
        if (!"revision-core-v1".equals(report.optString("protocol"))) throw new IllegalStateException("Run a benchmark first");
        File folder = new File(context.getCacheDir(), "benchmark-exports");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Cannot prepare results");
        String device = report.getString("device").replaceAll("[^A-Za-z0-9_-]", "_");
        String date = report.getString("completed_at_utc").replaceAll("[^0-9TZ]", "");
        File exported = new File(folder, "benchmark-" + device + "-" + date + ".json");
        Files.copy(saved.toPath(), exported.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".benchmark.files", exported);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newRawUri("Benchmark results", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
