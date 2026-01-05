# Aperture - Keep all public APIs
-keep class io.aperture.** { public *; }

# Keep database entities
-keep @androidx.room.Entity class * { *; }

# Keep Ktor serialization
-keep class io.ktor.** { *; }

# Kotlinx Serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
