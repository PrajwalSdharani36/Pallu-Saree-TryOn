# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.pallu.tryon.data.**$$serializer { *; }
-keepclassmembers class com.pallu.tryon.data.** { *** Companion; }
-keepclasseswithmembers class com.pallu.tryon.data.** { kotlinx.serialization.KSerializer serializer(...); }
# Retrofit
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.pallu.tryon.data.PalluApi { *; }
