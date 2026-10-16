# ProGuard / R8 Obfuscation & Shrinking Rules for Airwave Radio

# 1. Essential Reflection Keep Rules
# MainActivity reflectively invokes PrivateExperience.install() at runtime.
-keep class lk.sugaapps.airwave.PrivateExperience {
    public static void install();
    *;
}

# 2. Native C++ JNI & Audio Engine Keep Rules
# Prevents stripping/renaming JNI entry points and callbacks used by Oboe & C++ engine.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class lk.sugaapps.airwave.engine.** { *; }
-keep class com.google.oboe.** { *; }

# 3. Data Models, Firestore, & Serialization
# Keeps data models and fields used for Firestore document parsing and JSON serialization.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.firebase.firestore.** *;
}
-keepclassmembers class lk.sugaapps.airwave.data.** {
    <fields>;
    <methods>;
}
-keep class lk.sugaapps.airwave.data.StationChatMessage { *; }
-keep class lk.sugaapps.airwave.data.NewsItem { *; }
-keep class lk.sugaapps.airwave.engine.Playlist$Item { *; }

# 4. Debugging & Stacktrace Preservation
# Preserves line numbers for decoded crash reports and stacktraces via mapping.txt
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 5. Jetpack Compose
-dontwarn androidx.compose.**
