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
import android.content.ClipboardManager;
import android.content.ClipData;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

public class ToxicKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private Keyboard symbolsKeyboard;
    private Keyboard numpadKeyboard;
    private Keyboard symbolsExtKeyboard;
    private View emojiPanelView;
    private boolean isEmojiPanelShowing = false;
    private EmojiAdapter emojiAdapter;
    private List<String> currentEmojiList = new ArrayList<>();
    private List<String> recentEmojis = new ArrayList<>();
    private Keyboard clipboardKeyboard;
    private boolean isCaps = false;
    private boolean isCapsLock = false;
    private long lastShiftClickTime = 0;
    private long lastSpaceClickTime = 0;
    private static final long DOUBLE_TAP_TIMEOUT = 300;
    private StringBuilder inputBuffer = new StringBuilder();
    private SubstitutionEngine substitutionEngine;
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
    private java.util.Set<String> commonWords;
    private java.util.Set<String> commonSkeletons;
    private java.util.Set<String> toxicSkeletons = new java.util.HashSet<>();
    private java.util.Set<String> userSkeletons = new java.util.HashSet<>();
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

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("ToxicKeyboard", "onCreate called");
        substitutionEngine = new SubstitutionEngine(this);
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
            // If decryption fails (e.g. AEADBadTagException), we must clear the corrupted file
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

        initializeCommonWords();
        loadUserDictionary();
        loadToxicFeedback();
        loadFalsePositives();
        loadClipboardHistory();
        loadRecentEmojis();
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboardManager.addPrimaryClipChangedListener(this::onClipboardChanged);
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
        
        userSkeletons.clear();
        for (String w : userDictionary) {
            userSkeletons.add(w.toLowerCase().replaceAll("(.)\\1+", "$1"));
        }
    }

    private void loadToxicFeedback() {
        java.util.Set<String> pending = prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>());
        java.util.Set<String> history = prefs.getStringSet(KEY_TOXIC_FEEDBACK_HISTORY, new java.util.HashSet<>());
        toxicFeedbackSet = new java.util.HashSet<>(pending);
        toxicFeedbackSet.addAll(history);

        toxicSkeletons.clear();
        for (String w : toxicFeedbackSet) {
            toxicSkeletons.add(w.toLowerCase().replaceAll("(.)\\1+", "$1"));
        }
        
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

    private void initializeCommonWords() {
        java.util.List<String> words = java.util.Arrays.asList(
            "a", "about", "above", "across", "after", "again", "against", "all", "almost", "alone", "along", "already", "also", "although", "always", "am", "among", "an", "and", "another", "any", "anybody", "anyone", "anything", "anywhere", "are", "area", "around", "as", "ask", "at", "away",
            "back", "be", "became", "because", "become", "becomes", "been", "before", "began", "begin", "behind", "being", "below", "beside", "best", "better", "between", "big", "both", "boy", "but", "by",
            "call", "came", "can", "cannot", "can't", "car", "certain", "change", "child", "children", "city", "close", "come", "could", "couldn't", "country", "course", "cut",
            "day", "did", "didn't", "do", "does", "doesn't", "dog", "done", "don't", "door", "down", "during",
            "each", "early", "earth", "eat", "either", "else", "end", "enough", "even", "ever", "every", "everybody", "everyone", "everything", "everywhere", "eye",
            "face", "fact", "far", "father", "feel", "few", "find", "fire", "first", "five", "for", "four", "from", "front", "full",
            "gave", "get", "girl", "give", "given", "go", "good", "got", "great", "group", "grow",
            "had", "hadn't", "half", "hand", "has", "hasn't", "have", "haven't", "he", "head", "hear", "heard", "help", "her", "here", "hers", "herself", "he's", "high", "him", "himself", "his", "home", "house", "how", "however",
            "i", "if", "important", "in", "interest", "into", "is", "isn't", "it", "its", "it's", "itself",
            "just", "keep", "kind", "knew", "know", "known",
            "land", "large", "last", "later", "learn", "leave", "left", "less", "let", "letter", "life", "light", "like", "line", "little", "live", "long", "look", "love", "low",
            "made", "make", "man", "many", "may", "maybe", "me", "mean", "men", "might", "mile", "miss", "more", "most", "mother", "mountain", "move", "much", "must", "my", "myself",
            "name", "near", "need", "never", "new", "next", "night", "no", "not", "nothing", "now", "number",
            "of", "off", "often", "oh", "old", "on", "once", "one", "only", "open", "or", "order", "other", "others", "our", "out", "over", "own",
            "page", "paper", "part", "past", "people", "perhaps", "person", "place", "play", "point", "present", "public", "put",
            "question", "quite",
            "read", "real", "really", "right", "room", "run",
            "said", "same", "saw", "say", "school", "sea", "second", "see", "seem", "seemed", "sentence", "set", "several", "she", "she's", "short", "should", "show", "side", "since", "small", "so", "some", "somebody", "someone", "something", "somewhere", "soon", "sound", "still", "story", "study", "such", "sure",
            "take", "tell", "than", "thank", "thanks", "that", "that's", "the", "their", "them", "themselves", "then", "there", "therefore", "these", "they", "thing", "think", "this", "those", "though", "thought", "three", "through", "time", "to", "together", "too", "took", "toward", "turn", "two",
            "under", "until", "up", "upon", "us", "use", "used",
            "very",
            "walk", "want", "was", "wasn't", "water", "way", "we", "well", "went", "were", "weren't", "what", "what's", "when", "where", "whether", "which", "while", "white", "who", "whole", "whom", "whose", "why", "will", "with", "within", "without", "word", "work", "world", "would", "wouldn't", "write",
            "year", "yet", "you", "young", "your", "yours", "yourself", "yourselves", "you're",
            "hi", "hello", "hey", "heyy", "heya", "yo", "sup", "please", "sorry", "yes", "ok", "okay", "yeah", "yep", "yup", "thanks", "welcome", "howdy", "goodbye", "bye", "test", "testing", "message", "type", "keyboard", "input",
            "today", "tomorrow", "yesterday", "morning", "afternoon", "evening", "night", "week", "month", "year", "time", "hour", "minute", "second",
            "happy", "sad", "angry", "excited", "bored", "tired", "hungry", "thirsty", "good", "bad", "better", "best", "worse", "worst",
            "black", "white", "red", "blue", "green", "yellow", "orange", "purple", "pink", "brown", "gray", "grey",
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred", "thousand", "million",
            "can", "could", "will", "would", "shall", "should", "may", "might", "must", "ought", "need", "dare",
            "am", "is", "are", "was", "were", "be", "being", "been", "have", "has", "had", "do", "does", "did",
            "doing", "done", "having", "going", "gone", "coming", "come", "taking", "taken", "making", "made",
            "using", "used", "finding", "found", "giving", "given", "telling", "told", "working", "worked",
            "calling", "called", "trying", "tried", "asking", "asked", "needing", "needed", "feeling", "felt",
            "looking", "looked", "becoming", "became", "leaving", "left", "putting", "put", "meaning", "meant",
            "keep", "kept", "let", "begin", "begun", "began", "show", "shown", "hear", "heard", "play", "played",
            "run", "ran", "move", "moved", "live", "lived", "believe", "believed", "bring", "brought", "happen", "happened",
            "write", "wrote", "written", "sit", "sat", "stand", "stood", "lose", "lost", "pay", "paid", "meet", "met",
            "include", "included", "continue", "continued", "set", "learn", "learned", "change", "changed", "lead", "led",
            "understand", "understood", "watch", "watched", "follow", "followed", "stop", "stopped", "create", "created",
            "speak", "spoke", "spoken", "read", "allow", "allowed", "add", "added", "spend", "spent", "grow", "grew", "grown",
            "open", "opened", "walk", "walked", "win", "won", "offer", "offered", "remember", "remembered", "love", "loved",
            "consider", "considered", "appear", "appeared", "buy", "bought", "wait", "waited", "serve", "served", "die", "died",
            "send", "sent", "expect", "expected", "build", "built", "stay", "stayed", "fall", "fell", "fallen", "cut",
            "reach", "reached", "kill", "killed", "remain", "remained"
        );
        
        commonWords = new java.util.HashSet<>(words);
        commonSkeletons = new java.util.HashSet<>();
        for (String w : words) {
            commonSkeletons.add(w.replaceAll("(.)\\1+", "$1"));
        }
    }

    @Override
    public View onCreateInputView() {
        Log.d("ToxicKeyboard", "onCreateInputView called");
        // Wrap context with theme to resolve ?attr/ selectable items
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
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        Log.d("ToxicKeyboard", "onStartInputView called");
        inputBuffer.setLength(0);
        lastCheckedText = "";
        resetKeyboardState();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                  int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        
        // Catch text entered via hardware keyboard, ADB, or manual cursor movement
        if (newSelStart == newSelEnd) { // Only if not selecting text
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                CharSequence before = ic.getTextBeforeCursor(100, 0);
                if (before != null) {
                    String currentText = before.toString();
                    // Avoid re-checking if text hasn't changed
                    if (!currentText.equals(lastCheckedText)) {
                        if (!currentText.isEmpty()) {
                            char lastChar = currentText.charAt(currentText.length() - 1);
                            // Trigger check on space or punctuation
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
            case 32: // Space
                handleSpace();
                break;
            case Keyboard.KEYCODE_DONE:
            case 10: // Enter
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
            case -300: // Switch to Extended Symbols
                keyboardView.setKeyboard(symbolsExtKeyboard);
                break;
            case -3: // Switch to Numpad or back to Symbols
                if (keyboardView.getKeyboard() == symbolsKeyboard || keyboardView.getKeyboard() == symbolsExtKeyboard) {
                    keyboardView.setKeyboard(numpadKeyboard);
                } else {
                    keyboardView.setKeyboard(symbolsKeyboard);
                }
                break;
            case -100: // Emoji
                showEmojiPanel();
                break;
            case -101: // Clipboard
                updateClipboardKeyboard();
                keyboardView.setKeyboard(clipboardKeyboard);
                break;
            case -102: // Settings Toggle
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
                if (primaryCode > 1000) { // Emoji or special unicode
                    String emoji = new String(Character.toChars(primaryCode));
                    ic.commitText(emoji, 1);
                    return;
                }
                char code = (char) primaryCode;
                if (Character.isLetter(code) && isCaps) {
                    code = Character.toUpperCase(code);
                }
                inputBuffer.append(code);
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
            // Double tap - Caps Lock
            isCapsLock = !isCapsLock;
            isCaps = isCapsLock;
        } else {
            // Single tap
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
            // Double tap space - insert period
            ic.deleteSurroundingText(1, 0);
            ic.commitText(". ", 1);
            inputBuffer.setLength(Math.max(0, inputBuffer.length() - 1));
            inputBuffer.append(". ");
            checkToxicityAsync(true);
            
            // Auto-cap after period
            isCaps = true;
            qwertyKeyboard.setShifted(true);
            updateShiftKeyState();
        } else {
            ic.commitText(" ", 1);
            inputBuffer.append(" ");
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
        for (Keyboard.Key key : keyboardView.getKeyboard().getKeys()) {
            if (key.codes[0] == code) return key;
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
        // Since Keyboard object is somewhat static, we can't easily add keys.
        // For a true clipboard manager, we'd use a different view, but for now
        // let's just use the first few slots if they exist.
        List<Keyboard.Key> keys = clipboardKeyboard.getKeys();
        for (int i = 0; i < keys.size(); i++) {
            Keyboard.Key k = keys.get(i);
            if (k.codes[0] == -2) continue; // Skip back button
            
            int clipIndex = i - 1; // Assuming first key is back button
            if (clipIndex >= 0 && clipIndex < clipboardHistory.size()) {
                k.label = clipboardHistory.get(clipIndex);
                // We'll use a special range for clipboard index codes
                k.codes = new int[]{ -200 - clipIndex }; 
            } else if (clipIndex >= 0) {
                k.label = "[Empty]";
                k.codes = new int[]{ 0 };
            }
        }
    }

    private void checkToxicityAsync(boolean isEndOfSentence) {
        if (!prefs.getBoolean(KEY_FILTERING_ENABLED, true)) {
            if (isEndOfSentence) inputBuffer.setLength(0);
            return;
        }

        if (classificationRunnable != null) {
            mainHandler.removeCallbacks(classificationRunnable);
        }

        String fullText = inputBuffer.toString();
        String contextText = getLastWords(fullText, 7);

        if (contextText.trim().isEmpty()) return;

        classificationRunnable = () -> executorService.execute(() -> {
            if (BuildConfig.DEBUG) {
                Log.d("ToxicKeyboard", "Inference on context");
            }

            // Normalize context for false positive check (remove punctuation)
            String cleanContext = contextText.toLowerCase().replaceAll("[^a-z'\\s]", "").trim();
            
            // 0. CHECK SUBSTITUTION ENGINE FOR WHITELISTED PHRASES FIRST
            // This ensures that phrases like "wow that is so big" aren't even processed by the ML model.
            if (substitutionEngine.isLoaded()) {
                // We'll add a method to check if the context is explicitly whitelisted
                if (isWhitelistedInEngine(contextText)) {
                    Log.d("ToxicKeyboard", "Skipping alert: Explicitly whitelisted in substitutions.json");
                    if (isEndOfSentence) inputBuffer.setLength(0);
                    mainHandler.post(() -> updateSafeIndicator(true));
                    return;
                }
            }

            if (falsePositivesSet.contains(cleanContext)) {
                Log.d("ToxicKeyboard", "Skipping alert: Known false positive");
                if (isEndOfSentence) inputBuffer.setLength(0);
                mainHandler.post(() -> updateSafeIndicator(true));
                return;
            }
            
            double probability = toxicityClassifier.calculateProbability(contextText);
            boolean isToxic = toxicityClassifier.isToxic(contextText);

            // 1. Scan context for manual overrides and safe words
            boolean containsManualToxic = false;
            String[] wordsInContext = cleanContext.split("\\s+");
            
            for (String w : wordsInContext) {
                if (w.isEmpty()) continue;
                
                // Check manual toxic flags (unless whitelisted)
                if (isToxicResilient(w) && !userDictionary.contains(w)) {
                    containsManualToxic = true;
                }
            }

            // Decision Logic:
            // Trigger if:
            // A. An explicitly marked toxic word is present
            // B. ML model detects toxicity (based on threshold)
            // C. Keyword match + probability boost
            
            boolean shouldShowAlert = containsManualToxic;
            if (!shouldShowAlert) {
                if (isToxic) {
                    shouldShowAlert = true;
                } else if (substitutionEngine.containsToxicLanguage(contextText) && probability > 0.45) {
                    shouldShowAlert = true;
                }
            }

            if (shouldShowAlert) {
                mainHandler.post(() -> {
                    updateSafeIndicator(false);
                    showToxicAlert(contextText, isEndOfSentence);
                });
                return;
            }

            // 2. Check for Unrecognized Vocabulary (only on space/punctuation)
            String lastWord = getLastWords(fullText, 1).toLowerCase().replaceAll("[^a-z']", "");
            if (!lastWord.isEmpty() && isUnrecognized(lastWord)) {
                mainHandler.post(() -> showUnrecognizedAlert(lastWord));
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
                    safeIndicatorText.setText("Safe to type");
                } else {
                    safeIndicatorDot.setBackgroundResource(R.drawable.indicator_dot_red);
                    safeIndicatorText.setText("Toxic language detected");
                }
            });
        }
    }

    private boolean isUnrecognized(String word) {
        if (word.length() <= 2) return false;
        if (word.contains("@")) return false;
        
        String lower = word.toLowerCase();
        
        // 1. Check for exact or normalized match in safe dictionaries
        if (isKnownResilient(lower)) return false;
        
        // 2. If it matches a toxic word's skeleton, we don't prompt "unrecognized"
        // because the toxicity alert will handle it.
        if (isToxicResilient(lower)) return false;

        return true;
    }

    private boolean isKnownResilient(String word) {
        if (word == null || word.isEmpty()) return true;
        String lower = word.toLowerCase().trim();
        
        // 1. Exact matches first
        if (commonWords.contains(lower) || userDictionary.contains(lower)) return true;
        if (substitutionEngine.isKnownWord(lower)) return true;

        // 2. Skeleton match: "heyyy" -> "hey"
        String skeleton = lower.replaceAll("(.)\\1+", "$1");
        
        // Check pre-calculated common skeletons
        if (commonSkeletons != null && commonSkeletons.contains(skeleton)) return true;
        
        // Check user dictionary skeletons
        if (userSkeletons.contains(skeleton)) return true;

        return false;
    }

    private boolean isToxicResilient(String word) {
        if (word == null || word.isEmpty()) return false;
        String lower = word.toLowerCase().trim();
        if (toxicFeedbackSet.contains(lower)) return true;
        
        String skeleton = lower.replaceAll("(.)\\1+", "$1");
        if (toxicSkeletons.contains(skeleton)) return true;
        
        return substitutionEngine.isToxicWord(lower);
    }

    private WindowManager getOverlayWindowManager() {
        if (overlayWindowManager == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    // On Android 12+ (API 31), we can use the Display object to create the WindowContext
                    windowContext = createWindowContext(getDisplay(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
                    overlayWindowManager = (WindowManager) windowContext.getSystemService(Context.WINDOW_SERVICE);
                } catch (Exception e) {
                    Log.e("ToxicKeyboard", "Failed to create window context on API 31+", e);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    // On Android 11 (API 30), the Display overload is not available.
                    // We use the version that uses the context's associated display.
                    windowContext = createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
                    overlayWindowManager = (WindowManager) windowContext.getSystemService(Context.WINDOW_SERVICE);
                } catch (Exception e) {
                    Log.e("ToxicKeyboard", "Failed to create window context on API 30", e);
                }
            }

            if (overlayWindowManager == null) {
                // Fallback for older versions or if WindowContext creation failed
                overlayWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            }
        }
        return overlayWindowManager;
    }

    private void showUnrecognizedAlert(String word) {
        WindowManager windowManager = getOverlayWindowManager();
        
        // Use the windowContext for inflation if available to ensure theme and configuration consistency
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
        Button btnEdit = dialogView.findViewById(R.id.btn_edit_word);

        messageView.setText(getString(R.string.unrecognized_word_message, word));

        btnIgnore.setOnClickListener(v -> {
            // Increment Ignored Count
            int currentIgnored = prefs.getInt(KEY_COUNT_IGNORED, 0);
            prefs.edit().putInt(KEY_COUNT_IGNORED, currentIgnored + 1).apply();
            windowManager.removeView(dialogView);
        });

        btnAdd.setOnClickListener(v -> {
            String cleanWord = word.toLowerCase().replaceAll("[^a-z']", "");
            
            // Check history first to prevent redundant pending entries
            java.util.Set<String> history = prefs.getStringSet(KEY_USER_DICTIONARY_HISTORY, new java.util.HashSet<>());
            if (!history.contains(cleanWord)) {
                java.util.Set<String> pending = new java.util.HashSet<>(prefs.getStringSet(KEY_USER_DICTIONARY, new java.util.HashSet<>()));
                if (pending.add(cleanWord)) {
                    prefs.edit().putStringSet(KEY_USER_DICTIONARY, pending).apply();
                    // Only upload if it's a new contribution
                    uploadToFirebase(cleanWord, "safe", "unrecognized_prompt");
                }
            }
            
            Toast.makeText(this, R.string.toast_word_marked_safe, Toast.LENGTH_SHORT).show();
            windowManager.removeView(dialogView);
        });

        Button btnMarkToxic = dialogView.findViewById(R.id.btn_mark_toxic);
        btnMarkToxic.setOnClickListener(v -> {
            String cleanWord = word.toLowerCase().replaceAll("[^a-z']", "");
            
            // Check history first
            java.util.Set<String> history = prefs.getStringSet(KEY_TOXIC_FEEDBACK_HISTORY, new java.util.HashSet<>());
            if (!history.contains(cleanWord)) {
                java.util.Set<String> pending = new java.util.HashSet<>(prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>()));
                if (pending.add(cleanWord)) {
                    prefs.edit().putStringSet(KEY_TOXIC_FEEDBACK, pending).apply();
                    // Immediately update the engine
                    loadToxicFeedback();
                    // Only upload if it's a new contribution
                    uploadToFirebase(cleanWord, "toxic", "unrecognized_prompt");
                }
            }

            Toast.makeText(this, R.string.toast_word_marked_toxic, Toast.LENGTH_SHORT).show();
            windowManager.removeView(dialogView);
        });

        btnEdit.setOnClickListener(v -> {
            // Increment Edits Count
            int currentEdits = prefs.getInt(KEY_COUNT_EDITS, 0);
            prefs.edit().putInt(KEY_COUNT_EDITS, currentEdits + 1).apply();

            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                // Delete the unrecognized word + the space/punctuation that triggered it
                ic.deleteSurroundingText(word.length() + 1, 0);
            }
            windowManager.removeView(dialogView);
        });

        try {
            windowManager.addView(dialogView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getLastWords(String text, int count) {
        String[] words = text.split("\\s+");
        if (words.length <= count) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = words.length - count; i < words.length; i++) {
            sb.append(words[i]).append(" ");
        }
        return sb.toString().trim();
    }

    private void showToxicAlert(String toxicText, boolean isEndOfSentence) {
        // Increment Detections Count
        int currentDetections = prefs.getInt(KEY_COUNT_DETECTIONS, 0);
        prefs.edit().putInt(KEY_COUNT_DETECTIONS, currentDetections + 1).apply();

        // Reset lastCheckedText so if the user makes no changes but types another space, we re-trigger
        lastCheckedText = "";

        WindowManager windowManager = getOverlayWindowManager();
        
        // Use the windowContext for inflation if available
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

        String reframed = substitutionEngine.getReframedSentence(toxicText);
        messageView.setText(getString(R.string.toxic_alert_display, toxicText, reframed));

        btnEdit.setOnClickListener(v -> {
            // Increment Edits Count
            int currentEdits = prefs.getInt(KEY_COUNT_EDITS, 0);
            prefs.edit().putInt(KEY_COUNT_EDITS, currentEdits + 1).apply();

            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.deleteSurroundingText(toxicText.length(), 0);
                ic.commitText(reframed, 1);
            }
            inputBuffer.setLength(0);
            windowManager.removeView(dialogView);
        });

        btnProceed.setOnClickListener(v -> {
            // Increment Ignored Count
            int currentIgnored = prefs.getInt(KEY_COUNT_IGNORED, 0);
            prefs.edit().putInt(KEY_COUNT_IGNORED, currentIgnored + 1).apply();

            inputBuffer.setLength(0);
            windowManager.removeView(dialogView);
        });

        Button btnFalsePositive = dialogView.findViewById(R.id.btn_false_positive);
        btnFalsePositive.setOnClickListener(v -> {
            // Normalize context before saving
            String cleanContext = toxicText.toLowerCase().replaceAll("[^a-z'\\s]", "").trim();
            
            // 1. Add to False Positives set (checking history first)
            java.util.Set<String> fpHours = prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK_HISTORY, new java.util.HashSet<>());
            if (!fpHours.contains(cleanContext)) {
                java.util.Set<String> fpPending = new java.util.HashSet<>(prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK, new java.util.HashSet<>()));
                if (fpPending.add(cleanContext)) {
                    prefs.edit().putStringSet(KEY_FALSE_POSITIVES_FEEDBACK, fpPending).apply();
                    // Only upload if it's a new contribution
                    uploadToFirebase(cleanContext, "safe", "false_positive_report");
                }
            }

            // 2. Add words to User Dictionary (Safe list) and remove from Toxic list (checking history first)
            String[] words = cleanContext.split("\\s+");
            java.util.Set<String> safeHistory = prefs.getStringSet(KEY_USER_DICTIONARY_HISTORY, new java.util.HashSet<>());
            java.util.Set<String> safePending = new java.util.HashSet<>(prefs.getStringSet(KEY_USER_DICTIONARY, new java.util.HashSet<>()));
            java.util.Set<String> toxicPending = new java.util.HashSet<>(prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>()));
            
            boolean changedSafe = false;
            boolean changedToxic = false;

            for (String w : words) {
                if (w.isEmpty()) continue;
                // Add to safe if not in history and not already in pending
                if (!safeHistory.contains(w) && safePending.add(w)) {
                    changedSafe = true;
                }
                // Remove from toxic pending if present
                if (toxicPending.remove(w)) {
                    changedToxic = true;
                }
            }
            
            SharedPreferences.Editor editor = prefs.edit();
            if (changedSafe) {
                editor.putStringSet(KEY_USER_DICTIONARY, safePending);
            }
            if (changedToxic) {
                editor.putStringSet(KEY_TOXIC_FEEDBACK, toxicPending);
            }
            editor.apply();

            Toast.makeText(this, R.string.toast_false_positive_reported, Toast.LENGTH_SHORT).show();
            windowManager.removeView(dialogView);
        });

        try {
            windowManager.addView(dialogView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showEmojiPanel() {
        isEmojiPanelShowing = true;
        keyboardView.setVisibility(View.GONE);
        emojiPanelView.setVisibility(View.VISIBLE);
    }

    private void hideEmojiPanel() {
        isEmojiPanelShowing = false;
        emojiPanelView.setVisibility(View.GONE);
        keyboardView.setVisibility(View.VISIBLE);
    }

    private void loadRecentEmojis() {
        String recent = prefs.getString("recent_emojis", "");
        if (!recent.isEmpty()) {
            recentEmojis = new ArrayList<>(java.util.Arrays.asList(recent.split(",")));
        }
    }

    private void saveRecentEmoji(String emoji) {
        recentEmojis.remove(emoji);
        recentEmojis.add(0, emoji);
        if (recentEmojis.size() > 40) {
            recentEmojis.remove(recentEmojis.size() - 1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recentEmojis.size(); i++) {
            sb.append(recentEmojis.get(i));
            if (i < recentEmojis.size() - 1) sb.append(",");
        }
        prefs.edit().putString("recent_emojis", sb.toString()).apply();
    }

    private void setupEmojiPanel(View root) {
        root.findViewById(R.id.btn_emoji_back).setOnClickListener(v -> hideEmojiPanel());
        root.findViewById(R.id.btn_emoji_to_abc).setOnClickListener(v -> hideEmojiPanel());
        
        ImageButton delBtn = root.findViewById(R.id.btn_emoji_del);
        delBtn.setOnClickListener(v -> onKey(Keyboard.KEYCODE_DELETE, null));
        
        androidx.recyclerview.widget.RecyclerView emojiRecycler = root.findViewById(R.id.emoji_recycler);
        emojiRecycler.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 8));
        
        currentEmojiList = EmojiLibrary.getAllEmojis();
        emojiAdapter = new EmojiAdapter(currentEmojiList, emoji -> {
            getCurrentInputConnection().commitText(emoji, 1);
            saveRecentEmoji(emoji);
        });
        emojiRecycler.setAdapter(emojiAdapter);

        // Setup categories
        LinearLayout categoryContainer = root.findViewById(R.id.emoji_categories);
        for (int i = 0; i < categoryContainer.getChildCount(); i++) {
            View child = categoryContainer.getChildAt(i);
            if (child instanceof ImageButton) {
                final int index = i;
                child.setOnClickListener(v -> jumpToCategory(index));
            }
        }
    }

    private void jumpToCategory(int categoryIndex) {
        androidx.recyclerview.widget.RecyclerView emojiRecycler = emojiPanelView.findViewById(R.id.emoji_recycler);
        if (emojiRecycler == null) return;

        // Reset to all if showing filtered (not applicable anymore as search is removed, but good for safety)
        if (emojiAdapter.getItemCount() != EmojiLibrary.getAllEmojis().size() && categoryIndex != 0) {
            emojiAdapter.updateData(EmojiLibrary.getAllEmojis());
        }

        if (categoryIndex == 0) { // Recent
            emojiAdapter.updateData(recentEmojis.isEmpty() ? new ArrayList<>() : recentEmojis);
            emojiRecycler.scrollToPosition(0);
            return;
        }

        int position = 0;
        Map<String, List<String>> categories = EmojiLibrary.getCategorizedEmojis();
        int i = 1; // Start from 1 because 0 is Recent
        for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
            if (i == categoryIndex) {
                if (emojiRecycler.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager) {
                    ((androidx.recyclerview.widget.GridLayoutManager) emojiRecycler.getLayoutManager()).scrollToPositionWithOffset(position, 0);
                }
                break;
            }
            position += entry.getValue().size();
            i++;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        if (clipboardManager != null) {
            clipboardManager.removePrimaryClipChangedListener(this::onClipboardChanged);
        }
    }

    @Override
    public void onPress(int primaryCode) {
        if (primaryCode != 0) {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(android.os.VibrationEffect.createOneShot(15, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    @Override
    public void onRelease(int primaryCode) {}

    @Override
    public void onText(CharSequence text) {}

    @Override
    public void swipeLeft() {}

    @Override
    public void swipeRight() {}

    @Override
    public void swipeDown() {}

    @Override
    public void swipeUp() {}

    private void uploadToFirebase(String text, String label, String type) {
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> data = new HashMap<>();
            data.put("text", text);
            data.put("label", label); // "safe" or "toxic"
            data.put("type", type);   // "unrecognized_prompt" or "false_positive_report"
            data.put("timestamp", FieldValue.serverTimestamp());
            // Use hash of Android ID for anonymous but consistent user tracking
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            data.put("userId", androidId != null ? androidId.hashCode() : 0);

            db.collection("feedback")
                    .add(data)
                    .addOnFailureListener(e -> Log.e("ToxicKeyboard", "Upload failed", e));
        } catch (Exception e) {
            // This might happen if google-services.json is missing or Firebase not initialized
            Log.e("ToxicKeyboard", "Firebase upload error: " + e.getMessage());
        }
    }
}
