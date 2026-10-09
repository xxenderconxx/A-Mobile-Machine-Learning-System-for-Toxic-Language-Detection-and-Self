package com.example.toxiclanguagekeyboard;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import java.util.function.Consumer;

/** Authentication is initiated only after an explicit upload confirmation. */
public final class ContributionClient {
    private static FirebaseFirestore database;
    private ContributionClient() {}

    public static synchronized FirebaseFirestore database() {
        if (database == null) {
            database = FirebaseFirestore.getInstance();
            database.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(false).build());
        }
        return database;
    }

    public static void authenticated(Consumer<String> success, Consumer<Exception> failure) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            success.accept(auth.getCurrentUser().getUid());
            return;
        }
        auth.signInAnonymously().addOnSuccessListener(result -> {
            if (result.getUser() == null) failure.accept(new IllegalStateException("No authenticated user"));
            else success.accept(result.getUser().getUid());
        }).addOnFailureListener(failure::accept);
    }
}
