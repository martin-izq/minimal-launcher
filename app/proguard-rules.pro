# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
