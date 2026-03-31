# Add project specific ProGuard rules here.

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class io.split.client.thin.internal.streaming.**$$serializer { *; }
-keepclassmembers class io.split.client.thin.internal.streaming.** {
    *** Companion;
}
-keepclasseswithmembers class io.split.client.thin.internal.streaming.** {
    kotlinx.serialization.KSerializer serializer(...);
}
