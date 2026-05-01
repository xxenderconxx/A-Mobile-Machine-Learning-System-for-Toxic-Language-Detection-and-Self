package com.example.toxiclanguagekeyboard;

import android.content.Context;
import android.graphics.PixelFormat;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;

public class ToxicKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private Keyboard symbolsKeyboard;
    private boolean isCaps = false;
    private StringBuilder inputBuffer = new StringBuilder();
    private SubstitutionEngine substitutionEngine;
    private ToxicityClassifier toxicityClassifier;

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
                checkToxicityAndSend(true);
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
                checkToxicityAndSend(false);
                break;
            case 46: // Period
            case 33: // !
            case 63: // ?
                char punct = (char) primaryCode;
                inputBuffer.append(punct);
                ic.commitText(String.valueOf(punct), 1);
                checkToxicityAndSend(true); // Treat end of sentence as "Done" for check
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

    private void checkToxicityAndSend(boolean isEndOfSentence) {
        String currentText = inputBuffer.toString().trim();
        if (currentText.isEmpty()) {
            if (isEndOfSentence) {
                sendDefaultEditorAction(true);
            }
            return;
        }

        if (toxicityClassifier.isToxic(currentText) || substitutionEngine.containsToxicLanguage(currentText)) {
            showToxicAlert(currentText, isEndOfSentence);
        } else if (isEndOfSentence) {
            sendDefaultEditorAction(true);
            inputBuffer.setLength(0);
        }
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

        // We capture the length of the buffer AT THE TIME of the alert
        final String bufferToReplace = inputBuffer.toString();

        btnEdit.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                // Delete everything we've buffered so far
                ic.deleteSurroundingText(bufferToReplace.length(), 0);
                // Insert the reframed text
                ic.commitText(reframed, 1);
                
                if (isEndOfSentence) {
                    sendDefaultEditorAction(true);
                }
            }
            inputBuffer.setLength(0);
            windowManager.removeView(dialogView);
        });

        btnProceed.setOnClickListener(v -> {
            if (isEndOfSentence) {
                sendDefaultEditorAction(true);
            }
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
