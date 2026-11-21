package vn.launcher.tv.data

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlin.coroutines.coroutineContext

/**
 * Low-latency Google Gemini client via Firebase AI Logic:
 * - Uses gemini-3.5-flash-lite (ThinkingLevel.MINIMAL) as primary for sub-second TTFT on TV,
 *   with automatic fallback to gemini-3.8-flash (ThinkingLevel.LOW) on transient stalls.
 * - Pre-warms model initialization, caches recent answers briefly, compiles regexes once,
 *   and throttles partial UI updates so low-power TV CPUs stay responsive.
 */
object GeminiAiService {

    private const val TAG = "GeminiAiService"
    private const val FAST_MODEL = "gemini-3.5-flash-lite"
    private const val STANDARD_MODEL = "gemini-3.8-flash"

    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private const val MAX_CACHE_SIZE = 24

    private const val VI_SYSTEM_PROMPT =
        "Bạn là trợ lý Google AI siêu nhanh trên Android TV. " +
            "Trả lời thẳng vào trọng tâm, chính xác, tự nhiên để đọc thành tiếng trong 2–4 câu ngắn " +
            "(khoảng 40–90 từ, không rào đón). Văn bản thuần, không dùng ** hay ###."

    private const val EN_SYSTEM_PROMPT =
        "You are a fast Google AI voice assistant on Android TV. " +
            "Answer directly, accurately, and concisely in 2-4 short spoken-friendly sentences " +
            "(~40-90 words, no filler). Plain text only without markdown asterisks or headers."

    private val REGEX_CODE_FENCE = Regex("```[a-zA-Z]*\\n?")
    private val REGEX_BOLD = Regex("\\*\\*(.*?)\\*\\*")
    private val REGEX_UNDERLINE = Regex("__(.*?)__")
    private val REGEX_HEADING = Regex("(?m)^#{1,6}\\s+")
    private val REGEX_BULLET = Regex("(?m)^\\*\\s+")
    private val REGEX_MULTI_NL = Regex("\\n{3,}")
    private val REGEX_SPEECH_BULLET = Regex("(?m)^[-•]\\s+")
    private val REGEX_MULTI_WS = Regex("\\s+")

    private class FirstTokenTimeoutException : CancellationException("First token timed out")
    private class PersonalApiKeyException(val code: Int, message: String) : Exception(message)
    private class ModelUnavailableException(val code: Int, message: String) : Exception(message)

    private data class CachedAnswer(val response: AiResponse, val timestampMs: Long)

