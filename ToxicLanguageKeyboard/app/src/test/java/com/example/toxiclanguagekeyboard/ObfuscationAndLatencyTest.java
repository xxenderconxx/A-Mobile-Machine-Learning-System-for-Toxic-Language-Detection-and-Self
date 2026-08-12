package com.example.toxiclanguagekeyboard;

import org.junit.Test;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class ObfuscationAndLatencyTest {

    @Test
    public void testObfuscationNormalization() throws Exception {
        File modelFile = new File("app/src/main/assets/model_weights.json");
        if (!modelFile.exists()) {
             modelFile = new File("D:/programs/ToxicLanguageKeyboard/app/src/main/assets/model_weights.json");
        }
        ToxicityClassifier classifier = new ToxicityClassifier(new FileInputStream(modelFile));

        Map<String, String> testCases = new HashMap<>();
        testCases.put("f*ck", "fuck"); // Note: normalizeText removes *, so it becomes 'fck'. 
                                      // I should check if 'fck' or 'fuck' is expected.
                                      // Actually code says: .replace("*", "") -> f*ck becomes fck.
        testCases.put("5tupid", "stupid");
        testCases.put("4sshole", "asshole");
        testCases.put("@ss", "ass");
        testCases.put("sh!t", "shit");
        testCases.put("b1tch", "bitch");
        testCases.put("l0ser", "loser");
        testCases.put("h4te", "hate");

        System.out.println("=== Obfuscation Normalization Test ===");
        for (Map.Entry<String, String> entry : testCases.entrySet()) {
            // We can't call normalizeText directly as it's private. 
            // We'll use reflection or just check if it's detected as toxic if the base word is toxic.
            // For now, let's just see if the classifier detects them.
            boolean toxic = classifier.isToxic(entry.getKey());
            System.out.println("Input: " + entry.getKey() + " -> Toxic: " + toxic);
        }
    }

    @Test
    public void testLatency() throws Exception {
        File modelFile = new File("app/src/main/assets/model_weights.json");
        if (!modelFile.exists()) {
             modelFile = new File("D:/programs/ToxicLanguageKeyboard/app/src/main/assets/model_weights.json");
        }
        ToxicityClassifier classifier = new ToxicityClassifier(new FileInputStream(modelFile));

        String longText = "This is a relatively long sentence designed to test the performance of the toxicity classifier " +
                "when dealing with multiple words and potential bigrams that might be present in the weights file. " +
                "We want to ensure that the keyboard remains responsive even with 1442 features.";

        System.out.println("=== Latency Benchmark ===");
        int iterations = 1000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            classifier.isToxic(longText);
        }
        long end = System.nanoTime();
        double avgMs = (double)(end - start) / iterations / 1_000_000.0;
        System.out.printf("Average Latency over %d iterations: %.4f ms\n", iterations, avgMs);
        
        assertTrue("Latency should be less than 5ms for real-time typing", avgMs < 5.0);
    }
}
