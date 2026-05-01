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
                String key = keys.next();
                substitutions.put(key.toLowerCase(), jsonObject.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getReframedSentence(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String lowerInput = input.toLowerCase();
        String result = input;

        // Sort keys by length descending to handle multi-word substitutions correctly
        // For simplicity in this version, we iterate and replace.
        for (Map.Entry<String, String> entry : substitutions.entrySet()) {
            // Use regex for word boundaries to avoid partial matches (e.g., "idiot" in "idiotic")
            String pattern = "(?i)\\b" + entry.getKey() + "\\b";
            result = result.replaceAll(pattern, entry.getValue());
        }

        return result;
    }

    public boolean containsToxicLanguage(String input) {
        if (input == null || input.isEmpty()) return false;
        String lowerInput = input.toLowerCase();
        for (String key : substitutions.keySet()) {
            if (lowerInput.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
