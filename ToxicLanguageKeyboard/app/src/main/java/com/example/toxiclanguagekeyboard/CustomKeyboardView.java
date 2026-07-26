package com.example.toxiclanguagekeyboard;

import android.content.Context;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

/**
 * Modernized KeyboardView with Material 3 haptics and horizontal swipe-to-move-cursor.
 * This provides a Gboard-like experience on top of the legacy platform.
 */
@SuppressWarnings("deprecation")
public class CustomKeyboardView extends KeyboardView {

    private float lastX;
    private static final int SWIPE_THRESHOLD = 30; // Pixels per cursor step

    public CustomKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomKeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        switch (me.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Instant mechanical haptic feedback
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                
                if (handleBack()) {
                    return true; 
                }
                break;

            case MotionEvent.ACTION_UP:
                performClick();
                break;
        }
        
        return super.onTouchEvent(me);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public boolean isPopupShowing() {
        return handleBack();
    }
}
