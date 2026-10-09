# Dotline R8 rules. Manifest components (activities, services, receivers) are kept
# automatically by AGP; these rules cover what R8 cannot see.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Services / receivers / widget providers are instantiated by the framework by name.
-keep class com.dotline.launcher.service.** { *; }
-keep class com.dotline.launcher.widgets.** { *; }

# Enums are looked up by name when settings and layouts are restored from disk / JSON.
-keepclassmembers enum com.dotline.launcher.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# DataStore (protobuf-lite internals are reflective).
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Coroutines debug agent / optional dependencies.
-dontwarn kotlinx.coroutines.debug.**
-dontwarn java.lang.instrument.**
-dontwarn org.slf4j.**
