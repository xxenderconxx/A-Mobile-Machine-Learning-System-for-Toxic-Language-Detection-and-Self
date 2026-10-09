package com.example.toxiclanguagekeyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
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
    private static final String KEY_DISCLAIMER_ACCEPTED = "privacy_notice_v2_accepted";
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
    private boolean uploadInProgress;
    private TextView tvStats;
    private TextView tvFeedbackStats;
    private View viewSettings;
    private View viewContribute;
    private ListView lvQueuedData;
    private java.util.List<String> queuedItemsList = new java.util.ArrayList<>();
    private ArrayAdapter<String> queuedAdapter;
    private Button btnUploadFeedback;
    private Button btnSkipTutorial;
    private TapTargetSequence activeSequence;
    
    private Button btnEnable;
    private Button btnSelect;
    private Button btnOverlay;
    private SwitchMaterial switchFiltering;
    private Button btnReset;
    private Button btnClearDict;
    private Button btnExport;
    private Button btnPrivacyInfo;
    private ImageButton btnHelp;
    private com.google.android.material.tabs.TabLayout tabLayout;
    private int currentTutorialStep;

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

        prefs = SecurePreferences.open(this);
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
        btnSkipTutorial = findViewById(R.id.btn_skip_tutorial);
        btnHelp = findViewById(R.id.btn_help);
        findViewById(R.id.btn_open_benchmark).setOnClickListener(v ->
                startActivity(new Intent(this, BenchmarkActivity.class)));

        // Move skip button to DecorView to ensure it stays above the TapTargetView overlay
        btnSkipTutorial.post(() -> {
            ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
            ViewGroup currentParent = (ViewGroup) btnSkipTutorial.getParent();
            if (currentParent != null && currentParent != decorView) {
                currentParent.removeView(btnSkipTutorial);
                decorView.addView(btnSkipTutorial);
            }
        });
        
        btnSkipTutorial.setOnClickListener(v -> finishInAppTutorial(true));
        btnHelp.setOnClickListener(v -> {
            if (activeSequence != null) {
                activeSequence.cancel();
            }
            if (tabLayout != null && tabLayout.getTabAt(0) != null) {
                tabLayout.getTabAt(0).select();
            }
            startInAppTutorial();
        });
        
        EditText etTest = findViewById(R.id.et_test);
        lvQueuedData = findViewById(R.id.lv_queued_data);
        
        queuedAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, queuedItemsList);
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
        currentTutorialStep = 0;
        btnSkipTutorial.setVisibility(View.VISIBLE);
        
        View[] targets = {btnEnable, btnSelect, btnOverlay, switchFiltering, btnReset, btnExport, btnClearDict, btnPrivacyInfo};
        updateSkipButtonPosition(targets[0]);

        activeSequence = new TapTargetSequence(this)
                .targets(
                        TapTarget.forView(btnEnable, getString(R.string.btn_enable_keyboard), getString(R.string.tutorial_step_1))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true),
                        TapTarget.forView(btnSelect, getString(R.string.btn_select_keyboard), getString(R.string.tutorial_step_2))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true),
                        TapTarget.forView(btnOverlay, getString(R.string.btn_overlay_permission), getString(R.string.tutorial_step_3))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true),
                        TapTarget.forView(switchFiltering, getString(R.string.switch_filtering_label), getString(R.string.tutorial_step_4))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true),
                        TapTarget.forView(btnReset, getString(R.string.btn_reset_metrics), getString(R.string.tutorial_step_5))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true),
                        TapTarget.forView(btnExport, getString(R.string.btn_export_metrics), getString(R.string.tutorial_step_6))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true),
                        TapTarget.forView(btnClearDict, getString(R.string.btn_clear_dictionary), getString(R.string.tutorial_step_7))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true),
                        TapTarget.forView(btnPrivacyInfo, getString(R.string.btn_privacy_info), getString(R.string.tutorial_step_8))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true)
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
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                        currentTutorialStep++;
                        if (currentTutorialStep < targets.length) {
                            updateSkipButtonPosition(targets[currentTutorialStep]);
                        }
                    }

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {
                        finishInAppTutorial(true);
                    }
                });
        activeSequence.start();
    }

    private void startContributeTutorial() {
        currentTutorialStep = 0;
        btnSkipTutorial.setVisibility(View.VISIBLE);
        
        View[] targets = {tvFeedbackStats, lvQueuedData, btnUploadFeedback};
        updateSkipButtonPosition(targets[0]);

        activeSequence = new TapTargetSequence(this)
                .targets(
                        TapTarget.forView(tvFeedbackStats, "Pending Categories", getString(R.string.tutorial_step_9))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true),
                        TapTarget.forView(lvQueuedData, getString(R.string.label_queued_contributions), getString(R.string.tutorial_step_10))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true),
                        TapTarget.forView(btnUploadFeedback, getString(R.string.btn_upload), getString(R.string.tutorial_step_11))
                                .outerCircleColor(R.color.color_primary)
                                .targetCircleColor(android.R.color.white)
                                .titleTextColor(android.R.color.white)
                                .descriptionTextColor(android.R.color.white)
                                .transparentTarget(true)
                                .cancelable(true)
                )
                .listener(new TapTargetSequence.Listener() {
                    @Override
                    public void onSequenceFinish() {
                        finishInAppTutorial(false);
                    }

                    @Override
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                        currentTutorialStep++;
                        if (currentTutorialStep < targets.length) {
                            updateSkipButtonPosition(targets[currentTutorialStep]);
                        }
                    }

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {
                        finishInAppTutorial(true);
                    }
                });
        activeSequence.start();
    }

    private void updateSkipButtonPosition(View targetView) {
        if (targetView == null || btnSkipTutorial == null) return;
        
        targetView.post(() -> {
            View parent = (View) btnSkipTutorial.getParent();
            if (parent == null) return;

            int[] parentLocation = new int[2];
            parent.getLocationInWindow(parentLocation);
            
            int[] targetLocation = new int[2];
            targetView.getLocationInWindow(targetLocation);
            
            int viewHeight = targetView.getHeight();
            int viewWidth = targetView.getWidth();
            
            btnSkipTutorial.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int skipWidth = btnSkipTutorial.getMeasuredWidth();
            
            float density = getResources().getDisplayMetrics().density;
            
            // X and Y relative to the parent FrameLayout
            float x = (targetLocation[0] - parentLocation[0]) + (viewWidth / 2f) - (skipWidth / 2f);
            float y = (targetLocation[1] - parentLocation[1]) + viewHeight + (12 * density); // 12dp margin
            
            // Ensure button doesn't go off screen horizontally
            float margin = 16 * density;
            float parentWidth = parent.getWidth();
            x = Math.max(margin, x);
            if (x + skipWidth > parentWidth - margin) {
                x = parentWidth - skipWidth - margin;
            }
            
            // Ensure button doesn't go off screen vertically (bottom)
            float parentHeight = parent.getHeight();
            int skipHeight = btnSkipTutorial.getMeasuredHeight();
            if (y + skipHeight > parentHeight - margin) {
                // If it goes off bottom, try placing it above the target
                y = (targetLocation[1] - parentLocation[1]) - skipHeight - (12 * density);
            }

            btnSkipTutorial.setX(x);
            btnSkipTutorial.setY(y);
            
            // Aggressive Z-ordering to stay above the TapTargetView overlay
            btnSkipTutorial.setElevation(1000f);
            btnSkipTutorial.setZ(1000f);
            btnSkipTutorial.bringToFront();
            
            // Re-apply bringToFront after a short delay to ensure we are above the newly added TapTargetView
            btnSkipTutorial.postDelayed(() -> {
                btnSkipTutorial.bringToFront();
                btnSkipTutorial.requestLayout();
            }, 100);
        });
    }

    private void finishInAppTutorial(boolean canceledByUser) {
        prefs.edit().putBoolean(KEY_COMPLETED_IN_APP_TUTORIAL, true).apply();
        if (btnSkipTutorial != null) {
            btnSkipTutorial.setVisibility(View.GONE);
        }
        if (canceledByUser && activeSequence != null) {
            TapTargetSequence seq = activeSequence;
            activeSequence = null;
            seq.cancel();
        }
        if (!canceledByUser) {
            if (tabLayout != null && tabLayout.getTabAt(0) != null) {
                tabLayout.getTabAt(0).select();
            }
            Toast.makeText(MainActivity.this, R.string.btn_tutorial_finish, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateLiveToxicity(String text) {
        // Method kept for logic but UI preview removed per cleanup task
    }

    private void exportResearchReport() {
        new AlertDialog.Builder(this)
                .setTitle("Upload research metrics?")
                .setMessage("Send detection, successful-edit and continue counts, correction rate, active keyboard time, an authenticated installation ID and submission time to Firebase. No message text is included in this report. Research administrators can retain the report; clearing local data does not delete server records.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Upload", (dialog, which) -> uploadResearchReport())
                .show();
    }

    private void uploadResearchReport() {
        if (uploadInProgress || !isNetworkAvailable()) {
            Toast.makeText(this, "Connect to the internet and wait for any current upload.", Toast.LENGTH_SHORT).show();
            return;
        }
        int detections = prefs.getInt(KEY_COUNT_DETECTIONS, 0);
        java.util.Map<String, Object> report = new java.util.HashMap<>();
        report.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        report.put("total_detections", detections);
        report.put("user_corrections", prefs.getInt(KEY_COUNT_EDITS, 0));
        report.put("ignored_alerts", prefs.getInt(KEY_COUNT_IGNORED, 0));
        report.put("self_correction_rate", detections > 0 ? 100.0 * prefs.getInt(KEY_COUNT_EDITS, 0) / detections : 0.0);
        report.put("total_usage_ms", prefs.getLong(KEY_TOTAL_USAGE_TIME, 0));
        uploadInProgress = true;
        ContributionClient.authenticated(uid -> {
            report.put("userId", uid);
            ContributionClient.database().collection("research_reports").add(report)
                    .addOnSuccessListener(ref -> {
                        uploadInProgress = false;
                        Toast.makeText(this, "Research metrics uploaded.", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(this::uploadFailed);
        }, this::uploadFailed);
    }

    private void uploadFailed(Exception failure) {
        uploadInProgress = false;
        Toast.makeText(this, "Upload failed. Your pending items remain on this device.", Toast.LENGTH_LONG).show();
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
        if (uploadInProgress || !isNetworkAvailable()) {
            Toast.makeText(this, "Connect to the internet and wait for any current upload.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Freeze exactly the items reviewed by the user, not sets changing during the request.
        java.util.Set<String> safeSnapshot = new java.util.HashSet<>(safe);
        java.util.Set<String> toxicSnapshot = new java.util.HashSet<>(toxic);
        java.util.Set<String> fpSnapshot = new java.util.HashSet<>(falsePos);
        uploadInProgress = true;
        ContributionClient.authenticated(uid -> {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
            data.put("userId", uid);
            data.put("safe_words", com.google.firebase.firestore.FieldValue.arrayUnion(safeSnapshot.toArray()));
            data.put("toxic_words", com.google.firebase.firestore.FieldValue.arrayUnion(toxicSnapshot.toArray()));
            data.put("false_positives", com.google.firebase.firestore.FieldValue.arrayUnion(fpSnapshot.toArray()));
            ContributionClient.database().collection("contributions").document(uid)
                    .set(data, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(unused -> {
                        uploadInProgress = false;
                        movePendingToHistory(safeSnapshot, toxicSnapshot, fpSnapshot);
                        prefs.edit().putLong(KEY_LAST_UPLOAD_TIME, System.currentTimeMillis()).apply();
                        Toast.makeText(this, R.string.feedback_uploaded, Toast.LENGTH_LONG).show();
                    }).addOnFailureListener(this::uploadFailed);
        }, this::uploadFailed);
    }

    private void movePendingToHistory(java.util.Set<String> safe, java.util.Set<String> toxic, java.util.Set<String> falsePos) {
        SharedPreferences.Editor editor = prefs.edit();
        
        // 1. Update History Sets
        addToHistory(editor, KEY_USER_DICTIONARY_HISTORY, safe);
        addToHistory(editor, KEY_TOXIC_FEEDBACK_HISTORY, toxic);
        addToHistory(editor, KEY_FALSE_POSITIVES_FEEDBACK_HISTORY, falsePos);
        
        // 2. Clear Pending Sets
        removeSubmitted(editor, KEY_USER_DICTIONARY, safe);
        removeSubmitted(editor, KEY_TOXIC_FEEDBACK, toxic);
        removeSubmitted(editor, KEY_FALSE_POSITIVES_FEEDBACK, falsePos);
        
        editor.apply();
        updateFeedbackStats();
    }

    private void removeSubmitted(SharedPreferences.Editor editor, String key, java.util.Set<String> submitted) {
        java.util.Set<String> pending = new java.util.HashSet<>(prefs.getStringSet(key, new java.util.HashSet<>()));
        pending.removeAll(submitted);
        editor.putStringSet(key, pending);
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
        // App upgrades must preserve personal refinements and unsubmitted contributions.
        prefs.edit().putInt(KEY_LAST_VERSION_CODE, BuildConfig.VERSION_CODE).apply();
    }

    private void updateStatsDisplay() {
        if (prefs == null || tvStats == null) return;
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
                .setMessage("Thank you! This device has recorded 3 hours of active keyboard usage locally. Uploading metrics is optional. Your participation is helping us improve behavioral self-regulation tools.")
                .setPositiveButton("Continue", (dialog, which) -> prefs.edit().putBoolean("milestone_shown", true).apply())
                .show();
    }

    private void deleteQueuedItem(String displayItem) {
        String originalText;
        String key;

        if (displayItem.startsWith("[TOXIC] ")) {
            originalText = displayItem.substring("[TOXIC] ".length());
            key = KEY_TOXIC_FEEDBACK;
        } else if (displayItem.startsWith("[NOT HARMFUL] ")) {
            originalText = displayItem.substring("[NOT HARMFUL] ".length());
            key = KEY_FALSE_POSITIVES_FEEDBACK;
        } else if (displayItem.startsWith("[CLEAN CONTENT] ")) {
            originalText = displayItem.substring("[CLEAN CONTENT] ".length());
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
        if (prefs == null || tvFeedbackStats == null || queuedAdapter == null) return;
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
        for (String s : falsePos) queuedItemsList.add("[NOT HARMFUL] " + s);
        for (String s : safe) queuedItemsList.add("[CLEAN CONTENT] " + s);
        
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
