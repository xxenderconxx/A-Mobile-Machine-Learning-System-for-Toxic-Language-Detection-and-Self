package com.example.toxiclanguagekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ToxicityFilteringTest {

    private SubstitutionEngine substitutionEngine;
    private ToxicityClassifier toxicityClassifier;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        substitutionEngine = new SubstitutionEngine(context);
        toxicityClassifier = new ToxicityClassifier(context);
        prefs = context.getSharedPreferences("ToxicKeyboardPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    @Test
    public void testSubstitutionEngine_ReframesToxicSentence() {
        String input = "You are so stupid.";
        String expected = "You are so uninformed.";
        String result = substitutionEngine.getReframedSentence(input);
        assertEquals(expected.toLowerCase(), result.toLowerCase());
    }

    @Test
    public void testSubstitutionEngine_IsKnownWord() {
        assertTrue(substitutionEngine.isKnownWord("stupid"));
        assertFalse(substitutionEngine.isKnownWord("xyzzy"));
    }

    @Test
    public void testToxicityClassifier_DetectsToxicity() throws InterruptedException {
        // ToxicityClassifier loads model in a background thread. Wait for it to load.
        int attempts = 0;
        while (attempts < 50) {
            if (toxicityClassifier.isToxic("stupid stupid stupid")) {
                break;
            }
            Thread.sleep(100);
            attempts++;
        }

        String toxicInput = "I hate you so much, you are the worst.";
        assertTrue("Model should detect toxicity in: " + toxicInput, toxicityClassifier.isToxic(toxicInput));
        
        String safeInput = "I love this keyboard, it's very helpful.";
        assertFalse("Model should NOT detect toxicity in: " + safeInput, toxicityClassifier.isToxic(safeInput));
    }
}
