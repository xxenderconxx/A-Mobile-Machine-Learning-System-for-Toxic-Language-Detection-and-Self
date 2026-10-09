package com.example.toxiclanguagekeyboard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

/** JSON-lines evaluator of production Java classes. This is desktop evidence, not Android timing. */
public final class RevisionEvaluator {
    public static void main(String[] args) throws Exception {
        ToxicityClassifier classifier;
        SubstitutionEngine rules;
        try (InputStream stream = new FileInputStream(args[0])) { classifier = new ToxicityClassifier(stream); }
        try (InputStream stream = new FileInputStream(args[1])) { rules = new SubstitutionEngine(stream); }
        if (!classifier.isLoaded() || !rules.isLoaded()) throw new IllegalStateException("Assets did not load");
        DetectionPipeline pipeline = new DetectionPipeline(classifier, rules);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(args[2]), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(args[3], StandardCharsets.UTF_8.name())) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject input = new JSONObject(line);
                String text = input.getString("text");
                double base = classifier.calculateProbability(text, false, false);
                double entity = classifier.calculateProbability(text, true, false);
                DetectionPipeline.Result result = pipeline.evaluate(text, Collections.emptySet(), false);
                JSONObject out = new JSONObject();
                out.put("id", input.getString("id"));
                out.put("normalized", classifier.normalizeText(text));
                out.put("baseline_probability", base);
                out.put("probability", entity);
                out.put("professional_probability", classifier.calculateProbability(text, true, true));
                out.put("threshold", result.threshold);
                out.put("professional_threshold", classifier.getThreshold(true));
                out.put("classifier_label", result.classifierToxic);
                out.put("rules", result.ruleToxic);
                out.put("warning", result.warning);
                out.put("reason", result.reason());
                out.put("person_signal", classifier.featureCounts(text, true).containsKey("__ent_person__"));
                out.put("group_signal", classifier.featureCounts(text, true).containsKey("__ent_group__"));
                writer.println(out.toString());
            }
        }
    }
}
