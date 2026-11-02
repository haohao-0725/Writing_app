# Jetpack Ink 透過 JNI 呼叫原生程式庫；函式庫本身有附 consumer rules，這裡再保險保留一次。
-keep class androidx.ink.** { *; }
-keepclassmembers class androidx.ink.** { native <methods>; }

# 保留行號，當機時 stack trace 看得懂
-keepattributes SourceFile,LineNumberTable
