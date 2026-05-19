package com.example.toxiclanguagekeyboard;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ToxicityClassifier {

    private Map<String, Double> weights = new HashMap<>();
    private double intercept = 0.0;

    public ToxicityClassifier(Context context) {
        loadModelAsync(context);
    }

    private void loadModelAsync(Context context) {
        // Background Processing: JSON weight lookup on a background thread
        new Thread(() -> {
            try {
                InputStream is = context.getAssets().open("model_weights.json");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                String json = new String(buffer, StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(json);

                intercept = jsonObject.optDouble("intercept", 0.0);
                JSONObject weightsJson = jsonObject.getJSONObject("weights");
                Iterator<String> keys = weightsJson.keys();

                while (keys.hasNext()) {
                    String key = keys.next();
                    weights.put(key.toLowerCase(), weightsJson.getDouble(key));
                }
                isLoaded = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private boolean isLoaded = false;

    /**
     * Implements the Multi-Word Rule (Adaptive Confidence Gate):
     * - Rule A (Short Input): 1-2 words -> Threshold = 0.90
     * - Rule B (Long Input): 3+ words -> Threshold = 0.65
     */
    public boolean isToxic(String text) {
        if (!isLoaded || text == null || text.trim().isEmpty()) return false;

        // Normalization: convert to lowercase and remove punctuation
        String cleaned = text.toLowerCase().replaceAll("[^a-z\\s]", "").trim();
        if (cleaned.isEmpty()) return false;

        String[] words = cleaned.split("\\s+");
        int wordCount = words.length;

        // Sigmoid calculation (handled in background by caller)
        double probability = calculateProbability(cleaned);
        
        // Adaptive Confidence Gate logic
        double threshold = (wordCount >= 3) ? 0.65 : 0.90;

        return probability >= threshold;
    }

    public double calculateProbability(String cleanedText) {
        if (cleanedText.isEmpty()) return 0.0;
        
        double score = intercept;
        String[] words = cleanedText.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            // Unigram
            if (weights.containsKey(words[i])) {
                score += weights.get(words[i]);
            }
            // Bigram
            if (i < words.length - 1) {
                String bigram = words[i] + " " + words[i+1];
                if (weights.containsKey(bigram)) {
                    score += weights.get(bigram);
                }
            }
        }

        return 1.0 / (1.0 + Math.exp(-score));
    }
}
