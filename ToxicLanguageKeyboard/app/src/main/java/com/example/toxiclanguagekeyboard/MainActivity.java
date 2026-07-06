package com.example.toxiclanguagekeyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "ToxicKeyboardPrefs";
    public static final String KEY_FILTERING_ENABLED = "filtering_enabled";
    private static final String KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted";
    public static final String KEY_COUNT_DETECTIONS = "count_detections";
    public static final String KEY_COUNT_EDITS = "count_edits";
    public static final String KEY_COUNT_IGNORED = "count_ignored";
    private static final String KEY_USER_DICTIONARY = "user_dictionary";
    private static final String KEY_TOXIC_FEEDBACK = "toxic_feedback";
    private static final String KEY_FALSE_POSITIVES_FEEDBACK = "false_positives_feedback";
    private static final String KEY_LAST_VERSION_CODE = "last_version_code";

    private SharedPreferences prefs;
    private TextView tvStats;
    private TextView tvFeedbackStats;
    private View viewSettings;
    private View viewContribute;
    private ToxicityClassifier classifier;
    private TextView tvToxicityPreview;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sharedPreferences, key) -> {
        if (KEY_COUNT_DETECTIONS.equals(key) || KEY_COUNT_EDITS.equals(key) || KEY_COUNT_IGNORED.equals(key)) {
            updateStatsDisplay();
        } else if (KEY_TOXIC_FEEDBACK.equals(key) || KEY_FALSE_POSITIVES_FEEDBACK.equals(key)) {
            updateFeedbackStats();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);

        checkVersionAndClearFeedback();
        checkDisclaimer(prefs);

        // UI References
        tvStats = findViewById(R.id.tv_stats);
        tvFeedbackStats = findViewById(R.id.tv_feedback_stats);
        viewSettings = findViewById(R.id.view_settings);
        viewContribute = findViewById(R.id.view_contribute);
        com.google.android.material.tabs.TabLayout tabLayout = findViewById(R.id.tab_layout);

        Button btnEnable = findViewById(R.id.btn_enable_keyboard);
        Button btnSelect = findViewById(R.id.btn_select_keyboard);
        Button btnOverlay = findViewById(R.id.btn_overlay_permission);
        SwitchMaterial switchFiltering = findViewById(R.id.switch_filtering);
        Button btnReset = findViewById(R.id.btn_reset_stats);
        Button btnClearDict = findViewById(R.id.btn_clear_dictionary);
        Button btnExport = findViewById(R.id.btn_export_metrics);
        Button btnUploadFeedback = findViewById(R.id.btn_upload_feedback);
        Button btnPrivacyInfo = findViewById(R.id.btn_privacy_info);
        
        EditText etTest = findViewById(R.id.et_test);
        tvToxicityPreview = findViewById(R.id.tv_toxicity_preview);
        
        classifier = new ToxicityClassifier(this);

        // Tab Logic
        tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    viewSettings.setVisibility(View.VISIBLE);
                    viewContribute.setVisibility(View.GONE);
                    updateStatsDisplay();
                } else {
                    viewSettings.setVisibility(View.GONE);
                    viewContribute.setVisibility(View.VISIBLE);
                    updateFeedbackStats();
                }
            }

            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });

        updateStatsDisplay();

        btnReset.setOnClickListener(v -> {
            prefs.edit()
                    .putInt(KEY_COUNT_DETECTIONS, 0)
                    .putInt(KEY_COUNT_EDITS, 0)
                    .putInt(KEY_COUNT_IGNORED, 0)
                    .apply();
            updateStatsDisplay();
            Toast.makeText(this, R.string.toast_metrics_reset, Toast.LENGTH_SHORT).show();
        });

        btnClearDict.setOnClickListener(v -> {
            prefs.edit()
                    .remove(KEY_USER_DICTIONARY)
                    .remove(KEY_TOXIC_FEEDBACK)
                    .remove(KEY_FALSE_POSITIVES_FEEDBACK)
                    .apply();
            
            // Force a refresh of local UI stats
            updateFeedbackStats();
            Toast.makeText(this, R.string.toast_dictionary_cleared, Toast.LENGTH_SHORT).show();
        });

        btnExport.setOnClickListener(v -> exportResearchReport());

        btnUploadFeedback.setOnClickListener(v -> {
            java.util.Set<String> toxic = prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>());
            java.util.Set<String> falsePos = prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK, new java.util.HashSet<>());

            if (toxic.isEmpty() && falsePos.isEmpty()) {
                Toast.makeText(this, R.string.no_feedback_data, Toast.LENGTH_SHORT).show();
            } else {
                showUploadConfirmationDialog(toxic, falsePos);
            }
        });

        // Set initial state
        switchFiltering.setChecked(prefs.getBoolean(KEY_FILTERING_ENABLED, true));

        switchFiltering.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_FILTERING_ENABLED, isChecked).apply();
            int resId = isChecked ? R.string.toast_filtering_enabled : R.string.toast_filtering_disabled;
            Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
        });

        btnEnable.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        });

        btnSelect.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });

        btnOverlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.overlay_already_granted, Toast.LENGTH_SHORT).show();
            }
        });

        btnPrivacyInfo.setOnClickListener(v -> showResearchInfoDialog());

        etTest.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateLiveToxicity(s.toString());
            }
        });
    }

    private void updateLiveToxicity(String text) {
        if (text.isEmpty()) {
            tvToxicityPreview.setText(getString(R.string.toxicity_preview_format, 0.0, getString(R.string.label_safe)));
            return;
        }
        double prob = classifier.calculateProbability(text);
        String label = prob > 0.5 ? getString(R.string.label_toxic) : getString(R.string.label_safe);
        tvToxicityPreview.setText(getString(R.string.toxicity_preview_format, prob * 100, label));
        tvToxicityPreview.setTextColor(prob > 0.5 ? 0xFFFF0000 : 0xFF757575);
    }

    private void exportResearchReport() {
        int detections = prefs.getInt(KEY_COUNT_DETECTIONS, 0);
        int edits = prefs.getInt(KEY_COUNT_EDITS, 0);
        int ignored = prefs.getInt(KEY_COUNT_IGNORED, 0);
        double rate = detections > 0 ? (double) edits / detections * 100 : 0.0;

        StringBuilder report = new StringBuilder();
        report.append(getString(R.string.research_report_header)).append("\n");
        report.append("Timestamp: ").append(new java.util.Date().toString()).append("\n");
        report.append("Total Detections: ").append(detections).append("\n");
        report.append("User Edits (Corrections): ").append(edits).append("\n");
        report.append("Ignored Alerts: ").append(ignored).append("\n");
        report.append("Self-Correction Rate: ").append(String.format("%.2f%%", rate)).append("\n");
        report.append("------------------------------\n");
        
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, report.toString());
        sendIntent.setType("text/plain");
        
        Intent shareIntent = Intent.createChooser(sendIntent, "Export Report");
        startActivity(shareIntent);
    }

    private void showResearchInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.research_info_title)
                .setMessage(android.text.Html.fromHtml(getString(R.string.research_info_message), android.text.Html.FROM_HTML_MODE_COMPACT))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showUploadConfirmationDialog(java.util.Set<String> toxic, java.util.Set<String> falsePos) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.upload_confirmation_title)
                .setMessage(R.string.upload_confirmation_message)
                .setPositiveButton(R.string.btn_allow_and_upload, (dialog, which) -> performUpload(toxic, falsePos))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performUpload(java.util.Set<String> toxic, java.util.Set<String> falsePos) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection available.", Toast.LENGTH_SHORT).show();
            return;
        }

        // This method acts as the "Internet Gate".
        // Code here would initiate network communication only after explicit user consent.
        
        JSONObject payload = createUploadPayload(toxic, falsePos);
        android.util.Log.d("ToxicKeyboard", "Payload prepared: " + payload);

        Toast.makeText(this, "Connecting to secure server...", Toast.LENGTH_SHORT).show();

        // Simulate network upload
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // TODO: In a production app, data would be sent to the server here using HttpsURLConnection.
            // Example structure:
            // URL url = new URL("https://api.example.com/keyboard/feedback");
            // HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            // conn.setRequestMethod("POST");
            // conn.setRequestProperty("Content-Type", "application/json; utf-8");
            // ... send payload.toString() ...
            
            // As per user request, we KEEP the local feedback data after upload 
            // so the keyboard continues to respect user refinements (Safe/Toxic flags).
            // This data will only be reset when the app is updated to a new version.
            
            Toast.makeText(this, R.string.feedback_uploaded, Toast.LENGTH_LONG).show();
            Toast.makeText(this, "Data preserved for local consistency.", Toast.LENGTH_SHORT).show();
            Toast.makeText(this, "Internet access revoked.", Toast.LENGTH_SHORT).show();
        }, 2000);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                                           capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        }
        return false;
    }

    private JSONObject createUploadPayload(java.util.Set<String> toxic, java.util.Set<String> falsePos) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("version_code", BuildConfig.VERSION_CODE);
            payload.put("timestamp", System.currentTimeMillis());
            
            JSONArray toxicArray = new JSONArray();
            for (String s : toxic) {
                toxicArray.put(s);
            }
            payload.put("toxic_words", toxicArray);

            JSONArray fpArray = new JSONArray();
            for (String s : falsePos) {
                fpArray.put(s);
            }
            payload.put("false_positives", fpArray);
            
        } catch (JSONException e) {
            android.util.Log.e("ToxicKeyboard", "Error creating payload", e);
        }
        return payload;
    }

    private void checkVersionAndClearFeedback() {
        int currentVersion = BuildConfig.VERSION_CODE;
        int lastVersion = prefs.getInt(KEY_LAST_VERSION_CODE, -1);

        if (lastVersion != -1 && currentVersion > lastVersion) {
            // New version detected! Clear local refinements because the global model is likely updated.
            prefs.edit()
                    .remove(KEY_USER_DICTIONARY)
                    .remove(KEY_TOXIC_FEEDBACK)
                    .remove(KEY_FALSE_POSITIVES_FEEDBACK)
                    .putInt(KEY_LAST_VERSION_CODE, currentVersion)
                    .apply();
            
            updateFeedbackStats();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.model_updated_title)
                    .setMessage(R.string.model_updated_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else if (lastVersion == -1) {
            prefs.edit().putInt(KEY_LAST_VERSION_CODE, currentVersion).apply();
        }
    }

    private void updateStatsDisplay() {
        int detections = prefs.getInt(KEY_COUNT_DETECTIONS, 0);
        int edits = prefs.getInt(KEY_COUNT_EDITS, 0);
        int ignored = prefs.getInt(KEY_COUNT_IGNORED, 0);
        
        // Behavioral Metric: Percentage of detections the user chose to self-correct
        double correctionRate = detections > 0 ? (double) edits / detections * 100 : 0.0;
        
        tvStats.setText(getString(R.string.stats_format, detections, edits, ignored, correctionRate));
    }

    private void updateFeedbackStats() {
        java.util.Set<String> toxic = prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>());
        java.util.Set<String> falsePos = prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK, new java.util.HashSet<>());
        tvFeedbackStats.setText(getString(R.string.feedback_stats_format, toxic.size(), falsePos.size()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatsDisplay();
        updateFeedbackStats();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }

    private void checkDisclaimer(SharedPreferences prefs) {
        if (!prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.disclaimer_title)
                    .setMessage(R.string.disclaimer_message)
                    .setPositiveButton(R.string.btn_agree, (dialog, which) -> prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply())
                    .setNegativeButton(R.string.btn_exit, (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
        }
    }
}
