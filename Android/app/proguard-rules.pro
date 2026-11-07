# ============ JNI/native bridge — bilunal kehna mana hai ============
-keep class com.yausername.** { *; }
-keep class com.arthenica.** { *; }

# Jackson — engine updater JSON parsing ke liye
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Apache commons (updater compress/io)
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.**
-dontwarn org.slf4j.**
-dontwarn javax.**
-dontwarn java.beans.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.joda.time.**

# ============ kotlinx.serialization (download queue persistence) ============
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.vidgrab.app.**$$serializer { *; }
-keepclassmembers class com.vidgrab.app.** { *** Companion; }
-keepclasseswithmembers class com.vidgrab.app.** { kotlinx.serialization.KSerializer serializer(...); }

# ============ DataStore/Compose/Coil ============
-dontwarn coil.**
-keep class androidx.datastore.** { *; }

# Enum values/methods R8-safe
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
