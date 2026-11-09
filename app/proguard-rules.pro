# Pleos SDK: AIDL로 서비스와 통신하고 내부에서 리플렉션·직렬화를 쓴다. 이름이 바뀌면 연결이 끊긴다.
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**

# Pleos SDK가 쓰는 Gson은 R8 규칙을 함께 싣지 않는다. 아래는 Gson 공식 규칙이다.
-keepattributes Signature, EnclosingMethod, InnerClasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }
-dontwarn com.google.gson.**

# NaviHelper 데이터 클래스가 쓰는 kotlinx.serialization
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn kotlinx.serialization.**

# Android Automotive 차량 API(useLibrary("android.car"))는 기기에 있다.
-dontwarn android.car.**
