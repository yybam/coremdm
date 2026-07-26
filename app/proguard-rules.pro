# ── App components referenced by name in the manifest / XML ──────────────────
-keep class com.techeaz.mdm.MdmDeviceAdmin { *; }
-keep class com.techeaz.mdm.MdmApplication { *; }
-keep class com.techeaz.mdm.receiver.BootReceiver { *; }
-keep class com.techeaz.mdm.worker.PolicyEnforcementWorker { *; }

# ── WorkManager workers (require specific constructor) ────────────────────────
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# ── Security Crypto / Keystore ────────────────────────────────────────────────
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ── Navigation Compose ────────────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }
-keepnames class androidx.navigation.** { *; }

# ── Compose — compiler handles stability, keep serialisation intact ───────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Suppress noisy warnings from transitive dependencies ─────────────────────
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
# ErrorProne annotations referenced by security-crypto's tink dependency (compile-time only)
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
