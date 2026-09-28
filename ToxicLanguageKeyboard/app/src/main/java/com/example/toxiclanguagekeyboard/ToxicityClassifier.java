package com.example.toxiclanguagekeyboard;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ToxicityClassifier {

    private static final String TAG = "ToxicityClassifier";

    private static class FeatureMeta {
        final double weight;
        final double idf;

        FeatureMeta(double weight, double idf) {
            this.weight = weight;
            this.idf = idf;
        }
    }

    private final Map<String, FeatureMeta> weights = new HashMap<>();
    private double intercept = 0.0;
    private double defaultThreshold = 0.70;
    private boolean isLoaded = false;

    public ToxicityClassifier(Context context) {
        loadModelAsync(context);
    }

    public ToxicityClassifier(InputStream inputStream) {
        loadModelFromStream(inputStream);
    }

    private void loadModelAsync(Context context) {
        new Thread(() -> {
            try (InputStream is = context.getAssets().open("model_weights.json")) {
                loadModelFromStream(is);
            } catch (Exception e) {
                Log.e(TAG, "Error loading model async", e);
            }
        }).start();
    }

    public synchronized void loadModelFromStream(InputStream is) {
        try {
            // Read complete stream into memory (fixes is.available() truncation bug)
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();

            String json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(json);

            intercept = jsonObject.optDouble("intercept", 0.0);
            defaultThreshold = jsonObject.optDouble("threshold", 0.70);

            JSONObject weightsJson = jsonObject.getJSONObject("weights");
            Iterator<String> keys = weightsJson.keys();

            weights.clear();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject item = weightsJson.getJSONObject(key);
                double w = item.getDouble("w");
                double idf = item.getDouble("idf");
                weights.put(key, new FeatureMeta(w, idf));
            }
            isLoaded = true;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing model_weights.json", e);
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public String normalizeText(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(Locale.ENGLISH);
        lower = lower.replaceAll("\\d+px|style=.*|#\\w+|color\\s\\w+", "");
        lower = lower.replaceAll("(.)\\1{2,}", "$1");
        lower = lower.replace("@", "a")
                .replace("0", "o")
                .replace("1", "i")
                .replace("3", "e")
                .replace("$", "s")
                .replace("!", "i");
        String cleaned = lower.replaceAll("[^a-z\\s]", " ");
        return cleaned.trim().replaceAll("\\s+", " ");
    }

    private Map<String, Integer> extractAllFeatures(String cleanedText, boolean useEntityFeatures) {
        Map<String, Integer> counts = new HashMap<>();
        if (cleanedText.isEmpty()) return counts;

        // 1. Word Unigrams & Bigrams
        String[] words = cleanedText.split("\\s+");
        List<String> validWords = new ArrayList<>();
        for (String w : words) {
            if (!w.isEmpty()) validWords.add(w);
        }

        for (int i = 0; i < validWords.size(); i++) {
            String word = validWords.get(i);
            counts.put(word, counts.getOrDefault(word, 0) + 1);

            if (i < validWords.size() - 1) {
                String bigram = word + " " + validWords.get(i + 1);
                counts.put(bigram, counts.getOrDefault(bigram, 0) + 1);
            }
        }

        // 2. Character Boundary N-grams (3 to 5 chars) matching analyzer='char_wb'
        String padded = " " + cleanedText + " ";
        int len = padded.length();
        for (int n = 3; n <= 5; n++) {
            for (int i = 0; i <= len - n; i++) {
                String ngram = padded.substring(i, i + n);
                String charKey = "char_" + ngram;
                counts.put(charKey, counts.getOrDefault(charKey, 0) + 1);
            }
        }

        // 3. Entity Features (Toggled by parameter for ablation tests)
        if (useEntityFeatures) {
            Set<String> personTokens = new HashSet<>(Arrays.asList("you", "u", "he", "she", "him", "her", "yourself"));
            Set<String> groupTokens = new HashSet<>(Arrays.asList("they", "them", "people", "everyone", "group"));

            boolean hasPerson = false;
            boolean hasGroup = false;

            for (String w : validWords) {
                if (personTokens.contains(w)) hasPerson = true;
                if (groupTokens.contains(w)) hasGroup = true;
            }

            if (hasPerson) counts.put("__ent_person__", 1);
            if (hasGroup) counts.put("__ent_group__", 1);
        }

        return counts;
    }

    public double calculateProbability(String text) {
        return calculateProbability(text, false, false);
    }

    public double calculateProbability(String text, boolean useEntityFeatures, boolean isProfessionalContext) {
        if (text == null || text.trim().isEmpty()) return 0.0;

        String cleaned = normalizeText(text);
        if (cleaned.isEmpty()) return 0.0;

        Map<String, Integer> tokenCounts = extractAllFeatures(cleaned, useEntityFeatures);

        double dotProduct = 0.0;
        double sumSquaredTfidf = 0.0;

        for (Map.Entry<String, Integer> entry : tokenCounts.entrySet()) {
            String token = entry.getKey();
            int count = entry.getValue();

            FeatureMeta meta = weights.get(token);
            if (meta != null) {
                // Sublinear TF Scaling: 1 + ln(tf)
                double tf = 1.0 + Math.log(count);
                double tfidf = tf * meta.idf;

                dotProduct += tfidf * meta.weight;
                sumSquaredTfidf += tfidf * tfidf;
            }
        }

        double l2Norm = sumSquaredTfidf > 0 ? Math.sqrt(sumSquaredTfidf) : 1.0;
        double score = intercept + (dotProduct / l2Norm);

        // Adjust log-odds score in professional apps (e.g., Slack, LinkedIn) to lower false alarms
        if (isProfessionalContext) {
            score -= 0.25;
        }

        return 1.0 / (1.0 + Math.exp(-score));
    }

    public boolean isToxic(String text) {
        return isToxic(text, false, false);
    }

    public boolean isToxic(String text, double threshold) {
        if (!isLoaded || text == null || text.trim().isEmpty()) return false;
        return calculateProbability(text, false, false) >= threshold;
    }

    public boolean isToxic(String text, boolean useEntityFeatures, boolean isProfessionalContext) {
        if (!isLoaded || text == null || text.trim().isEmpty()) return false;
        double effectiveThreshold = isProfessionalContext ? (defaultThreshold - 0.05) : defaultThreshold;
        return calculateProbability(text, useEntityFeatures, isProfessionalContext) >= effectiveThreshold;
    }
}