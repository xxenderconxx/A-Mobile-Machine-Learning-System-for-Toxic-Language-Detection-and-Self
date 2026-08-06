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
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "ToxicKeyboardPrefs";
    public static final String KEY_FILTERING_ENABLED = "filtering_enabled";
    private static final String KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted";
    private static final String KEY_COMPLETED_IN_APP_TUTORIAL = "has_completed_in_app_tutorial";
    public static final String KEY_COUNT_DETECTIONS = "count_detections";
    public static final String KEY_COUNT_EDITS = "count_edits";
    public static final String KEY_COUNT_IGNORED = "count_ignored";
    public static final String KEY_USER_DICTIONARY = "user_dictionary";
    public static final String KEY_USER_DICTIONARY_HISTORY = "user_dictionary_history";
    public static final String KEY_TOXIC_FEEDBACK = "toxic_feedback";
    public static final String KEY_TOXIC_FEEDBACK_HISTORY = "toxic_feedback_history";
    public static final String KEY_FALSE_POSITIVES_FEEDBACK = "false_positives_feedback";
    public static final String KEY_FALSE_POSITIVES_FEEDBACK_HISTORY = "false_positives_history";
    public static final String KEY_TOTAL_USAGE_TIME = "total_usage_time";
    private static final String KEY_LAST_VERSION_CODE = "last_version_code";
    private static final String KEY_LAST_UPLOAD_TIME = "last_upload_time";
    private static final long UPLOAD_COOLDOWN_MS = 60 * 1000; // 1 minute

    private SharedPreferences prefs;
    private TextView tvStats;
    private TextView tvFeedbackStats;
    private View viewSettings;
    private View viewContribute;
    private ToxicityClassifier classifier;
    private TextView tvToxicityPreview;
    private android.widget.ListView lvQueuedData;
    private java.util.ArrayList<String> queuedItemsList = new java.util.ArrayList<>();
    private android.widget.ArrayAdapter<String> queuedAdapter;
    private android.widget.Button btnUploadFeedback;
    
    private Button btnEnable;
    private Button btnSelect;
    private Button btnOverlay;
    private SwitchMaterial switchFiltering;
    private Button btnReset;
    private Button btnClearDict;
    private Button btnExport;
    private Button btnPrivacyInfo;
    private com.google.android.material.tabs.TabLayout tabLayout;

    private final android.os.Handler cooldownHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable cooldownRunnable = new Runnable() {
        @Override
        public void run() {
            updateCooldownButton();
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sharedPreferences, key) -> {
        if (KEY_COUNT_DETECTIONS.equals(key) || KEY_COUNT_EDITS.equals(key) || KEY_COUNT_IGNORED.equals(key)) {
            updateStatsDisplay();
        } else if (KEY_USER_DICTIONARY.equals(key) || KEY_TOXIC_FEEDBACK.equals(key) || KEY_FALSE_POSITIVES_FEEDBACK.equals(key) ||
                   KEY_USER_DICTIONARY_HISTORY.equals(key) || KEY_TOXIC_FEEDBACK_HISTORY.equals(key) || KEY_FALSE_POSITIVES_FEEDBACK_HISTORY.equals(key)) {
            updateFeedbackStats();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("has_completed_tutorial", false)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);

        checkVersionAndClearFeedback();
        checkDisclaimer(prefs);

        // UI References
        tvStats = findViewById(R.id.tv_stats);
        tvFeedbackStats = findViewById(R.id.tv_feedback_stats);
        viewSettings = findViewById(R.id.view_settings);
        viewContribute = findViewById(R.id.view_contribute);
        tabLayout = findViewById(R.id.tab_layout);

        btnEnable = findViewById(R.id.btn_enable_keyboard);
        btnSelect = findViewById(R.id.btn_select_keyboard);
        btnOverlay = findViewById(R.id.btn_overlay_permission);
        switchFiltering = findViewById(R.id.switch_filtering);
        btnReset = findViewById(R.id.btn_reset_stats);
        btnClearDict = findViewById(R.id.btn_clear_dictionary);
        btnExport = findViewById(R.id.btn_export_metrics);
        btnUploadFeedback = findViewById(R.id.btn_upload_feedback);
        btnPrivacyInfo = findViewById(R.id.btn_privacy_info);
        
        EditText etTest = findViewById(R.id.et_test);
        tvToxicityPreview = findViewById(R.id.tv_toxicity_preview);
        lvQueuedData = findViewById(R.id.lv_queued_data);
        
        queuedAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, queuedItemsList);
        lvQueuedData.setAdapter(queuedAdapter);

        lvQueuedData.setOnItemClickListener((parent, view, position, id) -> {
            String item = queuedItemsList.get(position);
            if (item.equals("No data queued for review.")) return;

            new AlertDialog.Builder(this)
                    .setTitle("Delete Contribution")
                    .setMessage("Remove this item from the upload queue?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteQueuedItem(item))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        
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
                    .remove(KEY_USER_DICTIONARY_HISTORY)
                    .remove(KEY_TOXIC_FEEDBACK_HISTORY)
                    .remove(KEY_FALSE_POSITIVES_FEEDBACK_HISTORY)
                    .apply();
            
            // Force a refresh of local UI stats
            updateFeedbackStats();
            Toast.makeText(this, R.string.toast_dictionary_cleared, Toast.LENGTH_SHORT).show();
        });

        btnExport.setOnClickListener(v -> exportResearchReport());

        btnUploadFeedback.setOnClickListener(v -> {
            long lastUpload = prefs.getLong(KEY_LAST_UPLOAD_TIME, 0);
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastUpload < UPLOAD_COOLDOWN_MS) {
                long secondsLeft = (UPLOAD_COOLDOWN_MS - (currentTime - lastUpload)) / 1000;
                Toast.makeText(this, getString(R.string.upload_cooldown_message, secondsLeft), Toast.LENGTH_LONG).show();
                return;
            }

            java.util.Set<String> safe = prefs.getStringSet(KEY_USER_DICTIONARY, new java.util.HashSet<>());
            java.util.Set<String> toxic = prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>());
            java.util.Set<String> falsePos = prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK, new java.util.HashSet<>());

            if (safe.isEmpty() && toxic.isEmpty() && falsePos.isEmpty()) {
                Toast.makeText(this, R.string.no_feedback_data, Toast.LENGTH_SHORT).show();
            } else {
                showUploadConfirmationDialog(safe, toxic, falsePos);
            }
        });

        // Set initial state
        switchFiltering.setChecked(prefs.getBoolean(KEY_FILTERING_ENABLED, true));

        switchFiltering.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_FILTERING_ENABLED, isChecked).apply();
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

        if (!prefs.getBoolean(KEY_COMPLETED_IN_APP_TUTORIAL, false)) {
            startInAppTutorial();
        }
    }

    private void startInAppTutorial() {
        TapTargetSequence sequence = new TapTargetSequence(this)
                .targets(
                        TapTarget.forView(btnEnable, getString(R.string.btn_enable_keyboard), getString(R.string.tutorial_step_1))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false),
                        TapTarget.forView(btnSelect, getString(R.string.btn_select_keyboard), getString(R.string.tutorial_step_2))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false),
                        TapTarget.forView(btnOverlay, getString(R.string.btn_overlay_permission), getString(R.string.tutorial_step_3))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false),
                        TapTarget.forView(switchFiltering, getString(R.string.switch_filtering_label), getString(R.string.tutorial_step_4))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false),
                        TapTarget.forView(btnReset, getString(R.string.btn_reset_metrics), getString(R.string.tutorial_step_5))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false),
                        TapTarget.forView(btnExport, getString(R.string.btn_export_metrics), getString(R.string.tutorial_step_6))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false),
                        TapTarget.forView(btnClearDict, getString(R.string.btn_clear_dictionary), getString(R.string.tutorial_step_7))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false),
                        TapTarget.forView(btnPrivacyInfo, getString(R.string.btn_privacy_info), getString(R.string.tutorial_step_8))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false)
                )
                .listener(new TapTargetSequence.Listener() {
                    @Override
                    public void onSequenceFinish() {
                        // Switch to Contribute tab for the next part of the tutorial
                        if (tabLayout != null && tabLayout.getTabAt(1) != null) {
                            tabLayout.getTabAt(1).select();
                            startContributeTutorial();
                        }
                    }

                    @Override
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {}

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {}
                });
        sequence.start();
    }

    private void startContributeTutorial() {
        // Find the "Contribute" tab view. TabLayout doesn't give direct access to the tab view easily,
        // so we target the tabLayout itself for Step 9 explanation if we can't find the specific view.
        // For simplicity in Step 9, we'll target the tabLayout.
        
        TapTargetSequence sequence = new TapTargetSequence(this)
                .targets(
                        TapTarget.forView(tabLayout, "Contribute Tab", getString(R.string.tutorial_step_9))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false),
                        TapTarget.forView(lvQueuedData, getString(R.string.label_queued_contributions), getString(R.string.tutorial_step_10))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false),
                        TapTarget.forView(btnUploadFeedback, getString(R.string.btn_upload), getString(R.string.tutorial_step_11))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(false)
                )
                .listener(new TapTargetSequence.Listener() {
                    @Override
                    public void onSequenceFinish() {
                        prefs.edit().putBoolean(KEY_COMPLETED_IN_APP_TUTORIAL, true).apply();
                        if (tabLayout != null && tabLayout.getTabAt(0) != null) {
                            tabLayout.getTabAt(0).select();
                        }
                        Toast.makeText(MainActivity.this, R.string.btn_tutorial_finish, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {}

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {}
                });
        sequence.start();
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
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Internet required for research upload.", Toast.LENGTH_SHORT).show();
            return;
        }

        int detections = prefs.getInt(KEY_COUNT_DETECTIONS, 0);
        int edits = prefs.getInt(KEY_COUNT_EDITS, 0);
        int ignored = prefs.getInt(KEY_COUNT_IGNORED, 0);
        long usageTime = prefs.getLong(KEY_TOTAL_USAGE_TIME, 0);
        double rate = detections > 0 ? (double) edits / detections * 100 : 0.0;

        java.util.Map<String, Object> report = new java.util.HashMap<>();
        report.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        report.put("total_detections", detections);
        report.put("user_corrections", edits);
        report.put("ignored_alerts", ignored);
        report.put("self_correction_rate", rate);
        report.put("total_usage_ms", usageTime);

        String androidId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        int userIdHash = androidId != null ? androidId.hashCode() : 0;
        report.put("userId", userIdHash);

        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
        db.collection("research_reports")
                .add(report)
                .addOnSuccessListener(documentReference -> Toast.makeText(this, "Research metrics uploaded securely.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showResearchInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.research_info_title)
                .setMessage(android.text.Html.fromHtml(getString(R.string.research_info_message), android.text.Html.FROM_HTML_MODE_COMPACT))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showUploadConfirmationDialog(java.util.Set<String> safe, java.util.Set<String> toxic, java.util.Set<String> falsePos) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.upload_confirmation_title)
                .setMessage(R.string.upload_confirmation_message)
                .setPositiveButton(R.string.btn_allow_and_upload, (dialog, which) -> performUpload(safe, toxic, falsePos))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performUpload(java.util.Set<String> safe, java.util.Set<String> toxic, java.util.Set<String> falsePos) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection available.", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject payload = createUploadPayload(safe, toxic, falsePos);
        android.util.Log.d("ToxicKeyboard", "Payload prepared: " + payload);

        Toast.makeText(this, "Connecting to secure server...", Toast.LENGTH_SHORT).show();

        // Use Firestore to upload the bulk payload as a single contribution document
        try {
            com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            int userIdHash = androidId != null ? androidId.hashCode() : 0;
            String docId = String.valueOf(userIdHash);

            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
            updates.put("userId", userIdHash);
            updates.put("safe_words", com.google.firebase.firestore.FieldValue.arrayUnion(safe.toArray()));
            updates.put("toxic_words", com.google.firebase.firestore.FieldValue.arrayUnion(toxic.toArray()));
            updates.put("false_positives", com.google.firebase.firestore.FieldValue.arrayUnion(falsePos.toArray()));

            // Use set with merge to ensure the document exists and update with arrayUnion in one call
            db.collection("contributions")
                    .document(docId)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        movePendingToHistory(safe, toxic, falsePos);
                        prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, System.currentTimeMillis()).apply();
                        Toast.makeText(this, R.string.feedback_uploaded, Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            Toast.makeText(this, "Firebase error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void movePendingToHistory(java.util.Set<String> safe, java.util.Set<String> toxic, java.util.Set<String> falsePos) {
        SharedPreferences.Editor editor = prefs.edit();
        
        // 1. Update History Sets
        addToHistory(editor, KEY_USER_DICTIONARY_HISTORY, safe);
        addToHistory(editor, KEY_TOXIC_FEEDBACK_HISTORY, toxic);
        addToHistory(editor, KEY_FALSE_POSITIVES_FEEDBACK_HISTORY, falsePos);
        
        // 2. Clear Pending Sets
        editor.remove(KEY_USER_DICTIONARY);
        editor.remove(KEY_TOXIC_FEEDBACK);
        editor.remove(KEY_FALSE_POSITIVES_FEEDBACK);
        
        editor.apply();
        updateFeedbackStats();
    }

    private void addToHistory(SharedPreferences.Editor editor, String key, java.util.Set<String> newItems) {
        if (newItems == null || newItems.isEmpty()) return;
        java.util.Set<String> history = new java.util.HashSet<>(prefs.getStringSet(key, new java.util.HashSet<>()));
        history.addAll(newItems);
        editor.putStringSet(key, history);
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

    private JSONObject createUploadPayload(java.util.Set<String> safe, java.util.Set<String> toxic, java.util.Set<String> falsePos) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("version_code", BuildConfig.VERSION_CODE);
            payload.put("timestamp", System.currentTimeMillis());
            
            JSONArray safeArray = new JSONArray();
            for (String s : safe) {
                safeArray.put(s);
            }
            payload.put("safe_words", safeArray);

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
                    .remove(KEY_USER_DICTIONARY_HISTORY)
                    .remove(KEY_TOXIC_FEEDBACK_HISTORY)
                    .remove(KEY_FALSE_POSITIVES_FEEDBACK_HISTORY)
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
        if (prefs == null) return;
        int detections = prefs.getInt(KEY_COUNT_DETECTIONS, 0);
        int edits = prefs.getInt(KEY_COUNT_EDITS, 0);
        int ignored = prefs.getInt(KEY_COUNT_IGNORED, 0);
        long totalTimeMs = prefs.getLong(KEY_TOTAL_USAGE_TIME, 0);

        // Behavioral Metric: Percentage of detections the user chose to self-correct
        double correctionRate = detections > 0 ? (double) edits / detections * 100 : 0.0;

        long totalSeconds = totalTimeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        String stats = getString(R.string.stats_format, detections, edits, ignored, correctionRate);
        String usage = "\nActive Usage: " + hours + "h " + minutes + "m " + seconds + "s (Goal: 3h)";
        tvStats.setText(stats + usage);

        if (totalTimeMs >= 3 * 60 * 60 * 1000 && !prefs.getBoolean("milestone_shown", false)) {
            showMilestoneDialog();
        }
    }

    private void showMilestoneDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Research Milestone Reached!")
                .setMessage("Thank you! You have contributed 3 hours of active keyboard usage to this study. Your participation is helping us improve behavioral self-regulation tools.")
                .setPositiveButton("Continue", (dialog, which) -> prefs.edit().putBoolean("milestone_shown", true).apply())
                .show();
    }

    private void deleteQueuedItem(String displayItem) {
        String originalText;
        String key;

        if (displayItem.startsWith("[TOXIC] ")) {
            originalText = displayItem.substring("[TOXIC] ".length());
            key = KEY_TOXIC_FEEDBACK;
        } else if (displayItem.startsWith("[FALSE POSITIVE] ")) {
            originalText = displayItem.substring("[FALSE POSITIVE] ".length());
            key = KEY_FALSE_POSITIVES_FEEDBACK;
        } else if (displayItem.startsWith("[SAFE/NEW] ")) {
            originalText = displayItem.substring("[SAFE/NEW] ".length());
            key = KEY_USER_DICTIONARY;
        } else {
            return;
        }

        java.util.Set<String> set = new java.util.HashSet<>(prefs.getStringSet(key, new java.util.HashSet<>()));
        if (set.remove(originalText)) {
            prefs.edit().putStringSet(key, set).apply();
            // updateFeedbackStats() will be called by prefListener
            Toast.makeText(this, "Item removed from queue", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFeedbackStats() {
        if (prefs == null) return;
        updateCooldownButton();
        java.util.Set<String> safe = prefs.getStringSet(KEY_USER_DICTIONARY, new java.util.HashSet<>());
        java.util.Set<String> toxic = prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>());
        java.util.Set<String> falsePos = prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK, new java.util.HashSet<>());
        
        java.util.Set<String> safeHistory = prefs.getStringSet(KEY_USER_DICTIONARY_HISTORY, new java.util.HashSet<>());
        java.util.Set<String> toxicHistory = prefs.getStringSet(KEY_TOXIC_FEEDBACK_HISTORY, new java.util.HashSet<>());
        java.util.Set<String> falsePosHistory = prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK_HISTORY, new java.util.HashSet<>());

        int totalSafe = safe.size() + safeHistory.size();
        int totalToxic = toxic.size() + toxicHistory.size();
        int totalFP = falsePos.size() + falsePosHistory.size();

        String pendingText = getString(R.string.feedback_stats_format, safe.size(), toxic.size(), falsePos.size());
        String totalText = getString(R.string.feedback_stats_total, totalSafe, totalToxic, totalFP);
        
        tvFeedbackStats.setText(pendingText + "\n" + totalText);

        // Update Queued Data List
        queuedItemsList.clear();
        for (String s : toxic) queuedItemsList.add("[TOXIC] " + s);
        for (String s : falsePos) queuedItemsList.add("[FALSE POSITIVE] " + s);
        for (String s : safe) queuedItemsList.add("[SAFE/NEW] " + s);
        
        if (queuedItemsList.isEmpty()) {
            queuedItemsList.add("No data queued for review.");
        }
        queuedAdapter.notifyDataSetChanged();
    }

    private void updateCooldownButton() {
        if (btnUploadFeedback == null) return;

        long lastUpload = prefs.getLong(KEY_LAST_UPLOAD_TIME, 0);
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastUpload;

        if (diff < UPLOAD_COOLDOWN_MS) {
            long secondsLeft = (UPLOAD_COOLDOWN_MS - diff) / 1000;
            btnUploadFeedback.setEnabled(false);
            btnUploadFeedback.setText(getString(R.string.btn_upload_cooldown, secondsLeft));
            cooldownHandler.postDelayed(cooldownRunnable, 1000);
        } else {
            btnUploadFeedback.setEnabled(true);
            btnUploadFeedback.setText(R.string.btn_upload);
            cooldownHandler.removeCallbacks(cooldownRunnable);
        }
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
        cooldownHandler.removeCallbacks(cooldownRunnable);
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
