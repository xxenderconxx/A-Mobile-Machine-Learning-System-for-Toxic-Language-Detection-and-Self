package com.example.toxiclanguagekeyboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EmojiLibrary {
    private static final Map<String, String> EMOJI_NAMES = new HashMap<>();

    static {
        // Sample metadata for search demo
        EMOJI_NAMES.put("😀", "grinning face happy smile");
        EMOJI_NAMES.put("🤣", "rolling on the floor laughing funny");
        EMOJI_NAMES.put("❤️", "heart love red");
        EMOJI_NAMES.put("👍", "thumbs up like");
        EMOJI_NAMES.put("🔥", "fire hot lit");
        EMOJI_NAMES.put("🍕", "pizza food cheese");
        EMOJI_NAMES.put("🚗", "car travel vehicle");
        EMOJI_NAMES.put("🐶", "dog animal pet");
        // ... more can be added
    }

    public static Map<String, List<String>> getCategorizedEmojis(android.graphics.Paint paint) {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        Map<String, List<String>> raw = getRawCategorizedEmojis();

        for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
            List<String> sanitized = new ArrayList<>();
            for (String emoji : entry.getValue()) {
                if (paint == null || paint.hasGlyph(emoji)) {
                    sanitized.add(emoji);
                }
            }
            if (!sanitized.isEmpty()) {
                categories.put(entry.getKey(), sanitized);
            }
        }
        return categories;
    }

    private static Map<String, List<String>> getRawCategorizedEmojis() {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        
        // 1. Smileys & Emotions
        List<String> smileys = new ArrayList<>();
        for (int i = 0x1F600; i <= 0x1F64F; i++) smileys.add(new String(Character.toChars(i)));
        for (int i = 0x1F910; i <= 0x1F917; i++) smileys.add(new String(Character.toChars(i)));
        for (int i = 0x1F920; i <= 0x1F92F; i++) smileys.add(new String(Character.toChars(i)));
        for (int i = 0x1F970; i <= 0x1F976; i++) smileys.add(new String(Character.toChars(i)));
        categories.put("Smileys", smileys);
        
        // 2. Hand Gestures & Body
        List<String> gestures = new ArrayList<>();
        for (int i = 0x1F446; i <= 0x1F450; i++) gestures.add(new String(Character.toChars(i)));
        for (int i = 0x1F590; i <= 0x1F596; i++) gestures.add(new String(Character.toChars(i)));
        for (int i = 0x1F918; i <= 0x1F91F; i++) gestures.add(new String(Character.toChars(i)));
        for (int i = 0x270A; i <= 0x270D; i++) gestures.add(new String(Character.toChars(i)));
        categories.put("Gestures", gestures);
        
        // 3. People & Fantasy
        List<String> people = new ArrayList<>();
        for (int i = 0x1F466; i <= 0x1F487; i++) people.add(new String(Character.toChars(i)));
        for (int i = 0x1F9D0; i <= 0x1F9FF; i++) people.add(new String(Character.toChars(i)));
        for (int i = 0x1F3FB; i <= 0x1F3FF; i++) people.add(new String(Character.toChars(i))); // Skintones
        categories.put("People", people);
        
        // 4. Animals & Nature
        List<String> nature = new ArrayList<>();
        for (int i = 0x1F400; i <= 0x1F43F; i++) nature.add(new String(Character.toChars(i)));
        for (int i = 0x1F330; i <= 0x1F350; i++) nature.add(new String(Character.toChars(i)));
        for (int i = 0x1F980; i <= 0x1F9AE; i++) nature.add(new String(Character.toChars(i)));
        categories.put("Nature", nature);
        
        // 5. Food & Drink
        List<String> food = new ArrayList<>();
        for (int i = 0x1F32D; i <= 0x1F37F; i++) food.add(new String(Character.toChars(i)));
        for (int i = 0x1F950; i <= 0x1F96F; i++) food.add(new String(Character.toChars(i)));
        categories.put("Food", food);

        // 6. Activities & Sports
        List<String> activity = new ArrayList<>();
        for (int i = 0x1F380; i <= 0x1F3C4; i++) activity.add(new String(Character.toChars(i)));
        for (int i = 0x1F947; i <= 0x1F94B; i++) activity.add(new String(Character.toChars(i)));
        categories.put("Activity", activity);

        // 7. Travel & Places
        List<String> travel = new ArrayList<>();
        for (int i = 0x1F680; i <= 0x1F6C5; i++) travel.add(new String(Character.toChars(i)));
        for (int i = 0x1F300; i <= 0x1F321; i++) travel.add(new String(Character.toChars(i)));
        categories.put("Travel", travel);

        // 8. Objects & Tech
        List<String> objects = new ArrayList<>();
        for (int i = 0x1F4BB; i <= 0x1F4EC; i++) objects.add(new String(Character.toChars(i)));
        for (int i = 0x1F5A5; i <= 0x1F5D3; i++) objects.add(new String(Character.toChars(i)));
        categories.put("Objects", objects);

        // 9. Symbols & Hearts
        List<String> symbols = new ArrayList<>();
        for (int i = 0x2764; i <= 0x2767; i++) symbols.add(new String(Character.toChars(i))); // Hearts
        for (int i = 0x1F493; i <= 0x1F49F; i++) symbols.add(new String(Character.toChars(i))); // More hearts
        for (int i = 0x2600; i <= 0x26FF; i++) symbols.add(new String(Character.toChars(i))); // Misc symbols
        categories.put("Symbols", symbols);

        // 10. Flags
        List<String> flags = new ArrayList<>();
        for (int i = 0x1F1E6; i <= 0x1F1FF; i++) {
            // Flags are regional indicator pairs, but simplified here
            flags.add(new String(Character.toChars(i)));
        }
        categories.put("Flags", flags);
        
        return categories;
    }

    public static List<String> getAllEmojis(android.graphics.Paint paint) {
        List<String> all = new ArrayList<>();
        Map<String, List<String>> categorized = getCategorizedEmojis(paint);
        for (List<String> list : categorized.values()) {
            all.addAll(list);
        }
        return all;
    }

    public static String getEmojiName(String emoji) {
        return EMOJI_NAMES.getOrDefault(emoji, "");
    }
}
