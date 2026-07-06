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
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ToxicKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private Keyboard symbolsKeyboard;
    private Keyboard emojiKeyboard;
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
    private java.util.Set<String> userDictionary;
    private java.util.Set<String> toxicFeedbackSet;
    private java.util.Set<String> falsePositivesSet;
    private static final String PREFS_NAME = "ToxicKeyboardPrefs";
    private static final String KEY_USER_DICTIONARY = "user_dictionary";
    private static final String KEY_FILTERING_ENABLED = "filtering_enabled";
    private static final String KEY_COUNT_DETECTIONS = "count_detections";
    private static final String KEY_COUNT_EDITS = "count_edits";
    private static final String KEY_COUNT_IGNORED = "count_ignored";
    private static final String KEY_TOXIC_FEEDBACK = "toxic_feedback";
    private static final String KEY_FALSE_POSITIVES_FEEDBACK = "false_positives_feedback";

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
        } catch (GeneralSecurityException | IOException e) {
            Log.e("ToxicKeyboard", "Error initializing EncryptedSharedPreferences", e);
            // Fallback to regular prefs if encryption fails (though ideally we handle this better)
            encryptedPrefs = prefs;
        }

        initializeCommonWords();
        loadUserDictionary();
        loadToxicFeedback();
        loadFalsePositives();
        loadClipboardHistory();
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
        if (KEY_USER_DICTIONARY.equals(key)) {
            loadUserDictionary();
        } else if (KEY_TOXIC_FEEDBACK.equals(key)) {
            loadToxicFeedback();
        } else if (KEY_FALSE_POSITIVES_FEEDBACK.equals(key)) {
            loadFalsePositives();
        }
    };

    private void loadUserDictionary() {
        java.util.Set<String> savedWords = encryptedPrefs.getStringSet(KEY_USER_DICTIONARY, new java.util.HashSet<>());
        // Create a copy to avoid mutating the set returned by SharedPreferences
        userDictionary = new java.util.HashSet<>(savedWords);
    }

    private void loadToxicFeedback() {
        java.util.Set<String> savedToxic = prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>());
        toxicFeedbackSet = new java.util.HashSet<>(savedToxic);
    }

    private void loadFalsePositives() {
        java.util.Set<String> savedFP = prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK, new java.util.HashSet<>());
        falsePositivesSet = new java.util.HashSet<>(savedFP);
    }

    private void initializeCommonWords() {
        commonWords = new java.util.HashSet<>(java.util.Arrays.asList(
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
            "hi", "hello", "please", "sorry", "yes", "ok", "okay", "yeah", "thanks", "welcome", "howdy", "goodbye", "bye", "test", "testing", "message", "type", "keyboard", "input",
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
        ));
    }

    @Override
    public View onCreateInputView() {
        Log.d("ToxicKeyboard", "onCreateInputView called");
        View root = getLayoutInflater().inflate(R.layout.keyboard_view, null);
        keyboardView = root.findViewById(R.id.keyboard_view);
        safeIndicatorDot = root.findViewById(R.id.safe_indicator_dot);
        safeIndicatorText = root.findViewById(R.id.safe_indicator_text);
        
        qwertyKeyboard = new Keyboard(this, R.xml.qwerty);
        symbolsKeyboard = new Keyboard(this, R.xml.symbols);
        emojiKeyboard = new Keyboard(this, R.xml.emoji);
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
        android.util.Log.d("ToxicKeyboard", "onKey: " + primaryCode);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                if (inputBuffer.length() > 0) {
                    inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                }
                ic.deleteSurroundingText(1, 0);
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
                break;
            case Keyboard.KEYCODE_MODE_CHANGE:
                if (keyboardView.getKeyboard() == qwertyKeyboard) {
                    keyboardView.setKeyboard(symbolsKeyboard);
                } else {
                    keyboardView.setKeyboard(qwertyKeyboard);
                }
                break;
            case -100: // Emoji
                keyboardView.setKeyboard(emojiKeyboard);
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
            Log.d("ToxicKeyboard", "Inference on: [" + contextText + "]");

            // Normalize context for false positive check (remove punctuation)
            String cleanContext = contextText.toLowerCase().replaceAll("[^a-z'\\s]", "").trim();
            if (falsePositivesSet.contains(cleanContext)) {
                Log.d("ToxicKeyboard", "Skipping alert: Known false positive");
                if (isEndOfSentence) inputBuffer.setLength(0);
                return;
            }
            
            double probability = toxicityClassifier.calculateProbability(contextText);
            boolean isToxic = toxicityClassifier.isToxic(contextText);

            // 1. Scan context for manual overrides and safe words
            boolean containsManualToxic = false;
            boolean containsUnsafeWord = false;
            String[] wordsInContext = cleanContext.split("\\s+");
            
            for (String w : wordsInContext) {
                if (w.isEmpty()) continue;
                
                // A word is recognized if it's in common words, the user dictionary, or the substitution engine
                boolean isRecognized = commonWords.contains(w) || 
                                userDictionary.contains(w) || 
                                substitutionEngine.isKnownWord(w);

                // Check manual toxic flags (unless whitelisted)
                if (toxicFeedbackSet.contains(w) && !userDictionary.contains(w)) {
                    containsManualToxic = true;
                }
                
                if (!isRecognized) {
                    containsUnsafeWord = true;
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

    private void updateSafeIndicator(boolean isSafe) {
        if (safeIndicatorDot != null && safeIndicatorText != null) {
            if (isSafe) {
                safeIndicatorDot.setBackgroundResource(R.drawable.indicator_dot_green);
                safeIndicatorText.setText("Safe to type");
            } else {
                safeIndicatorDot.setBackgroundResource(R.drawable.indicator_dot_red);
                safeIndicatorText.setText("Toxic language detected");
            }
        }
    }

    private boolean isUnrecognized(String word) {
        if (word.length() <= 2) return false;
        
        if (substitutionEngine.isKnownWord(word)) return false;
        
        // If it's in common words, user-added safe words, or already flagged toxic, don't prompt
        return !commonWords.contains(word) && 
               !userDictionary.contains(word) && 
               !toxicFeedbackSet.contains(word);
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
            userDictionary.add(cleanWord);
            prefs.edit().putStringSet(KEY_USER_DICTIONARY, userDictionary).apply();
            Toast.makeText(this, R.string.toast_word_marked_safe, Toast.LENGTH_SHORT).show();
            windowManager.removeView(dialogView);
        });

        Button btnMarkToxic = dialogView.findViewById(R.id.btn_mark_toxic);
        btnMarkToxic.setOnClickListener(v -> {
            String cleanWord = word.toLowerCase().replaceAll("[^a-z']", "");
            java.util.Set<String> toxicFeedback = new java.util.HashSet<>(prefs.getStringSet(KEY_TOXIC_FEEDBACK, new java.util.HashSet<>()));
            toxicFeedback.add(cleanWord);
            prefs.edit().putStringSet(KEY_TOXIC_FEEDBACK, toxicFeedback).apply();
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
            
            // 1. Add to False Positives set
            java.util.Set<String> falsePositives = new java.util.HashSet<>(prefs.getStringSet(KEY_FALSE_POSITIVES_FEEDBACK, new java.util.HashSet<>()));
            falsePositives.add(cleanContext);
            prefs.edit().putStringSet(KEY_FALSE_POSITIVES_FEEDBACK, falsePositives).apply();
            falsePositivesSet.add(cleanContext);

            // 2. Add words to User Dictionary (Safe list) so they aren't marked unrecognized later
            String[] words = cleanContext.split("\\s+");
            boolean changedSafe = false;
            boolean changedToxic = false;
            for (String w : words) {
                if (userDictionary.add(w)) {
                    changedSafe = true;
                }
                // Also ensure it's removed from toxic feedback if it was there
                if (toxicFeedbackSet.remove(w)) {
                    changedToxic = true;
                }
            }
            
            SharedPreferences.Editor editor = prefs.edit();
            if (changedSafe) {
                editor.putStringSet(KEY_USER_DICTIONARY, userDictionary);
            }
            if (changedToxic) {
                editor.putStringSet(KEY_TOXIC_FEEDBACK, toxicFeedbackSet);
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
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
}
