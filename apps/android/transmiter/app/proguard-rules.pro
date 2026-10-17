# ProGuard / R8 Obfuscation & Shrinking Rules for Airwave Radio

# 1. Essential Reflection & Experience Keep Rules
-keep class lk.sugaapps.airwave.PrivateExperience { *; }
-keepclassmembers class lk.sugaapps.airwave.PrivateExperience { *; }
-dontwarn lk.sugaapps.airwave.PrivateExperience

-keep class lk.sugaapps.airwave.ui.ExperienceRegistry { *; }
-keepclassmembers class lk.sugaapps.airwave.ui.ExperienceRegistry { *; }

# 2. Native C++ JNI & Proprietary Audio Streaming Engine
# Preserves AudioStreamer, RealBroadcastEngine, C++ JNI methods and all callbacks.
-keep class lk.sugastreamapps.broadcastmyself.** { *; }
-keepclassmembers class lk.sugastreamapps.broadcastmyself.** { *; }
-dontwarn lk.sugastreamapps.broadcastmyself.**

-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclasseswithmembers class * {
    native <methods>;
}
-keepclassmembers class * {
    public void onConnectionStateChanged(int, java.lang.String);
    public void playFallbackPcm(short[]);
}

-keep class lk.sugaapps.airwave.engine.** { *; }
-keepclassmembers class lk.sugaapps.airwave.engine.** { *; }

-keep class com.google.oboe.** { *; }
-keepclassmembers class com.google.oboe.** { *; }

# 3. Foreground Service & Audio Recording
-keep class lk.sugaapps.airwave.service.** { *; }
-keepclassmembers class lk.sugaapps.airwave.service.** { *; }

# 4. Data Stores, Models, Firestore, & Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class lk.sugaapps.airwave.data.** { *; }
-keepclassmembers class lk.sugaapps.airwave.data.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.** *;
}
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# 5. UI Screens & Components
-keep class lk.sugaapps.airwave.ui.screens.** { *; }
-keepclassmembers class lk.sugaapps.airwave.ui.screens.** { *; }
-dontwarn lk.sugaapps.airwave.ui.screens.**

# 6. Debugging & Stacktrace Preservation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 7. Jetpack Compose & Coroutines
-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**
