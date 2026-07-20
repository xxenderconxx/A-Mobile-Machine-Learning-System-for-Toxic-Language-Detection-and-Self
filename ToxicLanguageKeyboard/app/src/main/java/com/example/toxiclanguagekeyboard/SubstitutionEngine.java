package com.example.toxiclanguagekeyboard;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SubstitutionEngine {

    private Map<String, String> substitutions = new HashMap<>();
    private java.util.Set<String> toxicWordsSet = new java.util.HashSet<>();
    private java.util.Set<String> toxicSkeletons = new java.util.HashSet<>();
    private java.util.Set<String> knownSkeletons = new java.util.HashSet<>();
    private Map<String, String> skeletonToReplacement = new HashMap<>();
    private List<String> sortedSubstitutionKeys = new ArrayList<>();
    private Map<String, Pattern> phrasePatterns = new HashMap<>();
    private Map<String, Pattern> safePhrasePatterns = new HashMap<>();
    
    private java.util.Set<String> dynamicToxicWords = new java.util.HashSet<>();
    private java.util.Set<String> dynamicToxicSkeletons = new java.util.HashSet<>();
    
    private boolean isLoaded = false;

    public SubstitutionEngine(Context context) {
        loadSubstitutionsAsync(context);
    }

    private void loadSubstitutionsAsync(Context context) {
        new Thread(() -> {
            try {
                InputStream is = context.getAssets().open("substitutions.json");
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                String json = new String(buffer, StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(json);
                Iterator<String> keys = jsonObject.keys();

                Map<String, String> tempSubstitutions = new HashMap<>();
                java.util.Set<String> tempToxicWordsSet = new java.util.HashSet<>();
                java.util.Set<String> tempToxicSkeletons = new java.util.HashSet<>();
                java.util.Set<String> tempKnownSkeletons = new java.util.HashSet<>();
                Map<String, String> tempSkeletonToReplacement = new HashMap<>();
                Map<String, Pattern> tempPhrasePatterns = new HashMap<>();
                Map<String, Pattern> tempSafePhrasePatterns = new HashMap<>();

                while (keys.hasNext()) {
                    String key = keys.next().toLowerCase();
                    String value = jsonObject.getString(key);
                    tempSubstitutions.put(key, value);
                    String skeleton = key.replaceAll("(.)\\1+", "$1");
                    tempKnownSkeletons.add(skeleton);
                    
                    if (!key.equals(value)) {
                        tempToxicWordsSet.add(key);
                        tempToxicSkeletons.add(skeleton);
                        if (!tempSkeletonToReplacement.containsKey(skeleton)) {
                            tempSkeletonToReplacement.put(skeleton, value);
                        }
                        // Pre-compile patterns for phrases (containing spaces) or words
                        tempPhrasePatterns.put(key, Pattern.compile(buildPhraseRegex(key)));
                    } else {
                        // Whitelisted words/phrases (key == value)
                        tempSafePhrasePatterns.put(key, Pattern.compile(buildPhraseRegex(key)));
                    }
                }
                
                List<String> tempSortedKeys = new ArrayList<>(tempSubstitutions.keySet());
                Collections.sort(tempSortedKeys, (a, b) -> Integer.compare(b.length(), a.length()));

                substitutions = tempSubstitutions;
                toxicWordsSet = tempToxicWordsSet;
                toxicSkeletons = tempToxicSkeletons;
                knownSkeletons = tempKnownSkeletons;
                skeletonToReplacement = tempSkeletonToReplacement;
                phrasePatterns = tempPhrasePatterns;
                safePhrasePatterns = tempSafePhrasePatterns;
                sortedSubstitutionKeys = tempSortedKeys;
                isLoaded = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public void setDynamicToxicWords(java.util.Set<String> words) {
        this.dynamicToxicWords = new java.util.HashSet<>(words);
        this.dynamicToxicSkeletons.clear();
        for (String w : words) {
            dynamicToxicSkeletons.add(w.toLowerCase().replaceAll("(.)\\1+", "$1"));
        }
    }

    public String getReframedSentence(String input) {
        if (!isLoaded || input == null || input.isEmpty()) {
            return input;
        }

        String result = input;
        
        // 1. First pass: Replace exact phrases and words (longest first) using cached patterns
        for (String key : sortedSubstitutionKeys) {
            String replacement = substitutions.get(key);
            if (key.equals(replacement)) continue;

            Pattern pattern = phrasePatterns.get(key);
            if (pattern != null) {
                result = pattern.matcher(result).replaceAll(replacement);
            }
        }

        // 2. Second pass: Handle elongated words and dynamic toxic words
        String[] words = result.split("\\s+");
        StringBuilder sb = new StringBuilder();
        boolean changed = false;

        for (String word : words) {
            if (word.isEmpty()) continue;
            
            // Keep apostrophes for words like "don't"
            String clean = word.toLowerCase().replaceAll("[^a-z']", "");
            if (clean.isEmpty()) {
                sb.append(word).append(" ");
                continue;
            }

            String replacement = null;
            
            // a. Check dynamic toxic words (user-marked)
            if (isDynamicToxic(clean)) {
                replacement = "***"; 
            } 
            // b. Check elongated toxic words
            else {
                String skeleton = clean.replaceAll("(.)\\1+", "$1");
                if (toxicSkeletons.contains(skeleton)) {
                    replacement = skeletonToReplacement.get(skeleton);
                }
            }
            
            if (replacement != null) {
                // Attempt to preserve punctuation (non-alpha/apostrophe)
                Matcher mStart = Pattern.compile("^([^a-z']+)").matcher(word.toLowerCase());
                Matcher mEnd = Pattern.compile("([^a-z']+)$").matcher(word.toLowerCase());
                String prefix = mStart.find() ? mStart.group(1) : "";
                String suffix = mEnd.find() ? mEnd.group(1) : "";
                
                sb.append(prefix).append(replacement).append(suffix).append(" ");
                changed = true;
            } else {
                sb.append(word).append(" ");
            }
        }

        String finalResult = changed ? sb.toString().trim() : result;
        return postProcessGrammar(finalResult.replaceAll("\\s+", " "));
    }

    private String buildPhraseRegex(String phrase) {
        String[] parts = phrase.split("\\s+");
        StringBuilder sb = new StringBuilder("(?i)\\b");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (Character.isLetter(c)) {
                    // Allow character repetition for each letter in the phrase
                    sb.append(Pattern.quote(String.valueOf(c))).append("+");
                } else {
                    sb.append(Pattern.quote(String.valueOf(c)));
                }
            }
            if (i < parts.length - 1) {
                // Allow punctuation and whitespace between words in a phrase
                sb.append("[^a-zA-Z']+"); 
            }
        }
        sb.append("\\b");
        return sb.toString();
    }

    private boolean isDynamicToxic(String word) {
        if (dynamicToxicWords.contains(word)) return true;
        String skeleton = word.replaceAll("(.)\\1+", "$1");
        return dynamicToxicSkeletons.contains(skeleton);
    }

    private String postProcessGrammar(String text) {
        // 1. Correct a/an usage
        String processed = text.replaceAll("(?i)\\ba\\s+([aeiou])", "an $1");
        processed = processed.replaceAll("(?i)\\ban\\s+([^aeiou])", "a $1");
        
        // 2. Remove multiple spaces
        processed = processed.replaceAll("\\s+", " ");
        
        // 3. Remove spaces before punctuation
        processed = processed.replaceAll("\\s+([.,!?;])", "$1");
        
        return processed.trim();
    }

    public boolean containsToxicLanguage(String input) {
        if (input == null || input.isEmpty()) return false;

        String checkInput = input.toLowerCase();

        // 0. Check for whitelisted phrases first to prevent false positives.
        // We iterate through all safe phrases. If one is found, we mask it out 
        // to prevent it from triggering the toxic check.
        boolean hasSafePhrase = false;
        for (Map.Entry<String, Pattern> entry : safePhrasePatterns.entrySet()) {
            Matcher matcher = entry.getValue().matcher(checkInput);
            if (matcher.find()) {
                checkInput = matcher.replaceAll(" [SAFE_PHRASE] ");
                hasSafePhrase = true;
            }
        }
        
        // 1. Check for toxic phrases from dictionary (exact and elongated)
        for (Map.Entry<String, Pattern> entry : phrasePatterns.entrySet()) {
            if (entry.getValue().matcher(checkInput).find()) {
                return true;
            }
        }

        // 2. Check for single dynamic toxic words/skeletons
        String[] words = checkInput.split("\\s+");
        for (String word : words) {
            if (word.equals("[SAFE_PHRASE]")) continue;
            String cleanWord = word.replaceAll("[^a-z']", "");
            if (!cleanWord.isEmpty() && isToxicWord(cleanWord)) {
                return true;
            }
        }
        return false;
    }

    public boolean isToxicWord(String word) {
        if (word == null) return false;
        String lower = word.toLowerCase().trim();
        if (toxicWordsSet.contains(lower) || dynamicToxicWords.contains(lower)) return true;

        String skeleton = lower.replaceAll("(.)\\1+", "$1");
        return toxicSkeletons.contains(skeleton) || dynamicToxicSkeletons.contains(skeleton);
    }

    public boolean isWhitelistedPhrase(String input) {
        if (!isLoaded || input == null) return false;
        String lower = input.toLowerCase().trim();
        for (Pattern pattern : safePhrasePatterns.values()) {
            if (pattern.matcher(lower).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean isKnownWord(String word) {
        if (word == null) return false;
        String lower = word.toLowerCase().trim();
        if (substitutions.containsKey(lower)) return true;

        String skeleton = lower.replaceAll("(.)\\1+", "$1");
        return knownSkeletons.contains(skeleton);
    }
}
