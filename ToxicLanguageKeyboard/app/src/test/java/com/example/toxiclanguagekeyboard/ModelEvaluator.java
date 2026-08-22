package com.example.toxiclanguagekeyboard;

import org.junit.Test;
import java.io.*;
import java.util.*;

public class ModelEvaluator {

    @Test
    public void runFullEvaluation() throws Exception {
        System.out.println("=== Toxicity Model Benchmark ===");

        File modelFile = findFile("app/src/main/assets/model_weights.json");
        ToxicityClassifier classifier = new ToxicityClassifier(new FileInputStream(modelFile));

        // Load Ground Truth
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

        // Final Markdown Report
        System.out.println("\n## 🔬 Evaluation Summary Report");
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
        file = new File("D:/programs/ToxicLanguageKeyboard/" + path);
        if (file.exists()) return file;
        throw new FileNotFoundException("Could not find file: " + path);
    }

    private Map<String, Integer> loadLabels(File file) throws IOException {
        Map<String, Integer> labels = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.readLine(); // skip header: id,toxic,severe_toxic,obscene,threat,insult,identity_hate
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

    private EvaluationResult evaluate(ToxicityClassifier classifier, File testFile, Map<String, Integer> labels, boolean useEntity) throws IOException {
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

    private void processRow(String row, ToxicityClassifier classifier, Map<String, Integer> labels, boolean useEntity, EvaluationResult res) {
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
        boolean predicted = classifier.isToxic(comment, useEntity, false);
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

        System.out.println("FP Examples:");
        for (String s : r.fpExamples) System.out.println(" - " + s.replace("\n", " ").substring(0, Math.min(100, s.length())));
        System.out.println("FN Examples:");
        for (String s : r.fnExamples) System.out.println(" - " + s.replace("\n", " ").substring(0, Math.min(100, s.length())));
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