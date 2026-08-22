package com.example.toxiclanguagekeyboard;

import org.json.JSONObject;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StandaloneModelEvaluator {

    // =========================================================================
    // 1. EMBEDDED TOXICITY CLASSIFIER LOGIC
    // =========================================================================
    public static class StandaloneClassifier {

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
        private double threshold = 0.78; // Default calibrated threshold
        private boolean isLoaded = false;

        public StandaloneClassifier(File jsonFile) throws IOException {
            loadModelFromFile(jsonFile);
        }

        private void loadModelFromFile(File file) throws IOException {
            try (InputStream is = new FileInputStream(file)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();

                String json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(json);

                this.intercept = jsonObject.optDouble("intercept", 0.0);
                this.threshold = jsonObject.optDouble("threshold", 0.78);

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
                this.isLoaded = true;
            } catch (Exception e) {
                throw new IOException("Failed to load JSON model: " + e.getMessage(), e);
            }
        }

        public double getThreshold() { return threshold; }
        public int getWeightCount() { return weights.size(); }
        public boolean isLoaded() { return isLoaded; }

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

            // Word Unigrams & Bigrams
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

            // Character Boundary N-grams (3 to 5 chars) matching analyzer='char_wb'
            String padded = " " + cleanedText + " ";
            int len = padded.length();
            for (int n = 3; n <= 5; n++) {
                for (int i = 0; i <= len - n; i++) {
                    String ngram = padded.substring(i, i + n);
                    String charKey = "char_" + ngram;
                    counts.put(charKey, counts.getOrDefault(charKey, 0) + 1);
                }
            }

            // Entity Features
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

        public double calculateProbability(String text, boolean useEntityFeatures) {
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
                    double tf = 1.0 + Math.log(count);
                    double tfidf = tf * meta.idf;

                    dotProduct += tfidf * meta.weight;
                    sumSquaredTfidf += tfidf * tfidf;
                }
            }

            double l2Norm = sumSquaredTfidf > 0.0 ? Math.sqrt(sumSquaredTfidf) : 1.0;
            double score = intercept + (dotProduct / l2Norm);

            return 1.0 / (1.0 + Math.exp(-score));
        }

        public boolean isToxic(String text, boolean useEntityFeatures) {
            return calculateProbability(text, useEntityFeatures) >= this.threshold;
        }
    }

    // =========================================================================
    // 2. BENCHMARK RUNNER & EVALUATION SUITE
    // =========================================================================
    @Test
    public void runFullEvaluation() throws Exception {
        System.out.println("=== Standalone Toxicity Model Benchmark ===");

        File modelFile = findFile("app/src/main/assets/model_weights.json");
        System.out.println("Loading JSON from: " + modelFile.getAbsolutePath());

        StandaloneClassifier classifier = new StandaloneClassifier(modelFile);
        System.out.printf("Loaded %d features | Active Threshold: %.2f\n",
                classifier.getWeightCount(), classifier.getThreshold());

        // Load Ground Truth Labels
        Map<String, Integer> labels = loadLabels(findFile("app/src/test/assets/test_labels.csv"));
        System.out.println("Loaded " + labels.size() + " valid labeled test samples.");

        // Evaluate Baseline (Entity-Aware OFF)
        System.out.println("\n--- Evaluating Baseline (TF-IDF only) ---");
        EvaluationResult baseline = evaluate(classifier, findFile("app/src/test/assets/test.csv"), labels, false);
        printResults(baseline);

        // Evaluate Enhanced (Entity-Aware ON)
        System.out.println("\n--- Evaluating Enhanced (TF-IDF + Entity-Aware) ---");
        EvaluationResult enhanced = evaluate(classifier, findFile("app/src/test/assets/test.csv"), labels, true);
        printResults(enhanced);

        // Final Report
        System.out.println("\n## 🔬 Standalone Evaluation Summary Report");
        System.out.println("\n### Entity-Aware Feature Extraction Ablation Study");
        System.out.println("| Model Variant | Accuracy | Precision | Recall | F1-Score | Avg Latency |");
        System.out.println("| :--- | :--- | :--- | :--- | :--- | :--- |");
        printRow("TF-IDF only (baseline)", baseline);
        printRow("TF-IDF + Entity-Aware", enhanced);

        System.out.println("\n### Confusion Matrix (Enhanced Model)");
        System.out.println("| | Predicted (Non-toxic) | Predicted (Toxic) |");
        System.out.println("| :--- | :--- | :--- |");
        System.out.printf("| **Actual (Non-toxic)** | %d (TN) | %d (FP) |\n", enhanced.tn, enhanced.fp);
        System.out.printf("| **Actual (Toxic)** | %d (FN) | %d (TP) |\n", enhanced.fn, enhanced.tp);
    }

    private File findFile(String path) throws FileNotFoundException {
        File file = new File(path);
        if (file.exists()) return file;
        file = new File(path.replace("app/", ""));
        if (file.exists()) return file;
        throw new FileNotFoundException("Could not find file: " + path);
    }

    private Map<String, Integer> loadLabels(File file) throws IOException {
        Map<String, Integer> labels = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 7) continue;
                String id = parts[0];

                int toxic = Integer.parseInt(parts[1]);
                int severeToxic = Integer.parseInt(parts[2]);
                int obscene = Integer.parseInt(parts[3]);
                int threat = Integer.parseInt(parts[4]);
                int insult = Integer.parseInt(parts[5]);
                int identityHate = Integer.parseInt(parts[6]);

                if (toxic != -1) {
                    boolean isToxicAny = (toxic == 1 || severeToxic == 1 || obscene == 1 || threat == 1 || insult == 1 || identityHate == 1);
                    labels.put(id, isToxicAny ? 1 : 0);
                }
            }
        }
        return labels;
    }

    private EvaluationResult evaluate(StandaloneClassifier classifier, File testFile, Map<String, Integer> labels, boolean useEntity) throws IOException {
        EvaluationResult res = new EvaluationResult();

        try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
            reader.readLine(); // skip header

            StringBuilder entryBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                entryBuilder.append(line).append("\n");

                String current = entryBuilder.toString();
                if (isCompleteCsvRow(current)) {
                    processRow(current, classifier, labels, useEntity, res);
                    entryBuilder.setLength(0);
                }
            }
        }
        return res;
    }

    private boolean isCompleteCsvRow(String row) {
        int quoteCount = 0;
        for (char c : row.toCharArray()) {
            if (c == '\"') quoteCount++;
        }
        return quoteCount >= 4 && quoteCount % 2 == 0;
    }

    private void processRow(String row, StandaloneClassifier classifier, Map<String, Integer> labels, boolean useEntity, EvaluationResult res) {
        int firstQuote = row.indexOf("\"");
        int secondQuote = row.indexOf("\"", firstQuote + 1);
        if (firstQuote == -1 || secondQuote == -1) return;

        String id = row.substring(firstQuote + 1, secondQuote);
        if (!labels.containsKey(id)) return;

        int actual = labels.get(id);

        int commentStart = row.indexOf("\"", secondQuote + 1);
        int commentEnd = row.lastIndexOf("\"");
        if (commentStart == -1 || commentEnd == -1 || commentStart >= commentEnd) return;

        String comment = row.substring(commentStart + 1, commentEnd).replace("\"\"", "\"");

        long start = System.nanoTime();
        boolean predicted = classifier.isToxic(comment, useEntity);
        long end = System.nanoTime();

        res.totalLatencyNs += (end - start);
        res.count++;

        if (predicted && actual == 1) {
            res.tp++;
        } else if (!predicted && actual == 0) {
            res.tn++;
        } else if (predicted && actual == 0) {
            res.fp++;
            if (res.fpExamples.size() < 10) res.fpExamples.add(comment);
        } else if (!predicted && actual == 1) {
            res.fn++;
            if (res.fnExamples.size() < 10) res.fnExamples.add(comment);
        }

        if (res.count % 5000 == 0) System.out.print(".");
    }

    private void printResults(EvaluationResult r) {
        System.out.printf("\nProcessed %d samples.\n", r.count);
        System.out.printf("Accuracy: %.2f%% | Precision: %.2f%% | Recall: %.2f%% | F1: %.2f%%\n",
                r.getAccuracy() * 100, r.getPrecision() * 100, r.getRecall() * 100, r.getF1() * 100);
    }

    private void printRow(String name, EvaluationResult r) {
        System.out.printf("| %s | %.2f%% | %.2f%% | %.2f%% | %.2f%% | %.2f ms |\n",
                name, r.getAccuracy() * 100, r.getPrecision() * 100, r.getRecall() * 100, r.getF1() * 100, r.getAvgLatencyMs());
    }

    private static class EvaluationResult {
        int tp, tn, fp, fn, count;
        long totalLatencyNs;
        List<String> fpExamples = new ArrayList<>();
        List<String> fnExamples = new ArrayList<>();
        double getAccuracy() { return count == 0 ? 0 : (double)(tp + tn) / count; }
        double getPrecision() { return (tp + fp) == 0 ? 0 : (double)tp / (tp + fp); }
        double getRecall() { return (tp + fn) == 0 ? 0 : (double)tp / (tp + fn); }
        double getF1() {
            double p = getPrecision(), r = getRecall();
            return (p + r) == 0 ? 0 : 2 * p * r / (p + r);
        }
        double getAvgLatencyMs() { return count == 0 ? 0 : (double)totalLatencyNs / count / 1_000_000.0; }
    }
}