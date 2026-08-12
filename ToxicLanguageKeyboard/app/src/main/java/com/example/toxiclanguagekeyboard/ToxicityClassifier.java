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

    /**
     * Constructor for testing/benchmarking without Android Context.
     */
    public ToxicityClassifier(InputStream inputStream) {
        loadModelFromStream(inputStream);
    }

    private void loadModelAsync(Context context) {
        new Thread(() -> {
            try {
                InputStream is = context.getAssets().open("model_weights.json");
                loadModelFromStream(is);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadModelFromStream(InputStream is) {
        try {
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
    }

    public boolean isLoaded = false;

    private String normalizeText(String text) {
        if (text == null) return "";
        
        // REQ-2.1: Ignore Email addresses and URLs (often part of signatures/technical text)
        String filtered = text.replaceAll("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+", " ")
                             .replaceAll("https?://\\S+\\s?", " ");

        // Obfuscation Handling: Replace common character substitutions
        String normalized = filtered.toLowerCase()
                .replace("$", "s")
                .replace("@", "a")
                .replace("!", "i")
                .replace("1", "i")
                .replace("0", "o")
                .replace("3", "e")
                .replace("4", "a")
                .replace("5", "s")
                .replace("8", "b")
                .replace("7", "t")
                .replace("+", "t")
                .replace("*", "")  // Remove asterisks used in obfuscation (e.g., f*ck -> fck)
                .replace("_", "")
                .replace("-", "");
        
        return normalized.replaceAll("[^a-z\\s]", "").trim();
    }

    /**
     * REQ-2.2: Entity-Aware Feature Extraction
     * Identifies if the text targets a specific person, group, or proper noun.
     */
    private double extractEntityFeatures(String originalText, String[] normalizedWords) {
        boolean targetsPerson = false;
        boolean targetsGroup = false;
        boolean targetsProperNoun = false;

        // 1. Detect Proper Nouns (Heuristic: Capitalized words not at start of sentence)
        String[] originalWords = originalText.trim().split("\\s+");
        for (int i = 1; i < originalWords.length; i++) {
            if (originalWords[i].length() > 0 && Character.isUpperCase(originalWords[i].charAt(0))) {
                targetsProperNoun = true;
                break;
            }
        }

        // 2. Detect Target Pronouns/Entities
        java.util.Set<String> personTokens = new java.util.HashSet<>(java.util.Arrays.asList(
                "you", "u", "he", "she", "him", "her", "yourself", "yourselves"
        ));
        java.util.Set<String> groupTokens = new java.util.HashSet<>(java.util.Arrays.asList(
                "they", "them", "people", "everyone", "group", "all", "those"
        ));

        for (String word : normalizedWords) {
            if (personTokens.contains(word)) targetsPerson = true;
            if (groupTokens.contains(word)) targetsGroup = true;
        }

        // Apply weights for targeted hostility
        if (targetsPerson && targetsProperNoun) return 1.45; // Targeted individual attack
        if (targetsPerson) return 1.3;  // General personal attack
        if (targetsGroup) return 1.2;   // Group hostility
        if (targetsProperNoun) return 1.1; // Mention of specific entity
        
        return 1.0;
    }

    public boolean isToxic(String text) {
        return isToxic(text, true);
    }

    public boolean isToxic(String text, boolean useEntityFeatures) {
        if (!isLoaded || text == null || text.trim().isEmpty()) return false;

        String cleaned = normalizeText(text);
        if (cleaned.isEmpty()) return false;

        String[] words = cleaned.split("\\s+");
        int wordCount = words.length;

        // Use original text for entity detection to preserve capitalization
        double probability = calculateProbability(text, useEntityFeatures);
        
        // Threshold chosen to balance Precision and Recall for V9 weights.
        // Peak F1 at 0.90 is too high for a safety feature (Recall would be too low).
        // 0.45 offers F1 ~56% with Recall ~80%, providing a good safety margin.
        double threshold = (wordCount >= 3) ? 0.45 : 0.55;

        return probability >= threshold;
    }

    public double calculateProbability(String text) {
        return calculateProbability(text, true);
    }

    public double calculateProbability(String text, boolean useEntityFeatures) {
        if (text == null || text.isEmpty()) return 0.0;
        
        String cleaned = normalizeText(text);
        if (cleaned.isEmpty()) return 0.0;

        double score = intercept;
        String[] words = cleaned.split("\\s+");

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

        // Apply Entity-Aware Multiplier (REQ-2.2)
        if (useEntityFeatures) {
            score *= extractEntityFeatures(text, words);
        }

        return 1.0 / (1.0 + Math.exp(-score));
    }
}
