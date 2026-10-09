package com.example.toxiclanguagekeyboard;

import java.util.Locale;
import java.util.Set;

/** Shared keyboard decision path. Diagnostics contain flags/timing, never retain input text. */
public final class DetectionPipeline {
    private final ToxicityClassifier classifier;
    private final SubstitutionEngine rules;
    public DetectionPipeline(ToxicityClassifier classifier, SubstitutionEngine rules) {
        this.classifier = classifier;
        this.rules = rules;
    }
    public static String feedbackKey(String text) {
        return text.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z'\\s]", " ")
                .trim().replaceAll("\\s+", " ");
    }
    public static final class Result {
        public final boolean ready, classifierToxic, ruleToxic, suppressed, warning;
        public final double probability, threshold;
        public final long classifierNanos, pipelineNanos;
        Result(boolean ready, boolean ml, boolean rule, boolean suppressed,
               double probability, double threshold, long classifierNanos, long pipelineNanos) {
            this.ready = ready;
            this.classifierToxic = ml;
            this.ruleToxic = rule;
            this.suppressed = suppressed;
            this.warning = ready && !suppressed && (ml || rule);
            this.probability = probability;
            this.threshold = threshold;
            this.classifierNanos = classifierNanos;
            this.pipelineNanos = pipelineNanos;
        }
        public String reason() {
            if (!ready) return "not_ready";
            if (suppressed) return "personal_or_phrase_override";
            return classifierToxic && ruleToxic ? "model_and_rules" : classifierToxic ? "model" : ruleToxic ? "rules" : "none";
        }
    }
    public Result evaluate(String text, Set<String> falsePositives, boolean professional) {
        long started = System.nanoTime();
        double threshold = classifier.getThreshold(professional);
        if (!classifier.isLoaded() || !rules.isLoaded())
            return new Result(false, false, false, false, 0, threshold, 0, System.nanoTime() - started);
        boolean suppressed = rules.isWhitelistedPhrase(text) || falsePositives.contains(feedbackKey(text));
        boolean explicit = rules.containsToxicLanguage(text);
        long mlStart = System.nanoTime();
        double probability = classifier.calculateProbability(text, true, professional);
        long mlNanos = System.nanoTime() - mlStart;
        return new Result(true, probability >= threshold, explicit, suppressed,
                probability, threshold, mlNanos, System.nanoTime() - started);
    }
}
