package com.example.toxiclanguagekeyboard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.json.JSONObject;

/** Offline full labelled-test-set runner for the production classifier/rules decision path. */
public final class RevisionFullPipelineEvaluator {
    public static void main(String[] args) throws Exception {
        ToxicityClassifier classifier;
        SubstitutionEngine rules;
        try (InputStream input = new FileInputStream(args[0])) { classifier = new ToxicityClassifier(input); }
        try (InputStream input = new FileInputStream(args[1])) { rules = new SubstitutionEngine(input); }
        if (!classifier.isLoaded() || !rules.isLoaded()) throw new IllegalStateException("Production assets failed to load");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(args[2]), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(args[3], StandardCharsets.UTF_8.name())) {
            String line;
            long count = 0;
            while ((line = reader.readLine()) != null) {
                JSONObject row = new JSONObject(line);
                String text = row.getString("text");
                boolean suppressed = rules.isWhitelistedPhrase(text);
                boolean ruleToxic = rules.containsToxicLanguage(text);
                double probability = classifier.calculateProbability(text, true, false);
                JSONObject result = new JSONObject();
                result.put("id", row.getString("id"));
                result.put("probability", probability);
                result.put("classifier", probability >= classifier.getThreshold(false));
                result.put("rules", ruleToxic);
                result.put("suppressed", suppressed);
                result.put("combined", !suppressed && (probability >= classifier.getThreshold(false) || ruleToxic));
                writer.println(result);
                if (++count % 10000 == 0) System.err.println("Evaluated " + count + " records");
            }
        }
    }
}
