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
    private static final double THRESHOLD = 0.7;

    public ToxicityClassifier(Context context) {
        loadModel(context);
    }

    private void loadModel(Context context) {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isToxic(String text) {
        if (text == null || text.isEmpty()) return false;
        return calculateProbability(text) >= THRESHOLD;
    }

    public double calculateProbability(String text) {
        String input = text.toLowerCase().replaceAll("[^a-z ]", "");
        double score = intercept;

        // Check for single words and bigrams (as found in model_weights.json)
        String[] words = input.split("\\s+");
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
