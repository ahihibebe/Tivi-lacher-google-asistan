package vn.launcher.tv.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import vn.launcher.tv.BuildConfig
import java.util.UUID
import kotlin.coroutines.resume

private const val TAG = "AppCheckInit"

// App Check contract keys. These strings are defined by the Firebase SDK and
// the runtime harness, so do not rename them.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private const val CUSTOM_PREFS = "app_check_cfg"
private const val KEY_CUSTOM_DEBUG_TOKEN = "custom_debug_token"

enum class AppCheckMode {
    DEBUG_INTENT,
    DEBUG_CUSTOM,
    DEBUG_AUTO_UUID,
    RECAPTCHA,
    UNCONFIGURED
}

sealed interface AppCheckCheckResult {
    data object Ready : AppCheckCheckResult
    data object TimedOut : AppCheckCheckResult
    data class Failed(val message: String?) : AppCheckCheckResult
}

private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Volatile
private var currentAppCheckMode: AppCheckMode = AppCheckMode.UNCONFIGURED

@Volatile
private var lastReadyAtMs: Long = 0L

private const val READY_CACHE_TTL_MS = 15 * 60 * 1000L

fun currentAppCheckMode(): AppCheckMode = currentAppCheckMode

fun configureAppCheck(context: Context, intent: Intent?) {
    lastReadyAtMs = 0L
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            val initialized = FirebaseApp.initializeApp(context)
            if (initialized == null) {
                currentAppCheckMode = AppCheckMode.UNCONFIGURED
                Log.w(TAG, "FirebaseApp could not be initialized (google-services.json missing in build).")
                return
            }
        }

        val intentDebugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val customDebugToken = getCustomDebugToken(context)
        val siteKey = resolveRecaptchaSiteKey(context)

        if (BuildConfig.DEBUG) {
            val debugToken = intentDebugToken ?: customDebugToken
            if (!debugToken.isNullOrEmpty()) {
                writeDebugSecretSync(context, debugToken)
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                currentAppCheckMode = if (intentDebugToken != null) {
                    AppCheckMode.DEBUG_INTENT
                } else {
                    AppCheckMode.DEBUG_CUSTOM
                }
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else if (isLikelyEmulator() || siteKey.isNullOrBlank()) {
                Log.w(TAG, "No debug token found in Intent extra; using DebugAppCheckProviderFactory.")
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                currentAppCheckMode = AppCheckMode.DEBUG_AUTO_UUID
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            } else {
                // Physical device running an assembleDebug APK without a harness Intent extra:
                // prefer reCAPTCHA Enterprise so sideloaded TVs with GMS attest automatically
                // instead of sending an unregistered random debug UUID.
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                )
                currentAppCheckMode = AppCheckMode.RECAPTCHA
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed on physical device.")
            }
        } else {
            if (!customDebugToken.isNullOrEmpty()) {
                writeDebugSecretSync(context, customDebugToken)
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                currentAppCheckMode = AppCheckMode.DEBUG_CUSTOM
                Log.d(TAG, "Custom DebugAppCheckProviderFactory installed in release build.")
            } else if (!siteKey.isNullOrBlank()) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                )
                currentAppCheckMode = AppCheckMode.RECAPTCHA
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
            } else {
                currentAppCheckMode = AppCheckMode.UNCONFIGURED
                Log.e(TAG, "recaptcha_site_key missing or blank in release build.")
            }
        }
    } catch (e: Exception) {
        currentAppCheckMode = AppCheckMode.UNCONFIGURED
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

/**
 * Non-blocking warm-up so reCAPTCHA / Debug token attestation and the Gemini
 * client initialize in the background when the launcher starts instead of
 * adding cold-start latency to the user's first AI query.
 */
fun prewarmAppCheck(context: Context) {
    val appCtx = context.applicationContext
    warmUpScope.launch {
        GeminiAiService.warmup(appCtx)
        if (currentAppCheckMode == AppCheckMode.UNCONFIGURED) return@launch
        awaitAppCheckReady(appCtx, timeoutMs = 12_000L)
    }
}

fun getCustomDebugToken(context: Context): String? {
    val prefs = context.applicationContext.getSharedPreferences(CUSTOM_PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_CUSTOM_DEBUG_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }
}

fun getActiveDebugSecret(context: Context): String? {
    return try {
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context) ?: return null
        val prefs = context.applicationContext.getSharedPreferences(
            "$DEBUG_STORE_PREFIX${app.persistenceKey}",
            Context.MODE_PRIVATE
        )
        prefs.getString(DEBUG_SECRET_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

fun generateDebugUuidCandidate(): String = UUID.randomUUID().toString()

fun saveCustomDebugTokenAndReconfigure(context: Context, rawToken: String?) {
    val cleaned = rawToken?.trim()?.takeIf { it.isNotEmpty() }
    val appCtx = context.applicationContext
    val cfgPrefs = appCtx.getSharedPreferences(CUSTOM_PREFS, Context.MODE_PRIVATE)
    // CRITICAL: use commit() synchronously before reinstalling App Check provider factory.
    cfgPrefs.edit().apply {
        if (cleaned == null) remove(KEY_CUSTOM_DEBUG_TOKEN)
        else putString(KEY_CUSTOM_DEBUG_TOKEN, cleaned)
    }.commit()
    configureAppCheck(appCtx, null)
    prewarmAppCheck(appCtx)
}

suspend fun awaitAppCheckReady(
    context: Context,
    timeoutMs: Long = 8_000L
): AppCheckCheckResult = withContext(Dispatchers.IO) {
    if (FirebaseApp.getApps(context).isEmpty()) {
        return@withContext AppCheckCheckResult.Failed("FirebaseApp is not initialized.")
    }
    if (currentAppCheckMode == AppCheckMode.UNCONFIGURED) {
        return@withContext AppCheckCheckResult.Failed("App Check provider is unconfigured.")
    }
    val now = System.currentTimeMillis()
    if (lastReadyAtMs > 0L && now - lastReadyAtMs < READY_CACHE_TTL_MS) {
        return@withContext AppCheckCheckResult.Ready
    }
    val res = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<AppCheckCheckResult> { cont ->
            try {
                FirebaseAppCheck.getInstance()
                    .getAppCheckToken(false)
                    .addOnSuccessListener {
                        lastReadyAtMs = System.currentTimeMillis()
                        if (cont.isActive) cont.resume(AppCheckCheckResult.Ready)
                    }
                    .addOnFailureListener { err ->
                        lastReadyAtMs = 0L
                        if (cont.isActive) {
                            cont.resume(AppCheckCheckResult.Failed(err.localizedMessage ?: err.message))
                        }
                    }
            } catch (e: Exception) {
                lastReadyAtMs = 0L
                if (cont.isActive) {
                    cont.resume(AppCheckCheckResult.Failed(e.localizedMessage ?: e.message))
                }
            }
        }
    }
    res ?: AppCheckCheckResult.TimedOut
}

suspend fun diagnoseAppCheckTokenError(context: Context): String? {
    lastReadyAtMs = 0L
    return when (val r = awaitAppCheckReady(context, timeoutMs = 8_000L)) {
        AppCheckCheckResult.Ready -> null
        AppCheckCheckResult.TimedOut -> "App Check token request timed out."
        is AppCheckCheckResult.Failed -> r.message
    }
}

private fun writeDebugSecretSync(context: Context, debugToken: String) {
    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val prefs = context.applicationContext.getSharedPreferences(
        "$DEBUG_STORE_PREFIX$persistenceKey",
        Context.MODE_PRIVATE
    )
    // CRITICAL: MUST use commit() instead of apply() so the secret is written
    // synchronously before DebugAppCheckProviderFactory reads it.
    prefs.edit()
        .putString(DEBUG_SECRET_KEY, debugToken)
        .commit()
}

private fun resolveRecaptchaSiteKey(context: Context): String? {
    val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return null
    }
    return context.getString(resId).trim().takeIf { it.isNotBlank() }
}

private fun isLikelyEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.lowercase()
    val model = Build.MODEL.lowercase()
    val brand = Build.BRAND.lowercase()
    val device = Build.DEVICE.lowercase()
    val product = Build.PRODUCT.lowercase()
    val hardware = Build.HARDWARE.lowercase()
    val manufacturer = Build.MANUFACTURER.lowercase()
    return fingerprint.startsWith("generic") ||
        fingerprint.startsWith("unknown") ||
        fingerprint.contains("emulator") ||
        model.contains("google_sdk") ||
        model.contains("emulator") ||
        model.contains("android sdk built for") ||
        manufacturer.contains("genymotion") ||
        (brand.startsWith("generic") && device.startsWith("generic")) ||
        product.contains("sdk_gphone") ||
        product.contains("sdk_google") ||
        product.contains("emulator") ||
        product.contains("simulator") ||
        hardware.contains("goldfish") ||
        hardware.contains("ranchu")
}
