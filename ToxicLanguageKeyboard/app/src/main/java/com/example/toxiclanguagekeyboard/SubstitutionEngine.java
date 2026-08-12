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
    private java.util.Set<String> dynamicSafeWords = new java.util.HashSet<>();
    private java.util.Set<String> dynamicSafeSkeletons = new java.util.HashSet<>();
    private static final java.util.Set<String> COMMON_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "a", "about", "above", "across", "after", "again", "against", "all", "almost", "alone", "along", "already", "also", "although", "always", "am", "among", "an", "and", "another", "any", "anybody", "anyone", "anything", "anywhere", "are", "area", "around", "as", "ask", "at", "away",
        "back", "be", "became", "because", "become", "becomes", "been", "before", "began", "begin", "behind", "being", "below", "beside", "best", "better", "between", "big", "both", "boy", "but", "by",
        "call", "came", "can", "cannot", "can't", "car", "certain", "change", "child", "children", "city", "close", "come", "could", "couldn't", "country", "course", "cut",
        "day", "did", "didn't", "do", "does", "doesn't", "dog", "done", "don't", "door", "down", "during",
        "each", "early", "earth", "eat", "either", "else", "end", "enough", "even", "ever", "every", "everybody", "everyone", "everything", "everywhere", "eye",
        "face", "fact", "far", "father", "feel", "few", "find", "fire", "first", "five", "for", "four", "from", "front", "full",
        "gave", "get", "girl", "give", "given", "go", "good", "got", "great", "group", "grow",
        "had", "hadn't", "half", "hand", "has", "hasn't", "have", "haven't", "he", "head", "hear", "heard", "help", "her", "here", "hers", "herself", "he's", "high", "him", "himself", "his", "home", "house", "how", "however",
        "i", "if", "important", "in", "interest", "into", "is", "isn't", "it", "its", "it's", "itself",
        "just", "keep", "kind", "knew", "know", "known",
        "land", "large", "last", "later", "learn", "leave", "left", "less", "let", "letter", "life", "light", "like", "line", "little", "live", "long", "look", "love", "low",
        "made", "make", "man", "many", "may", "maybe", "me", "mean", "men", "might", "mile", "miss", "more", "most", "mother", "mountain", "move", "much", "must", "my", "myself",
        "name", "near", "need", "never", "new", "next", "night", "no", "not", "nothing", "now", "number",
        "of", "off", "often", "oh", "old", "on", "once", "one", "only", "open", "or", "order", "other", "others", "our", "out", "over", "own",
        "page", "paper", "part", "past", "people", "perhaps", "person", "place", "play", "point", "present", "public", "put",
        "question", "quite",
        "read", "real", "really", "right", "room", "run",
        "said", "same", "saw", "say", "school", "sea", "second", "see", "seem", "seemed", "sentence", "set", "several", "she", "she's", "short", "should", "show", "side", "since", "small", "so", "some", "somebody", "someone", "something", "somewhere", "soon", "sound", "still", "story", "study", "such", "sure",
        "take", "tell", "than", "thank", "thanks", "that", "that's", "the", "their", "them", "themselves", "then", "there", "therefore", "these", "they", "thing", "think", "this", "those", "though", "thought", "three", "through", "time", "to", "together", "too", "took", "toward", "turn", "two",
        "under", "until", "up", "upon", "us", "use", "used",
        "very",
        "walk", "want", "was", "wasn't", "water", "way", "we", "well", "went", "were", "weren't", "what", "what's", "when", "where", "whether", "which", "while", "white", "who", "whole", "whom", "whose", "why", "will", "with", "within", "without", "word", "work", "world", "would", "wouldn't", "write",
        "year", "yet", "you", "young", "your", "yours", "yourself", "yourselves", "you're",
        "hi", "hello", "hey", "heyy", "heya", "yo", "sup", "please", "sorry", "yes", "ok", "okay", "yeah", "yep", "yup", "thanks", "welcome", "howdy", "goodbye", "bye", "test", "testing", "message", "type", "keyboard", "input",
            "today", "tomorrow", "yesterday", "morning", "afternoon", "evening", "night", "week", "month", "year", "time", "hour", "minute", "second",
            "happy", "sad", "angry", "excited", "bored", "tired", "hungry", "thirsty", "good", "bad", "better", "best", "worse", "worst",
            "black", "white", "red", "blue", "green", "yellow", "orange", "purple", "pink", "brown", "gray", "grey",
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred", "thousand", "million",
            "can", "could", "will", "would", "shall", "should", "may", "might", "must", "ought", "need", "dare",
            "am", "is", "are", "was", "were", "be", "being", "been", "have", "has", "had", "do", "does", "did",
            "doing", "done", "having", "going", "gone", "coming", "come", "taking", "taken", "making", "made",
            "using", "used", "finding", "found", "giving", "given", "telling", "told", "working", "worked",
            "calling", "called", "trying", "tried", "asking", "asked", "needing", "needed", "feeling", "felt",
            "looking", "looked", "becoming", "became", "leaving", "left", "putting", "put", "meaning", "meant",
            "keep", "kept", "let", "begin", "begun", "began", "show", "shown", "hear", "heard", "play", "played",
            "run", "ran", "move", "moved", "live", "lived", "believe", "believed", "bring", "brought", "happen", "happened",
            "write", "wrote", "written", "sit", "sat", "stand", "stood", "lose", "lost", "pay", "paid", "meet", "met",
            "include", "included", "continue", "continued", "set", "learn", "learned", "change", "changed", "lead", "led",
            "understand", "understood", "watch", "watched", "follow", "followed", "stop", "stopped", "create", "created",
            "speak", "spoke", "spoken", "read", "allow", "allowed", "add", "added", "spend", "spent", "grow", "grew", "grown",
            "open", "opened", "walk", "walked", "win", "won", "offer", "offered", "remember", "remembered", "love", "loved",
            "consider", "considered", "appear", "appeared", "buy", "bought", "wait", "waited", "serve", "served", "die", "died",
            "send", "sent", "expect", "expected", "build", "built", "stay", "stayed", "fall", "fell", "fallen", "cut",
            "reach", "reached", "kill", "killed", "remain", "remained"
    ));
    private static final java.util.Set<String> COMMON_SKELETONS;
    static {
        COMMON_SKELETONS = new java.util.HashSet<>();
        for (String w : COMMON_WORDS) {
            COMMON_SKELETONS.add(w.replaceAll("(.)\\1+", "$1"));
        }
    }
    
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
                android.util.Log.e("SubstitutionEngine", "Error loading substitutions", e);
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

    public void setDynamicSafeWords(java.util.Set<String> words) {
        this.dynamicSafeWords = new java.util.HashSet<>(words);
        this.dynamicSafeSkeletons.clear();
        for (String w : words) {
            dynamicSafeSkeletons.add(w.toLowerCase().replaceAll("(.)\\1+", "$1"));
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
        if (substitutions.containsKey(lower) || dynamicSafeWords.contains(lower) || COMMON_WORDS.contains(lower)) return true;

        String skeleton = lower.replaceAll("(.)\\1+", "$1");
        return knownSkeletons.contains(skeleton) || dynamicSafeSkeletons.contains(skeleton) || COMMON_SKELETONS.contains(skeleton);
    }
}
