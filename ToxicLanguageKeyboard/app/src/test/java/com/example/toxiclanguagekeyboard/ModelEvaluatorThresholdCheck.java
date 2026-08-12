package com.example.toxiclanguagekeyboard;

import org.junit.Test;
import java.io.*;
import java.util.*;

public class ModelEvaluatorThresholdCheck {

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
        
        System.out.println("\n### Threshold Sweep Comparison");
        System.out.println("| Threshold | Baseline F1 | Enhanced F1 | Accuracy (Enh) | Precision (Enh) | Recall (Enh) |");
        System.out.println("| :--- | :--- | :--- | :--- | :--- | :--- |");
        
        double bestF1 = 0;
        double bestThreshold = 0.5;
        
        for (double t = 0.1; t <= 0.95; t += 0.05) {
            EvaluationResult baseRes = evaluateWithThreshold(classifier, findFile("app/src/test/assets/test.csv"), labels, false, t);
            EvaluationResult enhRes = evaluateWithThreshold(classifier, findFile("app/src/test/assets/test.csv"), labels, true, t);
            
            System.out.printf("| %.2f | %.2f%% | %.2f%% | %.2f%% | %.2f%% | %.2f%% |\n", 
                t, baseRes.getF1()*100, enhRes.getF1()*100, enhRes.getAccuracy()*100, enhRes.getPrecision()*100, enhRes.getRecall()*100);
                
            if (enhRes.getF1() > bestF1) {
                bestF1 = enhRes.getF1();
                bestThreshold = t;
            }
        }
        
        System.out.println("\nPeak F1 Score: " + String.format("%.2f%%", bestF1 * 100) + " at threshold " + String.format("%.2f", bestThreshold));
    }

    private EvaluationResult evaluateWithThreshold(ToxicityClassifier classifier, File testFile, Map<String, Integer> labels, boolean useEntity, double threshold) throws IOException {
        EvaluationResult res = new EvaluationResult();
        try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
            reader.readLine();
            StringBuilder entryBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                entryBuilder.append(line).append("\n");
                String current = entryBuilder.toString();
                if (isCompleteCsvRow(current)) {
                    processRowWithThreshold(current, classifier, labels, useEntity, threshold, res);
                    entryBuilder.setLength(0);
                }
            }
        }
        return res;
    }

    private void processRowWithThreshold(String row, ToxicityClassifier classifier, Map<String, Integer> labels, boolean useEntity, double threshold, EvaluationResult res) {
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
        
        double probability = classifier.calculateProbability(comment, useEntity);
        boolean predicted = probability >= threshold;
        
        res.count++;
        if (predicted && actual == 1) res.tp++;
        else if (!predicted && actual == 0) res.tn++;
        else if (predicted && actual == 0) res.fp++;
        else if (!predicted && actual == 1) res.fn++;
    }

    private File findFile(String path) throws FileNotFoundException {
        File file = new File(path);
        if (file.exists()) return file;
        file = new File(path.replace("app/", ""));
        if (file.exists()) return file;
        // Try looking in the project root if it's there
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
                
                boolean isToxic = false;
                boolean isValid = true;
                for (int i = 1; i < parts.length; i++) {
                    int val = Integer.parseInt(parts[i]);
                    if (val == -1) {
                        isValid = false;
                        break;
                    }
                    if (val == 1) isToxic = true;
                }
                
                if (isValid) {
                    labels.put(id, isToxic ? 1 : 0);
                }
            }
        }
        return labels;
    }

    private EvaluationResult evaluate(ToxicityClassifier classifier, File testFile, Map<String, Integer> labels, boolean useEntity) throws IOException {
        EvaluationResult res = new EvaluationResult();
        
        // Simple manual CSV parser to handle quotes and newlines
        try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
            reader.readLine(); // skip header
            
            StringBuilder entryBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                entryBuilder.append(line).append("\n");
                
                // If the entry is complete (has balanced quotes)
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
        // A standard row in this CSV: "id","comment"
        // Should have at least 4 quotes and an even number total
        return quoteCount >= 4 && quoteCount % 2 == 0;
    }

    private void processRow(String row, ToxicityClassifier classifier, Map<String, Integer> labels, boolean useEntity, EvaluationResult res) {
        // Format: "id","comment"
        int firstQuote = row.indexOf("\"");
        int secondQuote = row.indexOf("\"", firstQuote + 1);
        if (firstQuote == -1 || secondQuote == -1) return;
        
        String id = row.substring(firstQuote + 1, secondQuote);
        if (!labels.containsKey(id)) return;
        
        int actual = labels.get(id);
        
        int commentStart = row.indexOf("\"", secondQuote + 1);
        int commentEnd = row.lastIndexOf("\"");
        if (commentStart == -1 || commentEnd == -1 || commentStart >= commentEnd) return;
        
        String comment = row.substring(commentStart + 1, commentEnd)
                            .replace("\"\"", "\""); // handle escaped quotes
        
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
