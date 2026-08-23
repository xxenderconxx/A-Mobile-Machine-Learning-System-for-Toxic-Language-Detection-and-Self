package com.example.toxiclanguagekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import android.text.InputType;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.provider.Settings;

public class ToxicKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private Keyboard symbolsKeyboard;
    private Keyboard numpadKeyboard;
    private Keyboard symbolsExtKeyboard;
    private View emojiPanelView;
    private boolean isEmojiPanelShowing = false;
    private EmojiAdapter emojiAdapter;
    private List<String> recentEmojis = new ArrayList<>();
    private Keyboard clipboardKeyboard;
    private boolean isCaps = false;
    private boolean isCapsLock = false;
    private long lastShiftClickTime = 0;
    private long lastSpaceClickTime = 0;
    private long keyboardStartTime = 0;
    private static final long DOUBLE_TAP_TIMEOUT = 300;
    private final StringBuilder inputBuffer = new StringBuilder();
    private SubstitutionEngine substitutionEngine;
    private VocabularyEngine vocabularyEngine;
    private ToxicityClassifier toxicityClassifier;
    private View safeIndicatorDot;
    private TextView safeIndicatorText;

    private List<String> clipboardHistory = new ArrayList<>();
    private ClipboardManager clipboardManager;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable classificationRunnable;
    private String lastCheckedText = "";
    private SharedPreferences prefs;
    private SharedPreferences encryptedPrefs;
    private WindowManager overlayWindowManager;
    private Context windowContext;
    private java.util.Set<String> userDictionary;
    private java.util.Set<String> toxicFeedbackSet;
    private java.util.Set<String> falsePositivesSet;
    private static final String PREFS_NAME = "ToxicKeyboardPrefs";
    private static final String KEY_USER_DICTIONARY = "user_dictionary";
    private static final String KEY_USER_DICTIONARY_HISTORY = "user_dictionary_history";
    private static final String KEY_FILTERING_ENABLED = "filtering_enabled";
    private static final String KEY_COUNT_DETECTIONS = "count_detections";
    private static final String KEY_COUNT_EDITS = "count_edits";
    private static final String KEY_COUNT_IGNORED = "count_ignored";
    private static final String KEY_TOXIC_FEEDBACK = "toxic_feedback";
    private static final String KEY_TOXIC_FEEDBACK_HISTORY = "toxic_feedback_history";
    private static final String KEY_FALSE_POSITIVES_FEEDBACK = "false_positives_feedback";
    private static final String KEY_FALSE_POSITIVES_FEEDBACK_HISTORY = "false_positives_history";
    private static final String PARTIAL_EMAIL_REGEX = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";

    @Override
    public void onCreate() {
        super.onCreate();
        substitutionEngine = new SubstitutionEngine(this);
        vocabularyEngine = new VocabularyEngine(this);
        executorService.execute(() -> vocabularyEngine.loadDictionary(this));
        toxicityClassifier = new ToxicityClassifier(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    this,
                    "secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            Log.e("ToxicKeyboard", "Error initializing EncryptedSharedPreferences, clearing corrupted data", e);
            deleteSharedPreferences("secure_prefs");
            try {
                MasterKey masterKey = new MasterKey.Builder(this)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                encryptedPrefs = EncryptedSharedPreferences.create(
                        this,
                        "secure_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception ex) {
                encryptedPrefs = prefs;
            }
        }

        loadUserDictionary();
        loadToxicFeedback();
        loadFalsePositives();
        loadClipboardHistory();
        loadRecentEmojis();
        prefs.registerOnSharedPreferenceChangeListener(prefListener);

        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.addPrimaryClipChangedListener(this::onClipboardChanged);
        }
    }

    private void loadClipboardHistory() {
        String history = encryptedPrefs.getString("clipboard_history", "");
        if (!history.isEmpty()) {
            String[] items = history.split("\u0001");
            clipboardHistory = new ArrayList<>(java.util.Arrays.asList(items));
        }
    }

    private void saveClipboardHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clipboardHistory.size(); i++) {
            sb.append(clipboardHistory.get(i));
            if (i < clipboardHistory.size() - 1) sb.append("\u0001");
        }
        encryptedPrefs.edit().putString("clipboard_history", sb.toString()).apply();
    }

    private void onClipboardChanged() {
        ClipData clip = clipboardManager.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence text = clip.getItemAt(0).getText();
            if (text != null) {
                String val = text.toString();
                if (!clipboardHistory.contains(val)) {
                    clipboardHistory.add(0, val);
                    if (clipboardHistory.size() > 10) clipboardHistory.remove(10);
                    saveClipboardHistory();
                }
            }
        }
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (sharedPreferences, key) -> {
        if (KEY_USER_DICTIONARY.equals(key) || KEY_USER_DICTIONARY_HISTORY.equals(key)) {
            loadUserDictionary();
        } else if (KEY_TOXIC_FEEDBACK.equals(key) || KEY_TOXIC_FEEDBACK_HISTORY.equals(key)) {
            loadToxicFeedback();
        } else if (KEY_FALSE_POSITIVES_FEEDBACK.equals(key) || KEY_FALSE_POSITIVES_FEEDBACK_HISTORY.equals(key)) {
            loadFalsePositives();
        }
    };

    private void loadUserDictionary() {
        java.util.Set<String> pending = prefs.getStringSet(KEY_USER_DICTIONARY, new java.util.HashSet<>());
        java.util.Set<String> history = prefs.getStringSet(KEY_USER_DICTIONARY_HISTORY, new java.util.HashSet<>());
        userDictionary = new java.util.HashSet<>(pending);
        userDictionary.addAll(history);

        if (substitutionEngine != null) {
            substitutionEngine.setDynamicSafeWords(userDictionary);
        }
    }

    private void loadToxicFeedback() {
        java.util.Set<String> pending = prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>());
        java.util.Set<String> history = prefs.getStringSet(KEY_TOXIC_FEEDBACK_HISTORY, new java.util.HashSet<>());
        toxicFeedbackSet = new java.util.HashSet<>(pending);
        toxicFeedbackSet.addAll(history);

        if (substitutionEngine != null) {
            substitutionEngine.setDynamicToxicWords(toxicFeedbackSet);
        }
    }

    private void loadFalsePositives() {
        java.util.Set<String> pending = prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK, new java.util.HashSet<>());
        java.util.Set<String> history = prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK_HISTORY, new java.util.HashSet<>());
        falsePositivesSet = new java.util.HashSet<>(pending);
        falsePositivesSet.addAll(history);
    }

    private void loadRecentEmojis() {
        String emojis = prefs.getString("recent_emojis", "");
        if (!emojis.isEmpty()) {
            recentEmojis = new ArrayList<>(java.util.Arrays.asList(emojis.split(",")));
        }
    }

    private void setupEmojiPanel(View root) {
        if (emojiPanelView == null) return;

        androidx.recyclerview.widget.RecyclerView recyclerView = emojiPanelView.findViewById(R.id.emoji_recycler);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 7));
        
        emojiAdapter = new EmojiAdapter(new ArrayList<>(), emoji -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(emoji, 1);
                // Add to recent emojis
                if (!recentEmojis.contains(emoji)) {
                    recentEmojis.add(0, emoji);
                    if (recentEmojis.size() > 50) recentEmojis.remove(recentEmojis.size() - 1);
                    prefs.edit().putString("recent_emojis", android.text.TextUtils.join(",", recentEmojis)).apply();
                }
            }
        });
        recyclerView.setAdapter(emojiAdapter);

        // Category Buttons
        LinearLayout categories = emojiPanelView.findViewById(R.id.emoji_categories);
        Map<String, List<String>> allEmojis = EmojiLibrary.getCategorizedEmojis(null);
        List<String> categoryNames = new ArrayList<>(allEmojis.keySet());
        
        for (int i = 0; i < categories.getChildCount(); i++) {
            View child = categories.getChildAt(i);
            if (child instanceof ImageButton) {
                final int index = i;
                child.setOnClickListener(v -> {
                    if (index == 0) {
                        emojiAdapter.updateData(recentEmojis);
                    } else if (index - 1 < categoryNames.size()) {
                        emojiAdapter.updateData(allEmojis.get(categoryNames.get(index - 1)));
                    }
                });
            }
        }

        // Action Buttons
        emojiPanelView.findViewById(R.id.btn_emoji_back).setOnClickListener(v -> hideEmojiPanel());
        emojiPanelView.findViewById(R.id.btn_emoji_to_abc).setOnClickListener(v -> hideEmojiPanel());
        emojiPanelView.findViewById(R.id.btn_emoji_del).setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                CharSequence before = ic.getTextBeforeCursor(2, 0);
                if (before != null && before.length() > 0) {
                    int deleteCount = 1;
                    if (before.length() == 2 && Character.isSurrogatePair(before.charAt(0), before.charAt(1))) {
                        deleteCount = 2;
                    }
                    ic.deleteSurroundingText(deleteCount, 0);
                }
            }
        });

        // Default data
        if (!recentEmojis.isEmpty()) {
            emojiAdapter.updateData(recentEmojis);
        } else {
            List<String> all = EmojiLibrary.getAllEmojis(null);
            emojiAdapter.updateData(all.subList(0, Math.min(all.size(), 100)));
        }

        emojiPanelView.setVisibility(View.GONE);
    }

    private void showEmojiPanel() {
        if (emojiPanelView != null) {
            isEmojiPanelShowing = true;
            emojiPanelView.setVisibility(View.VISIBLE);
            if (keyboardView != null) {
                keyboardView.setVisibility(View.GONE);
            }
        }
    }

    private void hideEmojiPanel() {
        if (emojiPanelView != null) {
            isEmojiPanelShowing = false;
            emojiPanelView.setVisibility(View.GONE);
            if (keyboardView != null) {
                keyboardView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public View onCreateInputView() {
        android.view.ContextThemeWrapper themeWrapper = new android.view.ContextThemeWrapper(this, R.style.Theme_ToxicLanguageKeyboard);
        View root = LayoutInflater.from(themeWrapper).inflate(R.layout.keyboard_view, null);
        keyboardView = root.findViewById(R.id.keyboard_view);
        emojiPanelView = root.findViewById(R.id.emoji_panel);

        setupEmojiPanel(root);
        safeIndicatorDot = root.findViewById(R.id.safe_indicator_dot);
        safeIndicatorText = root.findViewById(R.id.safe_indicator_text);

        qwertyKeyboard = new Keyboard(this, R.xml.qwerty);
        symbolsKeyboard = new Keyboard(this, R.xml.symbols);
        symbolsExtKeyboard = new Keyboard(this, R.xml.symbols_ext);
        numpadKeyboard = new Keyboard(this, R.xml.numpad);
        clipboardKeyboard = new Keyboard(this, R.xml.clipboard);

        keyboardView.setKeyboard(qwertyKeyboard);
        keyboardView.setOnKeyboardActionListener(this);
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboardStartTime == 0) {
            keyboardStartTime = System.currentTimeMillis();
        }
        inputBuffer.setLength(0);
        lastCheckedText = "";
        resetKeyboardState();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        saveUsageTime();
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        if (keyboardStartTime == 0) {
            keyboardStartTime = System.currentTimeMillis();
        }
        if (keyboardView != null) {
            keyboardView.setOnKeyboardActionListener(this);
        }
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        saveUsageTime();
    }

    private void saveUsageTime() {
        if (keyboardStartTime > 0) {
            long duration = System.currentTimeMillis() - keyboardStartTime;
            if (duration > 0) {
                long totalTime = prefs.getLong(MainActivity.KEY_TOTAL_USAGE_TIME, 0);
                prefs.edit().putLong(MainActivity.KEY_TOTAL_USAGE_TIME, totalTime + duration).apply();
            }
            keyboardStartTime = 0;
        }
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                  int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);

        if (newSelStart == newSelEnd) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                CharSequence before = ic.getTextBeforeCursor(100, 0);
                if (before != null) {
                    String currentText = before.toString();
                    if (!currentText.equals(lastCheckedText)) {
                        if (!currentText.isEmpty()) {
                            char lastChar = currentText.charAt(currentText.length() - 1);
                            if (lastChar == ' ' || lastChar == '.' || lastChar == '!' || lastChar == '?') {
                                syncBufferAndCheck(currentText);
                            }
                        }
                    }
                }
            }
        }
    }

    private void syncBufferAndCheck(String context) {
        lastCheckedText = context;
        inputBuffer.setLength(0);
        inputBuffer.append(context);
        checkToxicityAsync(context.endsWith(".") || context.endsWith("!") || context.endsWith("?"));
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                if (ic.getSelectedText(0) != null) {
                    ic.commitText("", 1);
                } else {
                    CharSequence before = ic.getTextBeforeCursor(2, 0);
                    if (before != null && before.length() > 0) {
                        int deleteCount = 1;
                        if (before.length() == 2 && Character.isSurrogatePair(before.charAt(0), before.charAt(1))) {
                            deleteCount = 2;
                        }

                        if (inputBuffer.length() > 0) {
                            int bufLen = inputBuffer.length();
                            inputBuffer.delete(Math.max(0, bufLen - deleteCount), bufLen);
                        }
                        ic.deleteSurroundingText(deleteCount, 0);
                    }
                }
                break;
            case Keyboard.KEYCODE_SHIFT:
                handleShift();
                break;
            case 32:
                handleSpace();
                break;
            case Keyboard.KEYCODE_DONE:
            case 10:
                ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
                checkToxicityAsync(true);
                resetKeyboardState();
                break;
            case Keyboard.KEYCODE_MODE_CHANGE:
                if (keyboardView.getKeyboard() == qwertyKeyboard) {
                    keyboardView.setKeyboard(symbolsKeyboard);
                } else if (keyboardView.getKeyboard() == symbolsExtKeyboard) {
                    keyboardView.setKeyboard(symbolsKeyboard);
                } else {
                    keyboardView.setKeyboard(qwertyKeyboard);
                }
                break;
            case -300:
                keyboardView.setKeyboard(symbolsExtKeyboard);
                break;
            case -3:
                if (keyboardView.getKeyboard() == symbolsKeyboard || keyboardView.getKeyboard() == symbolsExtKeyboard) {
                    keyboardView.setKeyboard(numpadKeyboard);
                } else {
                    keyboardView.setKeyboard(symbolsKeyboard);
                }
                break;
            case -100:
                showEmojiPanel();
                break;
            case -101:
                updateClipboardKeyboard();
                keyboardView.setKeyboard(clipboardKeyboard);
                break;
            case -102:
                toggleToxicityClassifier();
                break;
            default:
                if (primaryCode <= -200 && primaryCode > -210) {
                    int index = -primaryCode - 200;
                    if (index < clipboardHistory.size()) {
                        ic.commitText(clipboardHistory.get(index), 1);
                    }
                    return;
                }
                if (primaryCode > 1000) {
                    String emoji = new String(Character.toChars(primaryCode));
                    ic.commitText(emoji, 1);
                    return;
                }
                char code = (char) primaryCode;
                if (Character.isLetter(code) && isCaps) {
                    code = Character.toUpperCase(code);
                }

                EditorInfo currentInfo = getCurrentInputEditorInfo();
                boolean isSensitive = false;
                if (currentInfo != null) {
                    int inputType = currentInfo.inputType;
                    int classType = inputType & InputType.TYPE_MASK_CLASS;
                    int variation = inputType & InputType.TYPE_MASK_VARIATION;
                    isSensitive = (classType == InputType.TYPE_CLASS_TEXT && 
                            (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                             variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                             variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                             variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                             variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)) ||
                            (classType == InputType.TYPE_CLASS_NUMBER && 
                             variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                }

                if (!isSensitive) {
                    inputBuffer.append(code);
                }
                ic.commitText(String.valueOf(code), 1);
                if (isCaps && !isCapsLock) {
                    isCaps = false;
                    qwertyKeyboard.setShifted(false);
                    updateShiftKeyState();
                }
                break;
        }
    }

    private void handleShift() {
        long now = System.currentTimeMillis();
        if (now - lastShiftClickTime < DOUBLE_TAP_TIMEOUT) {
            isCapsLock = !isCapsLock;
            isCaps = isCapsLock;
        } else {
            if (isCapsLock) {
                isCapsLock = false;
                isCaps = false;
            } else {
                isCaps = !isCaps;
            }
        }
        lastShiftClickTime = now;
        qwertyKeyboard.setShifted(isCaps);
        updateShiftKeyState();
    }

    private void handleSpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        long now = System.currentTimeMillis();
        if (now - lastSpaceClickTime < DOUBLE_TAP_TIMEOUT) {
            ic.deleteSurroundingText(1, 0);
            ic.commitText(". ", 1);
            inputBuffer.setLength(Math.max(0, inputBuffer.length() - 1));
            inputBuffer.append(". ");
            checkToxicityAsync(true);

            isCaps = true;
            qwertyKeyboard.setShifted(true);
            updateShiftKeyState();
        } else {
            ic.commitText(" ", 1);
            inputBuffer.append(" ");
            checkToxicityAsync(false);
        }
        lastSpaceClickTime = now;
    }

    private void updateShiftKeyState() {
        Keyboard.Key shiftKey = findKeyByCode(Keyboard.KEYCODE_SHIFT);
        if (shiftKey != null) {
            if (isCapsLock) {
                shiftKey.icon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_shift_caps, null);
            } else if (isCaps) {
                shiftKey.icon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_shift_on, null);
            } else {
                shiftKey.icon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_shift_off, null);
            }
        }
        keyboardView.invalidateAllKeys();
    }

    private void resetKeyboardState() {
        isCaps = false;
        isCapsLock = false;
        if (qwertyKeyboard != null && keyboardView != null) {
            qwertyKeyboard.setShifted(false);
            keyboardView.setKeyboard(qwertyKeyboard);
            updateShiftKeyState();
            keyboardView.setVisibility(View.VISIBLE);
        }
        if (emojiPanelView != null) {
            emojiPanelView.setVisibility(View.GONE);
        }
        isEmojiPanelShowing = false;
    }

    private Keyboard.Key findKeyByCode(int code) {
        if (keyboardView == null || keyboardView.getKeyboard() == null) return null;
        for (Keyboard.Key key : keyboardView.getKeyboard().getKeys()) {
            if (key.codes.length > 0 && key.codes[0] == code) return key;
        }
        return null;
    }

    private void toggleToxicityClassifier() {
        boolean isEnabled = prefs.getBoolean(KEY_FILTERING_ENABLED, true);
        isEnabled = !isEnabled;
        prefs.edit().putBoolean(KEY_FILTERING_ENABLED, isEnabled).apply();
        String status = isEnabled ? "Toxicity Classifier Enabled" : "Toxicity Classifier Disabled";
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
    }

    private void updateClipboardKeyboard() {
        if (clipboardKeyboard == null) return;
        List<Keyboard.Key> keys = clipboardKeyboard.getKeys();
        for (int i = 0; i < keys.size(); i++) {
            Keyboard.Key k = keys.get(i);
            if (k.codes.length > 0 && k.codes[0] == -2) continue;

            int clipIndex = i - 1;
            if (clipIndex >= 0 && clipIndex < clipboardHistory.size()) {
                k.label = clipboardHistory.get(clipIndex);
                k.codes = new int[]{ -200 - clipIndex };
            } else if (clipIndex >= 0) {
                k.label = "[Empty]";
                k.codes = new int[]{ 0 };
            }
        }
    }

    private String getLastWords(String text, int maxWords) {
        if (text == null || text.isEmpty()) return "";
        
        int wordCount = 0;
        int i = text.length() - 1;
        
        // Find the end of the last word (skip trailing whitespace)
        int lastWordEnd = i;
        while (lastWordEnd >= 0 && Character.isWhitespace(text.charAt(lastWordEnd))) {
            lastWordEnd--;
        }
        
        if (lastWordEnd < 0) return text; // Only whitespace or empty
        
        int currentPos = lastWordEnd;
        while (currentPos >= 0) {
            // Find start of current word
            while (currentPos >= 0 && !Character.isWhitespace(text.charAt(currentPos))) {
                currentPos--;
            }
            wordCount++;
            
            if (wordCount == maxWords) {
                return text.substring(currentPos + 1);
            }
            
            // Move to previous word (skip whitespace between words)
            while (currentPos >= 0 && Character.isWhitespace(text.charAt(currentPos))) {
                currentPos--;
            }
        }
        
        return text;
    }

    private void checkToxicityAsync(boolean isEndOfSentence) {
        if (!prefs.getBoolean(KEY_FILTERING_ENABLED, true)) {
            if (isEndOfSentence) inputBuffer.setLength(0);
            return;
        }

        EditorInfo info = getCurrentInputEditorInfo();
        if (info != null) {
            int inputType = info.inputType;
            int classType = inputType & InputType.TYPE_MASK_CLASS;
            int variation = inputType & InputType.TYPE_MASK_VARIATION;

            boolean isPassword = (classType == InputType.TYPE_CLASS_TEXT && 
                    (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                     variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                     variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                    (classType == InputType.TYPE_CLASS_NUMBER && 
                     variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);

            boolean isEmailField = classType == InputType.TYPE_CLASS_TEXT && 
                    (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                     variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);

            if (isPassword || isEmailField) {
                if (isEndOfSentence) inputBuffer.setLength(0);
                mainHandler.post(() -> updateSafeIndicator(true));
                return;
            }
        }

        if (classificationRunnable != null) {
            mainHandler.removeCallbacks(classificationRunnable);
        }

        String fullText = inputBuffer.toString();

        boolean isProfessional = false;
        if (info != null && info.packageName != null) {
            String pkg = info.packageName.toLowerCase();
            isProfessional = pkg.contains("linkedin") ||
                    pkg.contains("gmail") ||
                    pkg.contains("outlook") ||
                    pkg.contains("slack") ||
                    pkg.contains("teams") ||
                    pkg.contains("workplace");
        }
        final boolean finalIsProfessional = isProfessional;

        String filteredText = fullText.replaceAll(PARTIAL_EMAIL_REGEX, "");
        String contextText = getLastWords(filteredText, 7);

        if (contextText.trim().isEmpty()) {
            if (isEndOfSentence) inputBuffer.setLength(0);
            return;
        }

        classificationRunnable = () -> executorService.execute(() -> {
            String cleanContext = contextText.toLowerCase().replaceAll("[^a-z'\\s]", "").trim();

            if (substitutionEngine.isLoaded()) {
                if (isWhitelistedInEngine(contextText)) {
                    if (isEndOfSentence) inputBuffer.setLength(0);
                    mainHandler.post(() -> updateSafeIndicator(true));
                    return;
                }
            }

            if (falsePositivesSet.contains(cleanContext)) {
                if (isEndOfSentence) inputBuffer.setLength(0);
                mainHandler.post(() -> updateSafeIndicator(true));
                return;
            }

            // STEP 1: Explicit Toxic Check (substitutions.json)
            boolean hasExplicitToxic = false;
            String[] wordsInContext = cleanContext.split("\\s+");

            for (String w : wordsInContext) {
                if (w.isEmpty()) continue;
                if (isToxicResilient(w) && !userDictionary.contains(w)) {
                    hasExplicitToxic = true;
                    break;
                }
            }

            // Also check for toxic phrases/skeletons via SubstitutionEngine
            if (!hasExplicitToxic && substitutionEngine.containsToxicLanguage(contextText)) {
                hasExplicitToxic = true;
            }

            // STEP 2: Evaluate ML Classifier
            boolean isMlToxic = toxicityClassifier.isToxic(contextText, true, finalIsProfessional);

            // If EITHER manual list OR ML classifier flags the text, halt and display Toxic Warning
            if (hasExplicitToxic || isMlToxic) {
                mainHandler.post(() -> {
                    updateSafeIndicator(false);
                    showToxicAlert(contextText);
                });
                return; // STOP execution here. Do NOT process vocabulary checks.
            }

            // STEP 3: Fallback Vocabulary Check (Only reached when text is 100% NON-TOXIC)
            for (String rawWord : wordsInContext) {
                if (rawWord.isEmpty()) continue;

                // Check if word is recognized by WordNet, User Dictionary, or common safe lists
                if (isUnrecognized(rawWord)) {
                    // Word is missing from all datasets -> Flag as Unknown Word
                    mainHandler.post(() -> showUnrecognizedAlert(rawWord));
                    // Break after first unrecognized word to avoid multiple dialogs
                    break;
                }
            }

            mainHandler.post(() -> updateSafeIndicator(true));
            if (isEndOfSentence) {
                inputBuffer.setLength(0);
            }
        });
        
        mainHandler.postDelayed(classificationRunnable, 300);
    }

    private boolean isWhitelistedInEngine(String text) {
        return substitutionEngine.isWhitelistedPhrase(text);
    }

    private void updateSafeIndicator(boolean isSafe) {
        if (safeIndicatorDot != null && safeIndicatorText != null) {
            mainHandler.post(() -> {
                if (isSafe) {
                    safeIndicatorDot.setBackgroundResource(R.drawable.indicator_dot_green);
                    safeIndicatorText.setText(R.string.label_safe);
                } else {
                    safeIndicatorDot.setBackgroundResource(R.drawable.indicator_dot_red);
                    safeIndicatorText.setText(R.string.label_toxic);
                }
            });
        }
    }

    private boolean isUnrecognized(String word) {
        if (word.length() <= 2) return false;
        String lower = word.toLowerCase();
        if (isKnownResilient(lower)) return false;
        if (isToxicResilient(lower)) return false;
        if (vocabularyEngine != null && vocabularyEngine.isLoaded() && vocabularyEngine.isKnownWord(lower)) return false;
        if (vocabularyEngine != null && !vocabularyEngine.isLoaded()) return false; // Default to safe until loaded
        return true;
    }

    private boolean isKnownResilient(String word) {
        return substitutionEngine.isKnownWord(word);
    }

    private boolean isToxicResilient(String word) {
        return substitutionEngine.isToxicWord(word);
    }

    private WindowManager getOverlayWindowManager() {
        if (overlayWindowManager == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    windowContext = createWindowContext(getDisplay(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
                    overlayWindowManager = (WindowManager) windowContext.getSystemService(Context.WINDOW_SERVICE);
                } catch (Exception e) {
                    Log.e("ToxicKeyboard", "Failed to create window context on API 31+", e);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    windowContext = createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
                    overlayWindowManager = (WindowManager) windowContext.getSystemService(Context.WINDOW_SERVICE);
                } catch (Exception e) {
                    Log.e("ToxicKeyboard", "Failed to create window context on API 30", e);
                }
            }

            if (overlayWindowManager == null) {
                overlayWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            }
        }
        return overlayWindowManager;
    }

    private void showToxicAlert(String toxicText) {
        WindowManager windowManager = getOverlayWindowManager();
        if (windowManager == null) return;

        LayoutInflater inflater = (windowContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ? LayoutInflater.from(windowContext)
                : (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View dialogView = inflater.inflate(R.layout.toxic_alert_dialog, null);

        int windowType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowContext == null)
                ? WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);

        params.dimAmount = 0.6f;
        params.gravity = Gravity.CENTER;

        TextView messageView = dialogView.findViewById(R.id.alert_message);
        Button btnEdit = dialogView.findViewById(R.id.btn_edit);
        Button btnProceed = dialogView.findViewById(R.id.btn_proceed);
        Button btnFalsePositive = dialogView.findViewById(R.id.btn_false_positive);

        String suggestion = substitutionEngine.getReframedSentence(toxicText);
        if (messageView != null) {
            messageView.setText("It looks like you're using potentially harmful language. \n\nDetected: \"" + toxicText + "\"\n\nSuggested: \"" + suggestion + "\"");
        }

        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    // Replace the toxic text with the suggestion
                    ic.deleteSurroundingText(toxicText.length(), 0);
                    if (!suggestion.isEmpty()) {
                        ic.commitText(suggestion, 1);
                    }

                    // Update inputBuffer to reflect the change
                    int start = Math.max(0, inputBuffer.length() - toxicText.length());
                    inputBuffer.delete(start, inputBuffer.length());
                    if (!suggestion.isEmpty()) {
                        inputBuffer.append(suggestion);
                    }
                }
                prefs.edit().putInt(KEY_COUNT_EDITS, prefs.getInt(KEY_COUNT_EDITS, 0) + 1).apply();
                try { windowManager.removeView(dialogView); } catch (Exception ignored) {}
            });
        }

        if (btnProceed != null) {
            btnProceed.setOnClickListener(v -> {
                prefs.edit().putInt(KEY_COUNT_IGNORED, prefs.getInt(KEY_COUNT_IGNORED, 0) + 1).apply();
                try { windowManager.removeView(dialogView); } catch (Exception ignored) {}
            });
        }

        if (btnFalsePositive != null) {
            btnFalsePositive.setOnClickListener(v -> {
                String cleanContext = toxicText.toLowerCase().replaceAll("[^a-z'\\s]", "").trim();
                java.util.Set<String> pending = new java.util.HashSet<>(prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK, new java.util.HashSet<>()));
                pending.add(cleanContext);
                prefs.edit().putStringSet(KEY_FALSE_POSITIVES_FEEDBACK, pending).apply();
                try { windowManager.removeView(dialogView); } catch (Exception ignored) {}
            });
        }

        try {
            windowManager.addView(dialogView, params);
            prefs.edit().putInt(KEY_COUNT_DETECTIONS, prefs.getInt(KEY_COUNT_DETECTIONS, 0) + 1).apply();
        } catch (Exception e) {
            Log.e("ToxicKeyboard", "Error showing toxic alert overlay", e);
        }
    }

    private void showUnrecognizedAlert(String word) {
        WindowManager windowManager = getOverlayWindowManager();

        LayoutInflater inflater = (windowContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ? LayoutInflater.from(windowContext)
                : (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View dialogView = inflater.inflate(R.layout.unrecognized_word_dialog, null);

        int windowType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowContext == null)
                ? WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);

        params.dimAmount = 0.5f;
        params.gravity = Gravity.CENTER;

        TextView messageView = dialogView.findViewById(R.id.unrecognized_message);
        Button btnIgnore = dialogView.findViewById(R.id.btn_ignore);
        Button btnAdd = dialogView.findViewById(R.id.btn_add_to_dictionary);
        Button btnMarkToxic = dialogView.findViewById(R.id.btn_mark_toxic);
        Button btnEditWord = dialogView.findViewById(R.id.btn_edit_word);

        if (messageView != null) {
            messageView.setText("Unrecognized word: " + word);
        }

        if (btnIgnore != null) {
            btnIgnore.setOnClickListener(v -> {
                try { windowManager.removeView(dialogView); } catch (Exception ignored) {}
            });
        }

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                java.util.Set<String> pending = new java.util.HashSet<>(prefs.getStringSet(KEY_USER_DICTIONARY, new java.util.HashSet<>()));
                pending.add(word);
                prefs.edit().putStringSet(KEY_USER_DICTIONARY, pending).apply();
                try { windowManager.removeView(dialogView); } catch (Exception ignored) {}
            });
        }

        if (btnMarkToxic != null) {
            btnMarkToxic.setOnClickListener(v -> {
                java.util.Set<String> pending = new java.util.HashSet<>(prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>()));
                pending.add(word);
                prefs.edit().putStringSet(KEY_TOXIC_FEEDBACK, pending).apply();
                try { windowManager.removeView(dialogView); } catch (Exception ignored) {}
            });
        }

        if (btnEditWord != null) {
            btnEditWord.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.deleteSurroundingText(word.length(), 0);
                    inputBuffer.setLength(Math.max(0, inputBuffer.length() - word.length()));
                }
                try { windowManager.removeView(dialogView); } catch (Exception ignored) {}
            });
        }

        try {
            windowManager.addView(dialogView, params);
        } catch (Exception e) {
            Log.e("ToxicKeyboard", "Error showing unrecognized alert", e);
        }
    }

    @Override
    public void onPress(int primaryCode) {}

    @Override
    public void onRelease(int primaryCode) {}

    @Override
    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            inputBuffer.append(text);
        }
    }

    @Override
    public void swipeLeft() {}

    @Override
    public void swipeRight() {}

    @Override
    public void swipeDown() {}

    @Override
    public void swipeUp() {}
}