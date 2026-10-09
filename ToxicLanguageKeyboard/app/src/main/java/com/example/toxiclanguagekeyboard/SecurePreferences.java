package com.example.toxiclanguagekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One store for settings, local refinements and metrics. Never falls back to plaintext. */
public final class SecurePreferences {
    private static SharedPreferences instance;
    private SecurePreferences() {}

    public static synchronized SharedPreferences open(Context context) {
        if (instance != null) return instance;
        Context app = context.getApplicationContext();
        try {
            MasterKey key = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences secure = EncryptedSharedPreferences.create(app, "secure_prefs", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            SharedPreferences legacy = app.getSharedPreferences("ToxicKeyboardPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = secure.edit().remove("clipboard_history");
            for (Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
                String name = entry.getKey();
                if (name.equals("clipboard_history") || secure.contains(name)) continue;
                Object value = entry.getValue();
                if (value instanceof String) editor.putString(name, (String) value);
                else if (value instanceof Boolean) editor.putBoolean(name, (Boolean) value);
                else if (value instanceof Integer) editor.putInt(name, (Integer) value);
                else if (value instanceof Long) editor.putLong(name, (Long) value);
                else if (value instanceof Float) editor.putFloat(name, (Float) value);
                else if (value instanceof Set) {
                    Set<String> values = new HashSet<>();
                    for (Object item : (Set<?>) value) values.add((String) item);
                    editor.putStringSet(name, values);
                }
            }
            // Clear legacy data only AFTER the encrypted write succeeds; retry is idempotent.
            if (!editor.commit()) throw new IllegalStateException("Encrypted migration failed");
            if (!legacy.edit().clear().commit()) throw new IllegalStateException("Legacy cleanup failed");
            instance = secure;
            return secure;
        } catch (Exception failure) {
            throw new IllegalStateException("Secure storage unavailable; no plaintext fallback", failure);
        }
    }
}
