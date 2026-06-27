package com.example.toxiclanguagekeyboard;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SubstitutionEngine {

    private Map<String, String> substitutions = new HashMap<>();
    private java.util.Set<String> toxicWordsSet = new java.util.HashSet<>();

    public SubstitutionEngine(Context context) {
        loadSubstitutions(context);
    }

    private void loadSubstitutions(Context context) {
        try {
            InputStream is = context.getAssets().open("substitutions.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(json);
            Iterator<String> keys = jsonObject.keys();

            while (keys.hasNext()) {
                String key = keys.next().toLowerCase();
                String value = jsonObject.getString(key);
                substitutions.put(key, value);
                if (!key.equals(value)) {
                    toxicWordsSet.add(key);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getReframedSentence(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;
        // Sort keys by length (descending) to match longest phrases first (e.g., "shut up" before "up")
        java.util.List<String> keys = new java.util.ArrayList<>(substitutions.keySet());
        java.util.Collections.sort(keys, (a, b) -> Integer.compare(b.length(), a.length()));

        for (String key : keys) {
            String replacement = substitutions.get(key);
            // Use case-insensitive word boundary matching
            String regex = "(?i)\\b" + java.util.regex.Pattern.quote(key) + "\\b";
            result = result.replaceAll(regex, replacement);
        }

        return postProcessGrammar(result.trim().replaceAll("\\s+", " "));
    }

    private String postProcessGrammar(String text) {
        // Simple a/an correction: "a [vowel]" -> "an [vowel]" and "an [consonant]" -> "a [consonant]"
        // This helps when "stupid" (consonant-led) is replaced by "uninformed" (vowel-led)
        String processed = text.replaceAll("(?i)\\ba\\s+([aeiou])", "an $1");
        processed = processed.replaceAll("(?i)\\ban\\s+([^aeiou])", "a $1");
        return processed;
    }

    public boolean containsToxicLanguage(String input) {
        if (input == null || input.isEmpty()) return false;
        
        String[] words = input.toLowerCase().split("\\s+");
        for (String word : words) {
            String cleanWord = word.replaceAll("[^a-z]", "");
            if (isToxicWord(cleanWord)) {
                return true;
            }
        }
        return false;
    }

    public boolean isToxicWord(String word) {
        return toxicWordsSet.contains(word.toLowerCase());
    }

    public boolean isKnownWord(String word) {
        return substitutions.containsKey(word.toLowerCase());
    }
}
