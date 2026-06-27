# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information for debugging release crash stack traces.
-keepattributes SourceFile,LineNumberTable
# Hide the original source file name while keeping line numbers.
-renamesourcefileattribute SourceFile

# ── WorkManager ─────────────────────────────────────────────
# Workers are instantiated reflectively by the default WorkerFactory.
-keep class com.odom.applimit.worker.** { *; }

# ── Room ────────────────────────────────────────────────────
# Entities are referenced by generated code, but keep them explicitly as a
# safety net so field names survive (used for the manual migration backfill).
-keep class com.odom.applimit.data.** { *; }