    private val cache: MutableMap<String, CachedAnswer> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedAnswer>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedAnswer>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )

    private fun buildModel(modelName: String, isEnglish: Boolean, thinking: ThinkingLevel): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                maxOutputTokens = 320
                thinkingConfig = thinkingConfig {
                    thinkingLevel = thinking
                }
            },
            systemInstruction = content {
                text(if (isEnglish) EN_SYSTEM_PROMPT else VI_SYSTEM_PROMPT)
            }
        )
    }

    private val viFastModel by lazy { buildModel(FAST_MODEL, isEnglish = false, ThinkingLevel.MINIMAL) }
    private val enFastModel by lazy { buildModel(FAST_MODEL, isEnglish = true, ThinkingLevel.MINIMAL) }
    private val viStandardModel by lazy { buildModel(STANDARD_MODEL, isEnglish = false, ThinkingLevel.LOW) }
    private val enStandardModel by lazy { buildModel(STANDARD_MODEL, isEnglish = true, ThinkingLevel.LOW) }

    data class AiResponse(
        val answerText: String = "",
        val spokenText: String = "",
        val errorMessage: String? = null,
        val isAppCheckError: Boolean = false,
        val isApiKeyError: Boolean = false
    )

    fun clearCache() {
        cache.clear()
    }

    /** Pre-instantiates the default model on IO thread after configureAppCheck(). */
    fun warmup(context: Context) {
        try {
            if (getPersonalGeminiApiKey(context) != null) return
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                viFastModel
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun askStreaming(
        context: Context,
        query: String,
        isEnglish: Boolean,
        forceRefresh: Boolean = false,
        onStatus: (String) -> Unit = {},
        onPartialText: (String) -> Unit
    ): AiResponse = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext AiResponse(
                errorMessage = if (isEnglish) "Empty question." else "Câu hỏi trống."
            )
        }

        val personalApiKey = getPersonalGeminiApiKey(context)
        val cachePrefix = if (personalApiKey != null) "key:${personalApiKey.hashCode()}" else "fb"
        val cacheKey = "$cachePrefix|${if (isEnglish) "en" else "vi"}|${cleanQuery.lowercase()}"
        if (!forceRefresh) {
            cache[cacheKey]?.let { entry ->
                if (System.currentTimeMillis() - entry.timestampMs < CACHE_TTL_MS) {
                    onPartialText(entry.response.answerText)
                    return@withContext entry.response
                }
            }
        }

        if (personalApiKey != null) {
            return@withContext askWithPersonalApiKeyStreaming(
                apiKey = personalApiKey,
                cleanQuery = cleanQuery,
                isEnglish = isEnglish,
                cacheKey = cacheKey,
                onStatus = onStatus,
                onPartialText = onPartialText
            )
        }

        try {
            if (FirebaseApp.getApps(context).isEmpty() && FirebaseApp.initializeApp(context) == null) {
                return@withContext AiResponse(
                    errorMessage = if (isEnglish) {
                        "Firebase is not configured in this APK build (missing app/google-services.json). Build from AI Studio or add GOOGLE_SERVICES_JSON to GitHub Secrets."
                    } else {
                        "Bản APK này chưa có tệp cấu hình app/google-services.json. Hãy build từ AI Studio hoặc thêm secret GOOGLE_SERVICES_JSON trên GitHub."
                    }
                )
            }

            // Phase 1: Bounded App Check check (0ms when recently pre-warmed; caps hung reCAPTCHA at 9s/6s)
            val appCheckTimeoutMs = if (currentAppCheckMode() == AppCheckMode.RECAPTCHA) 9_000L else 6_000L
            onStatus(if (isEnglish) "Verifying App Check…" else "Đang xác thực nhanh…")
            when (val appCheckResult = awaitAppCheckReady(context, timeoutMs = appCheckTimeoutMs)) {
                AppCheckCheckResult.Ready -> Unit
                AppCheckCheckResult.TimedOut -> {
                    val timeoutDetail = if (isEnglish) {
                        "App Check (${modeLabel(isEnglish)}) timed out after ${appCheckTimeoutMs / 1000}s on this TV. Google Play Services / reCAPTCHA may be slow or blocked on TV boxes."
                    } else {
                        "App Check (${modeLabel(isEnglish)}) phản hồi quá lâu (quá ${appCheckTimeoutMs / 1000} giây). Google Play Services hoặc reCAPTCHA có thể bị chậm/chặn trên TV Box này."
                    }
                    return@withContext buildAppCheckErrorResponse(context, timeoutDetail, isEnglish)
                }
                is AppCheckCheckResult.Failed -> {
                    val failMsg = appCheckResult.message ?: if (isEnglish) {
                        "App Check attestation failed."
                    } else {
                        "Xác thực App Check thất bại."
                    }
                    return@withContext buildAppCheckErrorResponse(context, failMsg, isEnglish)
                }
            }

            // Phase 2: Ultra-fast primary stream with gemini-3.5-flash-lite (ThinkingLevel.MINIMAL)
            onStatus(if (isEnglish) "Asking Google AI…" else "Google AI đang trả lời…")
            val fastModel = if (isEnglish) enFastModel else viFastModel
            val fastAccumulated = StringBuilder()

            val fastFailure: Exception? = try {
                streamWithFirstTokenTimeout(
                    model = fastModel,
                    query = cleanQuery,
                    firstTokenTimeoutMs = 9_000L,
                    totalTimeoutMs = 24_000L,
                    accumulated = fastAccumulated,
                    onPartialText = onPartialText
                )
                null
            } catch (e: FirstTokenTimeoutException) {
                Log.w(TAG, "$FAST_MODEL produced no token within 9s; trying $STANDARD_MODEL.")
                e
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "$FAST_MODEL timed out; checking partial or falling back.")
                if (fastAccumulated.isNotBlank()) null else e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$FAST_MODEL failed: ${e.message}", e)
                if (isAppCheckExceptionMessage(e.localizedMessage)) throw e
                if (fastAccumulated.isNotBlank()) null else e
            }

            if (fastFailure == null && fastAccumulated.isNotBlank()) {
                return@withContext finalizeSuccess(fastAccumulated.toString(), isEnglish).also { res ->
                    if (res.errorMessage == null) cache[cacheKey] = CachedAnswer(res, System.currentTimeMillis())
                }
            }

            // Phase 3: Fallback to gemini-3.8-flash (ThinkingLevel.LOW)
            onStatus(
                if (isEnglish) "Switching AI route…"
                else "Đang thử tuyến AI dự phòng…"
            )
            val fallbackModel = if (isEnglish) enStandardModel else viStandardModel
            val fallbackAccumulated = StringBuilder()
            try {
                streamWithFirstTokenTimeout(
                    model = fallbackModel,
                    query = cleanQuery,
                    firstTokenTimeoutMs = 14_000L,
                    totalTimeoutMs = 24_000L,
                    accumulated = fallbackAccumulated,
                    onPartialText = onPartialText
                )
            } catch (_: FirstTokenTimeoutException) {
                return@withContext timeoutErrorResponse(isEnglish)
            } catch (e: TimeoutCancellationException) {
                if (fallbackAccumulated.isBlank()) return@withContext timeoutErrorResponse(isEnglish)
            }

            if (fallbackAccumulated.isNotBlank()) {
                return@withContext finalizeSuccess(fallbackAccumulated.toString(), isEnglish).also { res ->
                    if (res.errorMessage == null) cache[cacheKey] = CachedAnswer(res, System.currentTimeMillis())
                }
            }

            if (fastFailure != null && fastFailure !is FirstTokenTimeoutException && fastFailure !is TimeoutCancellationException) {
                throw fastFailure
            }

            AiResponse(
                errorMessage = if (isEnglish) {
                    "Google AI returned an empty response."
                } else {
                    "Google AI chưa có câu trả lời cho nội dung này."
                }
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI generation timed out", e)
            timeoutErrorResponse(isEnglish)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            Log.e(TAG, "FirebaseApp missing or uninitialized", e)
            val missingConfig = e.message?.contains("FirebaseApp", ignoreCase = true) == true
            AiResponse(
                errorMessage = if (missingConfig) {
                    if (isEnglish) {
                        "Firebase is not configured in this APK build (missing app/google-services.json). Build from AI Studio or add GOOGLE_SERVICES_JSON to GitHub Secrets."
                    } else {
                        "Bản APK này chưa có tệp cấu hình app/google-services.json. Hãy build từ AI Studio hoặc thêm secret GOOGLE_SERVICES_JSON trên GitHub."
                    }
                } else {
                    e.localizedMessage ?: "Firebase AI error."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            val rawMsg = e.localizedMessage?.takeIf { it.isNotBlank() }
            if (isAppCheckExceptionMessage(rawMsg)) {
                buildAppCheckErrorResponse(context, rawMsg.orEmpty(), isEnglish)
            } else {
                AiResponse(
                    errorMessage = rawMsg ?: if (isEnglish) {
                        "Could not connect to Google AI."
                    } else {
                        "Không nhận được phản hồi từ Google AI."
                    }
                )
            }
        }
    }

    private suspend fun streamWithFirstTokenTimeout(
        model: GenerativeModel,
        query: String,
        firstTokenTimeoutMs: Long,
        totalTimeoutMs: Long,
        accumulated: StringBuilder,
        onPartialText: (String) -> Unit
    ) {
        withTimeout(totalTimeoutMs) {
            coroutineScope {
                var gotFirstText = false
                var lastEmitMs = 0L
                val watchdog = launch {
                    delay(firstTokenTimeoutMs)
                    if (!gotFirstText) {
                        this@coroutineScope.cancel(FirstTokenTimeoutException())
                    }
                }
                try {
                    model.generateContentStream(query).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            val first = !gotFirstText
                            if (first) {
                                gotFirstText = true
                                watchdog.cancel()
                            }
                            accumulated.append(piece)
                            val now = System.currentTimeMillis()
                            if (first || now - lastEmitMs >= 45L) {
                                lastEmitMs = now
                                onPartialText(cleanForTvDisplay(accumulated.toString()))
                            }
                        }
                    }
                } finally {
                    watchdog.cancel()
                }
            }
        }
    }

    private fun timeoutErrorResponse(isEnglish: Boolean): AiResponse = AiResponse(
        errorMessage = if (isEnglish) {
            "Google AI timed out waiting for a response. Check TV network/Wi-Fi and try again."
        } else {
            "Hết thời gian chờ phản hồi từ Google AI. Hãy kiểm tra lại Wi-Fi/mạng trên TV rồi bấm Thử lại."
        }
    )

    private fun finalizeSuccess(raw: String, isEnglish: Boolean): AiResponse {
        val finalDisplay = cleanForTvDisplay(raw)
        return if (finalDisplay.isBlank()) {
            AiResponse(
                errorMessage = if (isEnglish) {
                    "Google AI returned an empty response."
                } else {
                    "Google AI chưa có câu trả lời cho nội dung này."
                }
            )
        } else {
            AiResponse(
                answerText = finalDisplay,
                spokenText = cleanForSpeech(finalDisplay)
            )
        }
    }

    private fun isAppCheckExceptionMessage(rawMsg: String?): Boolean {
        if (rawMsg.isNullOrBlank()) return false
        return rawMsg.contains("App Check", ignoreCase = true) ||
            rawMsg.contains("AppCheck", ignoreCase = true) ||
            rawMsg.contains("attestation", ignoreCase = true) ||
            rawMsg.contains("token is invalid", ignoreCase = true) ||
            rawMsg.contains("403")
    }

    private fun modeLabel(isEnglish: Boolean): String = when (currentAppCheckMode()) {
        AppCheckMode.RECAPTCHA -> "reCAPTCHA Enterprise"
        AppCheckMode.DEBUG_INTENT -> "Debug Token (Intent)"
        AppCheckMode.DEBUG_CUSTOM -> "Debug Token (TV)"
        AppCheckMode.DEBUG_AUTO_UUID -> "Debug Token (Auto UUID)"
        AppCheckMode.UNCONFIGURED -> if (isEnglish) "Unconfigured" else "Chưa cấu hình"
    }

    private suspend fun buildAppCheckErrorResponse(
        context: Context,
        rawMsg: String,
        isEnglish: Boolean
    ): AiResponse {
        val sdkDetail = if (rawMsg.contains("timed out", ignoreCase = true)) {
            null
        } else {
            diagnoseAppCheckTokenError(context)
        }
        val mode = currentAppCheckMode()
        val label = modeLabel(isEnglish)
        val activeSecret = getActiveDebugSecret(context)
        val builtMsg = buildString {
            append(rawMsg)
            append("\n\n")
            append(if (isEnglish) "App Check mode: $label" else "Chế độ App Check: $label")
            if (!sdkDetail.isNullOrBlank() && !sdkDetail.equals(rawMsg, ignoreCase = true)) {
                append("\n")
                append(if (isEnglish) "Detail: $sdkDetail" else "Chi tiết: $sdkDetail")
            }
            if (!activeSecret.isNullOrBlank() && mode != AppCheckMode.RECAPTCHA) {
                append("\n")
                append(
                    if (isEnglish) "TV Debug Token: $activeSecret"
                    else "Mã Debug Token trên TV: $activeSecret"
                )
            }
            append("\n")
            append(
                if (isEnglish) {
                    "HTTP 403 \"App attestation failed\" happens on sideloaded/debug APKs on TV boxes. Tap \"Personal API Key\" below to use your own Gemini API key (no App Check required on TV Box), or open \"App Check\" / Google / YouTube."
                } else {
                    "Lỗi 403 \"App attestation failed\" xảy ra trên APK cài ngoài TV Box. Hãy bấm \"API Key cá nhân\" bên dưới để dùng Gemini API Key của bạn (chạy thẳng trên TV Box không cần App Check), hoặc dùng Tìm Google / YouTube."
                }
            )
        }
        return AiResponse(errorMessage = builtMsg, isAppCheckError = true)
    }

    private suspend fun askWithPersonalApiKeyStreaming(
        apiKey: String,
        cleanQuery: String,
        isEnglish: Boolean,
        cacheKey: String,
        onStatus: (String) -> Unit,
        onPartialText: (String) -> Unit
    ): AiResponse {
        try {
            onStatus(if (isEnglish) "Asking Google AI (Personal Key)…" else "Google AI đang trả lời (Key cá nhân)…")
            val fastAccumulated = StringBuilder()
            val fastFailure: Exception? = try {
                streamRestWithFirstTokenTimeout(
                    apiKey = apiKey,
                    modelName = FAST_MODEL,
                    thinkingLevel = "MINIMAL",
                    query = cleanQuery,
                    isEnglish = isEnglish,
                    firstTokenTimeoutMs = 9_000L,
                    totalTimeoutMs = 24_000L,
                    accumulated = fastAccumulated,
                    onPartialText = onPartialText
                )
                null
            } catch (e: PersonalApiKeyException) {
                return buildPersonalApiKeyErrorResponse(e.message, isEnglish)
            } catch (e: FirstTokenTimeoutException) {
                Log.w(TAG, "Personal key $FAST_MODEL first-token timeout; falling back to $STANDARD_MODEL.")
                e
            } catch (e: ModelUnavailableException) {
                Log.w(TAG, "Personal key $FAST_MODEL unavailable (${e.code}); falling back to $STANDARD_MODEL.")
                e
            } catch (e: TimeoutCancellationException) {
                if (fastAccumulated.isNotBlank()) null else e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Personal key $FAST_MODEL failed: ${e.message}", e)
                if (fastAccumulated.isNotBlank()) null else e
            }

            if (fastFailure == null && fastAccumulated.isNotBlank()) {
                return finalizeSuccess(fastAccumulated.toString(), isEnglish).also { res ->
                    if (res.errorMessage == null) cache[cacheKey] = CachedAnswer(res, System.currentTimeMillis())
                }
            }

            onStatus(if (isEnglish) "Switching AI route…" else "Đang thử tuyến AI dự phòng…")
            val fallbackAccumulated = StringBuilder()
            try {
                streamRestWithFirstTokenTimeout(
                    apiKey = apiKey,
                    modelName = STANDARD_MODEL,
                    thinkingLevel = "LOW",
                    query = cleanQuery,
                    isEnglish = isEnglish,
                    firstTokenTimeoutMs = 14_000L,
                    totalTimeoutMs = 24_000L,
                    accumulated = fallbackAccumulated,
                    onPartialText = onPartialText
                )
            } catch (e: PersonalApiKeyException) {
                return buildPersonalApiKeyErrorResponse(e.message, isEnglish)
            } catch (_: FirstTokenTimeoutException) {
                return timeoutErrorResponse(isEnglish)
            } catch (e: TimeoutCancellationException) {
                if (fallbackAccumulated.isBlank()) return timeoutErrorResponse(isEnglish)
            }

            if (fallbackAccumulated.isNotBlank()) {
                return finalizeSuccess(fallbackAccumulated.toString(), isEnglish).also { res ->
                    if (res.errorMessage == null) cache[cacheKey] = CachedAnswer(res, System.currentTimeMillis())
                }
            }

            if (fastFailure != null && fastFailure !is FirstTokenTimeoutException && fastFailure !is TimeoutCancellationException) {
                throw fastFailure
            }

            return AiResponse(
                errorMessage = if (isEnglish) {
                    "Google AI returned an empty response."
                } else {
                    "Google AI chưa có câu trả lời cho nội dung này."
                }
            )
        } catch (e: TimeoutCancellationException) {
            return timeoutErrorResponse(isEnglish)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PersonalApiKeyException) {
            return buildPersonalApiKeyErrorResponse(e.message, isEnglish)
        } catch (e: Exception) {
            Log.e(TAG, "Personal Gemini API key request failed", e)
            val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
            return AiResponse(
                errorMessage = msg ?: if (isEnglish) {
                    "Could not connect to Google AI with personal API key."
                } else {
                    "Không kết nối được Google AI bằng API Key cá nhân."
                }
            )
        }
    }

    private suspend fun streamRestWithFirstTokenTimeout(
        apiKey: String,
        modelName: String,
        thinkingLevel: String,
        query: String,
        isEnglish: Boolean,
        firstTokenTimeoutMs: Long,
        totalTimeoutMs: Long,
        accumulated: StringBuilder,
        onPartialText: (String) -> Unit
    ) {
        withTimeout(totalTimeoutMs) {
            coroutineScope {
                var gotFirstText = false
                var activeConn: HttpURLConnection? = null
                val watchdog = launch {
                    delay(firstTokenTimeoutMs)
                    if (!gotFirstText) {
                        try {
                            activeConn?.disconnect()
                        } catch (_: Exception) {
                        }
                        this@coroutineScope.cancel(FirstTokenTimeoutException())
                    }
                }
                try {
                    val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?alt=sse"
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 8_000
                        readTimeout = 15_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("Accept", "text/event-stream")
                        setRequestProperty("x-goog-api-key", apiKey)
                    }
                    activeConn = conn

                    val body = JSONObject().apply {
                        put(
                            "systemInstruction",
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", if (isEnglish) EN_SYSTEM_PROMPT else VI_SYSTEM_PROMPT))
                            )
                        )
                        put(
                            "contents",
                            JSONArray().put(
                                JSONObject()
                                    .put("role", "user")
                                    .put("parts", JSONArray().put(JSONObject().put("text", query)))
                            )
                        )
                        put(
                            "generationConfig",
                            JSONObject()
                                .put("maxOutputTokens", 320)
                                .put("thinkingConfig", JSONObject().put("thinkingLevel", thinkingLevel))
                        )
                    }.toString()

                    try {
                        conn.outputStream.use { out ->
                            out.write(body.toByteArray(StandardCharsets.UTF_8))
                        }

                        val code = conn.responseCode
                        if (code !in 200..299) {
                            val errBody = try {
                                (conn.errorStream ?: conn.inputStream)?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                            } catch (_: Exception) {
                                ""
                            }
                            val parsedErr = extractGoogleApiErrorMessage(errBody) ?: "HTTP $code"
                            val lower = "$errBody $parsedErr".lowercase()
                            val looksLikeKeyAuthIssue = code == 401 || code == 403 ||
                                (code == 400 && (lower.contains("api key") || lower.contains("api_key")))
                            if (looksLikeKeyAuthIssue) {
                                throw PersonalApiKeyException(code, parsedErr)
                            }
                            if (code == 404 || code == 400 || code == 429) {
                                throw ModelUnavailableException(code, parsedErr)
                            }
                            throw IllegalStateException(parsedErr)
                        }

                        var lastEmitMs = 0L
                        BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val line = reader.readLine() ?: break
                                if (!line.startsWith("data:")) continue
                                val payload = line.removePrefix("data:").trim()
                                if (payload.isEmpty() || payload == "[DONE]") continue
                                val piece = extractTextFromSseJson(payload)
                                if (piece.isNotEmpty()) {
                                    val first = !gotFirstText
                                    if (first) {
                                        gotFirstText = true
                                        watchdog.cancel()
                                    }
                                    accumulated.append(piece)
                                    val now = System.currentTimeMillis()
                                    if (first || now - lastEmitMs >= 45L) {
                                        lastEmitMs = now
                                        onPartialText(cleanForTvDisplay(accumulated.toString()))
                                    }
                                }
                            }
                        }
                    } finally {
                        activeConn = null
                        try {
                            conn.disconnect()
                        } catch (_: Exception) {
                        }
                    }
                } finally {
                    watchdog.cancel()
                }
            }
        }
    }

    private fun extractTextFromSseJson(payload: String): String {
        return try {
            val root = JSONObject(payload)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                val t = p.optString("text", "")
                if (t.isNotEmpty()) sb.append(t)
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractGoogleApiErrorMessage(errBody: String): String? {
        if (errBody.isBlank()) return null
        return try {
            val errObj = JSONObject(errBody).optJSONObject("error")
            errObj?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPersonalApiKeyErrorResponse(rawDetail: String?, isEnglish: Boolean): AiResponse {
        val detail = rawDetail?.takeIf { it.isNotBlank() }.orEmpty()
        val msg = buildString {
            if (detail.isNotEmpty()) {
                append(detail)
                append("\n\n")
            }
            append(
                if (isEnglish) {
                    "Your personal Gemini API key was rejected or lacks permission. Tap \"Personal API Key\" below to update or scan QR from your phone."
                } else {
                    "Gemini API Key cá nhân chưa hợp lệ hoặc bị giới hạn quyền. Hãy bấm \"API Key cá nhân\" bên dưới để sửa hoặc quét QR gửi lại từ điện thoại."
                }
            )
        }
        return AiResponse(errorMessage = msg, isApiKeyError = true)
    }

    private fun cleanForTvDisplay(text: String): String {
        return text
            .replace(REGEX_CODE_FENCE, "")
            .replace("```", "")
            .replace(REGEX_BOLD, "$1")
            .replace(REGEX_UNDERLINE, "$1")
            .replace(REGEX_HEADING, "")
            .replace(REGEX_BULLET, "- ")
            .replace(REGEX_MULTI_NL, "\n\n")
            .trim()
    }

    private fun cleanForSpeech(text: String): String {
        val compact = text
            .replace(REGEX_SPEECH_BULLET, "")
            .replace(REGEX_MULTI_WS, " ")
            .trim()
        return if (compact.length > 420) {
            val cut = compact.lastIndexOf('.', 420).takeIf { it > 160 } ?: 420
            compact.substring(0, cut).trim() + "."
        } else {
            compact
        }
    }
}
