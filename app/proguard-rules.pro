# Pro Racing OBD ProGuard Rules

# Keep all classes in the main package
-keep class shop.surina.proracingobd.** { *; }

# Keep Bluetooth classes
-keep class android.bluetooth.** { *; }

# Keep RecyclerView classes
-keep class androidx.recyclerview.widget.** { *; }

# Keep Material Design classes
-keep class com.google.android.material.** { *; }

# Keep OBD-related classes
-keepclassmembers class * {
    public <init>(java.io.InputStream, java.io.OutputStream);
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
