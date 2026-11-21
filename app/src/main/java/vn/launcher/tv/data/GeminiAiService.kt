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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Calls Google Gemini via Firebase AI Logic with streaming, low-latency thinking level,
 * fast App Check verification, and automatic fallback from gemini-3.8-flash to
 * gemini-3.5-flash-lite if the primary model stalls before first token.
 */
object GeminiAiService {

    private const val TAG = "GeminiAiService"
    private const val PRIMARY_MODEL = "gemini-3.8-flash"
    private const val LITE_MODEL = "gemini-3.5-flash-lite"

    private const val VI_SYSTEM_PROMPT =
        "Bạn là trợ lý Google AI thông minh trên Android TV (VietLauncher). " +
            "Hãy trả lời chính xác, dễ hiểu, súc tích bằng tiếng Việt (hoặc tiếng Anh nếu người dùng hỏi bằng tiếng Anh), " +
            "trình bày văn bản thuần gọn đẹp để hiển thị trên màn hình TV và đọc thành tiếng tự nhiên " +
            "(khoảng 3–6 câu ngắn hoặc gạch đầu dòng đơn giản bằng dấu '-'). Không dùng ký hiệu markdown ** hay ###."

    private const val EN_SYSTEM_PROMPT =
        "You are a helpful Google AI smart voice assistant on an Android TV launcher (VietLauncher). " +
            "Give clear, accurate, concise answers in plain text suitable for a TV screen and voice readback " +
            "(about 3-6 short sentences or brief bullet points using '-'). Avoid markdown headers or asterisks."

    private class FirstTokenTimeoutException : CancellationException("First token timed out")

    private fun buildModel(modelName: String, isEnglish: Boolean, thinking: ThinkingLevel): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                maxOutputTokens = 700
                thinkingConfig = thinkingConfig {
                    thinkingLevel = thinking
                }
            },
            systemInstruction = content {
                text(if (isEnglish) EN_SYSTEM_PROMPT else VI_SYSTEM_PROMPT)
            }
        )
    }

    private val viPrimaryModel by lazy { buildModel(PRIMARY_MODEL, isEnglish = false, ThinkingLevel.LOW) }
    private val enPrimaryModel by lazy { buildModel(PRIMARY_MODEL, isEnglish = true, ThinkingLevel.LOW) }
    private val viLiteModel by lazy { buildModel(LITE_MODEL, isEnglish = false, ThinkingLevel.MINIMAL) }
    private val enLiteModel by lazy { buildModel(LITE_MODEL, isEnglish = true, ThinkingLevel.MINIMAL) }

    data class AiResponse(
        val answerText: String = "",
        val spokenText: String = "",
        val errorMessage: String? = null,
        val isAppCheckError: Boolean = false
    )

    suspend fun askStreaming(
        context: Context,
        query: String,
        isEnglish: Boolean,
        onStatus: (String) -> Unit = {},
        onPartialText: (String) -> Unit
    ): AiResponse = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext AiResponse(
                errorMessage = if (isEnglish) "Empty question." else "Câu hỏi trống."
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

            // Phase 1: Bounded App Check verification so a stalled reCAPTCHA on Android TV
            // surfaces quickly instead of spinning silently for 60 seconds.
            onStatus(if (isEnglish) "Verifying App Check…" else "Đang xác thực App Check…")
            val appCheckTimeoutMs = if (currentAppCheckMode() == AppCheckMode.RECAPTCHA) 14_000L else 8_000L
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

            // Phase 2: Primary streaming call with ThinkingLevel.LOW and a 14s first-token watchdog
            onStatus(if (isEnglish) "Asking Google AI…" else "Google AI đang tìm câu trả lời…")
            val primaryModel = if (isEnglish) enPrimaryModel else viPrimaryModel
            val primaryAccumulated = StringBuilder()

            val primaryFailure: Exception? = try {
                streamWithFirstTokenTimeout(
                    model = primaryModel,
                    query = cleanQuery,
                    firstTokenTimeoutMs = 14_000L,
                    totalTimeoutMs = 35_000L,
                    accumulated = primaryAccumulated,
                    onPartialText = onPartialText
                )
                null
            } catch (e: FirstTokenTimeoutException) {
                Log.w(TAG, "$PRIMARY_MODEL produced no token within 14s; falling back to $LITE_MODEL.")
                e
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "$PRIMARY_MODEL timed out; checking partial or falling back.")
                if (primaryAccumulated.isNotBlank()) null else e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$PRIMARY_MODEL failed: ${e.message}", e)
                if (isAppCheckExceptionMessage(e.localizedMessage)) throw e
                if (primaryAccumulated.isNotBlank()) null else e
            }

            if (primaryFailure == null && primaryAccumulated.isNotBlank()) {
                return@withContext finalizeSuccess(primaryAccumulated.toString(), isEnglish)
            }

            // Phase 3: Ultra-fast fallback with gemini-3.5-flash-lite (ThinkingLevel.MINIMAL)
            onStatus(
                if (isEnglish) "Slow response, trying faster Flash-Lite…"
                else "Phản hồi chậm, đang chuyển sang Flash-Lite nhanh hơn…"
            )
            val liteModel = if (isEnglish) enLiteModel else viLiteModel
            val liteAccumulated = StringBuilder()
            try {
                streamWithFirstTokenTimeout(
                    model = liteModel,
                    query = cleanQuery,
                    firstTokenTimeoutMs = 18_000L,
                    totalTimeoutMs = 25_000L,
                    accumulated = liteAccumulated,
                    onPartialText = onPartialText
                )
            } catch (_: FirstTokenTimeoutException) {
                return@withContext timeoutErrorResponse(isEnglish)
            } catch (e: TimeoutCancellationException) {
                if (liteAccumulated.isBlank()) return@withContext timeoutErrorResponse(isEnglish)
            }

            if (liteAccumulated.isNotBlank()) {
                return@withContext finalizeSuccess(liteAccumulated.toString(), isEnglish)
            }

            if (primaryFailure != null && primaryFailure !is FirstTokenTimeoutException && primaryFailure !is TimeoutCancellationException) {
                throw primaryFailure
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
                            if (!gotFirstText) {
                                gotFirstText = true
                                watchdog.cancel()
                            }
                            accumulated.append(piece)
                            onPartialText(cleanForTvDisplay(accumulated.toString()))
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
                    "If this TV box lacks Google Play Services or reCAPTCHA fails/hangs, press \"App Check\" below to enter a registered Firebase App Check Debug Token."
                } else {
                    "Nếu TV không có Google Play Services hoặc reCAPTCHA bị treo/lỗi, bấm nút \"App Check\" bên dưới để xem/nhập mã Debug Token từ Firebase Console."
                }
            )
        }
        return AiResponse(errorMessage = builtMsg, isAppCheckError = true)
    }

    private fun cleanForTvDisplay(text: String): String {
        return text
            .replace(Regex("```[a-zA-Z]*\\n?"), "")
            .replace("```", "")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("__(.*?)__"), "$1")
            .replace(Regex("(?m)^#{1,6}\\s+"), "")
            .replace(Regex("(?m)^\\*\\s+"), "- ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun cleanForSpeech(text: String): String {
        val compact = text
            .replace(Regex("(?m)^[-•]\\s+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (compact.length > 520) {
            val cut = compact.lastIndexOf('.', 520).takeIf { it > 200 } ?: 520
            compact.substring(0, cut).trim() + "."
        } else {
            compact
        }
    }
}
