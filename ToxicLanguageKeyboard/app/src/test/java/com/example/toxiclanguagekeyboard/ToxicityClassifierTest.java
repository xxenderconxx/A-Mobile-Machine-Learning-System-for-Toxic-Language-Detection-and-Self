package com.example.toxiclanguagekeyboard;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import android.content.Context;
import android.content.res.AssetManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ToxicityClassifierTest {

    private ToxicityClassifier classifier;

    @Mock
    Context mockContext;

    @Mock
    AssetManager mockAssetManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getAssets()).thenReturn(mockAssetManager);
        
        // Mocking the model weights JSON with common toxic and neutral words for testing
        String json = "{" +
                "\"intercept\": -1.0," +
                "\"weights\": {" +
                "\"stupid\": 2.0," +
                "\"idiot\": 2.5," +
                "\"baby\": 0.2," +
                "\"up\": 0.5," +
                "\"man\": 0.4," +
                "\"want\": 0.3," +
                "\"jajabol\": 5.4," +
                "\"fk\": 3.0," +
                "\"you\": 0.1" +
                "}}";
        InputStream is = new ByteArrayInputStream(json.getBytes());
        when(mockAssetManager.open(anyString())).thenReturn(is);

        classifier = new ToxicityClassifier(mockContext);
        
        // Wait for the background loading thread to complete
        int timeout = 20;
        while (!classifier.isLoaded && timeout > 0) {
            Thread.sleep(100);
            timeout--;
        }
        assertTrue("Classifier failed to load weights", classifier.isLoaded);
    }

    @Test
    public void testEntityAwareMultiplier_Person() {
        double prob = classifier.calculateProbability("you are stupid");
        // base score = -1.0 + 2.0 (stupid) + 0.1 (you) = 1.1
        // multiplier = 1.3 (person)
        // final score = 1.1 * 1.3 = 1.43
        // prob = 1 / (1 + exp(-1.43)) = 0.8068
        assertEquals(0.8068, prob, 0.01);
    }

    @Test
    public void testEntityAwareMultiplier_TargetedIndividual() {
        // "John" is a proper noun (heuristic: capitalized and not at start)
        double prob = classifier.calculateProbability("you are stupid John");
        // base score = 1.1
        // multiplier = 1.45 (targeted)
        // final score = 1.1 * 1.45 = 1.595
        // prob = 1 / (1 + exp(-1.595)) = 0.831
        assertEquals(0.831, prob, 0.01);
    }

    @Test
    public void testKnownFalsePositivesFromLogs() {
        // These phrases were flagged in the logs but should be below the 0.65 threshold after weight adjustments
        // Score for "baby" is low (0.2)
        assertFalse("Endearment should not be toxic", classifier.isToxic("sleeping so well my baby"));
        assertFalse("Casual status should not be toxic", classifier.isToxic("i just woke up thats all"));
    }

    @Test
    public void testNewSlangDetection() {
        // Verify Kapampangan slang 'jajabol' is detected as toxic (Score 5.4)
        assertTrue("Slang 'jajabol' should be detected", classifier.calculateProbability("jajabol") >= 0.90);
    }

    @Test
    public void testGeneralVsTargetedHostility() {
        double generalProb = classifier.calculateProbability("fk this");
        double targetedProb = classifier.calculateProbability("fk you");
        assertTrue("Targeted hostility should have a higher probability than general", targetedProb > generalProb);
    }
}
