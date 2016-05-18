-dontobfuscate

-keep class com.google.android.gms.** { *; }
-keep class org.apache.commons.** { *; }
-keep class pl.edu.icm.jlargearrays.** { *; }

-dontwarn pl.edu.icm.jlargearrays.**
-dontwarn org.apache.commons.**
-dontwarn com.google.android.gms.**
