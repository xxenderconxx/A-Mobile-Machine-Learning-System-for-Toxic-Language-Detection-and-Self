package com.example.toxiclanguagekeyboard;

import android.content.Context;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class VocabularyEngine {

    private final Map<String, String> vocabularyMap = new HashMap<>();
    private volatile boolean isLoaded = false;

    public VocabularyEngine(Context context) {
        // Initialization moved to async load to prevent UI block
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    /**
     * Reads vocabulary.json from app assets and populates the in-memory lookup table.
     */
    public void loadDictionary(Context context) {
        String fileName = "vocabulary.json";
        try (InputStream is = context.getAssets().open(fileName)) {
            byte[] buffer = new byte[is.available()];
            int size = is.read(buffer);
            if (size <= 0) return;

            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(json);
            Iterator<String> keys = jsonObject.keys();

            while (keys.hasNext()) {
                String key = keys.next().toLowerCase().trim();
                String value = jsonObject.getString(key).trim();
                vocabularyMap.put(key, value);
            }
            isLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a given word exists in the vocabulary dataset.
     * Prevents common safe words (e.g., "good", "god") from being flagged as "unknown".
     */
    public boolean isKnownWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }
        String cleanWord = word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase().trim();
        return vocabularyMap.containsKey(cleanWord) || vocabularyMap.containsValue(cleanWord);
    }
}
