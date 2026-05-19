package com.example.toxiclanguagekeyboard;

import android.content.Context;
import android.graphics.PixelFormat;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ToxicKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private Keyboard symbolsKeyboard;
    private boolean isCaps = false;
    private StringBuilder inputBuffer = new StringBuilder();
    private SubstitutionEngine substitutionEngine;
    private ToxicityClassifier toxicityClassifier;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable classificationRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        substitutionEngine = new SubstitutionEngine(this);
        toxicityClassifier = new ToxicityClassifier(this);
    }

    @Override
    public View onCreateInputView() {
        keyboardView = (KeyboardView) getLayoutInflater().inflate(R.layout.keyboard_view, null);
        qwertyKeyboard = new Keyboard(this, R.xml.qwerty);
        symbolsKeyboard = new Keyboard(this, R.xml.symbols);
        keyboardView.setKeyboard(qwertyKeyboard);
        keyboardView.setOnKeyboardActionListener(this);
        return keyboardView;
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
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
                isCaps = !isCaps;
                qwertyKeyboard.setShifted(isCaps);
                keyboardView.invalidateAllKeys();
                break;
            case Keyboard.KEYCODE_DONE:
            case 10: // Enter
                ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
                checkToxicityAsync(true); // Space-Bar Gating: triggers on Enter
                break;
            case Keyboard.KEYCODE_MODE_CHANGE:
                if (keyboardView.getKeyboard() == qwertyKeyboard) {
                    keyboardView.setKeyboard(symbolsKeyboard);
                } else {
                    keyboardView.setKeyboard(qwertyKeyboard);
                }
                break;
            case 32: // Space
                inputBuffer.append(" ");
                ic.commitText(" ", 1);
                checkToxicityAsync(false); // Space-Bar Gating: triggers on Space
                break;
            case 46: // Period
            case 33: // !
            case 63: // ?
                char punct = (char) primaryCode;
                inputBuffer.append(punct);
                ic.commitText(String.valueOf(punct), 1);
                checkToxicityAsync(true); // Space-Bar Gating: triggers on terminal punctuation
                break;
            default:
                char code = (char) primaryCode;
                if (Character.isLetter(code) && isCaps) {
                    code = Character.toUpperCase(code);
                }
                inputBuffer.append(code);
                ic.commitText(String.valueOf(code), 1);
        }
    }

    /**
     * Space-Bar Gating: Only triggered on space or punctuation.
     * Asynchronous Execution: Runs on background executor thread.
     */
    private void checkToxicityAsync(boolean isEndOfSentence) {
        // Debouncing: Remove any pending classification tasks
        if (classificationRunnable != null) {
            mainHandler.removeCallbacks(classificationRunnable);
        }

        String fullText = inputBuffer.toString();
        // Sentence Windowing: Look at the last 7 words for context
        String contextText = getLastWords(fullText, 7);

        if (contextText.trim().isEmpty()) return;

        classificationRunnable = () -> executorService.execute(() -> {
            // Inference Logic (Clean text, Tokenization, Probability calc) runs on background thread
            boolean isToxic = toxicityClassifier.isToxic(contextText) || substitutionEngine.containsToxicLanguage(contextText);
            
            if (isToxic) {
                // UI interaction must be on Main Thread
                mainHandler.post(() -> showToxicAlert(contextText, isEndOfSentence));
            } else if (isEndOfSentence) {
                inputBuffer.setLength(0);
            }
        });

        // Delay execution slightly for debouncing (300ms)
        mainHandler.postDelayed(classificationRunnable, 300);
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
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View dialogView = inflater.inflate(R.layout.toxic_alert_dialog, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);

        params.dimAmount = 0.6f;
        params.gravity = Gravity.CENTER;

        TextView messageView = dialogView.findViewById(R.id.alert_message);
        Button btnEdit = dialogView.findViewById(R.id.btn_edit);
        Button btnProceed = dialogView.findViewById(R.id.btn_proceed);

        String reframed = substitutionEngine.getReframedSentence(toxicText);
        messageView.setText("Wait! You typed: \"" + toxicText + "\"\n\nPolite version: \"" + reframed + "\"");

        btnEdit.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.deleteSurroundingText(toxicText.length(), 0);
                ic.commitText(reframed, 1);
            }
            inputBuffer.setLength(0);
            windowManager.removeView(dialogView);
        });

        btnProceed.setOnClickListener(v -> {
            inputBuffer.setLength(0);
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
    }

    @Override
    public void onPress(int primaryCode) {}
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
