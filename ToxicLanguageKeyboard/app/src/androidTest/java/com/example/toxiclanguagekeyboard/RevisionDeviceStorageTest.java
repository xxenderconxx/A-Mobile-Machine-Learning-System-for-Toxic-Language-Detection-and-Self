package com.example.toxiclanguagekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import static org.junit.Assert.*;

public class RevisionDeviceStorageTest {
    @Test public void encryptedStorePersistsAndLegacyClipboardIsGone() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences secure=SecurePreferences.open(context);
        String key="revision_storage_test", sample="synthetic-storage-verification-only";
        assertFalse(secure.contains("clipboard_history"));
        assertTrue(context.getSharedPreferences("ToxicKeyboardPrefs",Context.MODE_PRIVATE).getAll().isEmpty());
        assertTrue(secure.edit().putString(key,sample).commit());
        try {
            MasterKey master=new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences reopened=EncryptedSharedPreferences.create(context,"secure_prefs",master,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            assertEquals(sample,reopened.getString(key,""));
            File xml=new File(context.getApplicationInfo().dataDir,"shared_prefs/secure_prefs.xml");
            assertFalse(new String(Files.readAllBytes(xml.toPath()),StandardCharsets.UTF_8).contains(sample));
            int dictionary=secure.getStringSet("user_dictionary",new HashSet<>()).size()
                    +secure.getStringSet("user_dictionary_history",new HashSet<>()).size();
            Log.i("RevisionStorage","encrypted_persistence=passed plaintext_absent=passed legacy_empty=passed clipboard_absent=passed dictionary_entry_count="+dictionary);
        } finally { assertTrue(secure.edit().remove(key).commit()); }
    }
}
