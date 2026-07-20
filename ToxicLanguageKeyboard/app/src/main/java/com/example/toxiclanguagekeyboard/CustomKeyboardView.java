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
        float x = me.getX();

        switch (me.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                // Instant mechanical haptic feedback
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                
                if (handleBack()) {
                    return true; 
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaX = x - lastX;
                if (Math.abs(deltaX) > SWIPE_THRESHOLD) {
                    // Trigger horizontal cursor movement
                    OnKeyboardActionListener listener = getOnKeyboardActionListener();
                    if (listener instanceof ToxicKeyboardService) {
                        ((ToxicKeyboardService) listener).onCursorMove(deltaX > 0);
                        lastX = x; // Reset for continuous sliding
                        
                        // Subtle tactile tick for each character moved
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        return true; // Consume to prevent key ghosting during swipe
                    }
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
