package com.example.toxiclanguagekeyboard;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

/** Fixed core benchmark shared by the standalone screen and instrumentation. */
public final class DeviceBenchmark {
    public static final String RESULT_FILE = "revision-device-benchmark.json";
    private static final String[] SAMPLES = {"Please pass the salt.", "You are stupid.", "You did a great job.",
            "They are all idiots.", "This is an ordinary English sentence.", "You are stuuupid."};
    private DeviceBenchmark() {}

    public static JSONObject run(Context context) throws Exception {
        ToxicityClassifier classifier;
        SubstitutionEngine rules;
        try (InputStream input = context.getAssets().open("model_weights.json")) { classifier = new ToxicityClassifier(input); }
        try (InputStream input = context.getAssets().open("substitutions.json")) { rules = new SubstitutionEngine(input); }
        if (!classifier.isLoaded() || !rules.isLoaded()) throw new IllegalStateException("Detector could not load");
        DetectionPipeline pipeline = new DetectionPipeline(classifier, rules);
        for (int i = 0; i < 100; i++) {
            checkCancelled();
            pipeline.evaluate(SAMPLES[i % SAMPLES.length], Collections.emptySet(), false);
        }
        long[] inference = new long[600], detection = new long[600];
        for (int i = 0; i < inference.length; i++) {
            checkCancelled();
            String text = SAMPLES[i % SAMPLES.length];
            long start = System.nanoTime();
            classifier.calculateProbability(text, true, false);
            inference[i] = System.nanoTime() - start;
            detection[i] = pipeline.evaluate(text, Collections.emptySet(), false).pipelineNanos;
        }
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        JSONObject report = new JSONObject();
        report.put("protocol", "revision-core-v1");
        report.put("completed_at_utc", Instant.now().toString());
        report.put("app_version", BuildConfig.VERSION_NAME);
        report.put("app_version_code", BuildConfig.VERSION_CODE);
        report.put("build_type", BuildConfig.BUILD_TYPE);
        report.put("model_sha256", assetHash(context, "model_weights.json"));
        report.put("rules_sha256", assetHash(context, "substitutions.json"));
        report.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        report.put("android", Build.VERSION.RELEASE);
        report.put("api", Build.VERSION.SDK_INT);
        report.put("ram_bytes", memory.totalMem);
        report.put("processor", Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : Build.HARDWARE);
        report.put("warmup_trials", 100);
        report.put("recorded_trials", 600);
        report.put("sample_count", SAMPLES.length);
        report.put("timing_method", "System.nanoTime, sequential trials, fixed six samples, model loading excluded");
        report.put("classifier", stats(inference));
        report.put("core_detection", stats(detection));
        report.put("scope", "Core shared model/rule decision path; excludes unknown-word lookup, IME dispatch, 300 ms debounce and alert rendering.");
        checkCancelled();
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), RESULT_FILE));
        FileOutputStream output = file.startWrite();
        try {
            output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception error) {
            file.failWrite(output);
            throw error;
        }
        return report;
    }

    private static void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private static String assetHash(Context context, String name) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = context.getAssets().open(name)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return hex.toString();
    }

    private static JSONObject stats(long[] ns) throws Exception {
        long sum = 0;
        for (long value : ns) sum += value;
        Arrays.sort(ns);
        JSONObject values = new JSONObject();
        values.put("mean_ms", sum / (double) ns.length / 1e6);
        values.put("median_ms", (ns[ns.length / 2 - 1] + ns[ns.length / 2]) / 2.0 / 1e6);
        values.put("p95_ms", ns[(int) Math.ceil(ns.length * 0.95) - 1] / 1e6);
        return values;
    }
}
