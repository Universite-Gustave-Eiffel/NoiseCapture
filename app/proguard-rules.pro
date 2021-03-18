-dontobfuscate
-keepattributes LineNumberTable,SourceFile

-keep class com.google.android.gms.** { *; }
-keep class org.apache.commons.** { *; }
-keep class pl.edu.icm.jlargearrays.** { *; }
-keep class com.github.mikephil.** { *; }
-keep class com.nhaarman.supertooltips.** { *; }
-keep class org.renjin.** { *; }

-dontwarn pl.edu.icm.jlargearrays.**
-dontwarn org.renjin.**
-dontwarn org.apache.commons.**
-dontwarn com.google.android.gms.**
-dontwarn com.github.mikephil.**
-dontwarn com.nhaarman.supertooltips.**
