# ProGuard rules for Firebase/Firestore
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.firestore.** { *; }

# Keep your model classes if you had any (e.g., if you used custom objects for Firestore)
# -keep class com.example.toxiclanguagekeyboard.models.** { *; }

# Prevent R8 from removing components used by TapTargetView
-keep class com.getkeepsafe.taptargetview.** { *; }

# Keep line numbers for better crash reporting (optional for research builds)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
