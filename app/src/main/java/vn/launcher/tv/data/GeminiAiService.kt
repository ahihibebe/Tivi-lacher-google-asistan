package vn.launcher.tv.data

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Calls Google Gemini via Firebase AI Logic (gemini-3.8-flash) with streaming
 * and App Check attestation (no client-side API key required).
 */
object GeminiAiService {

    private const val TAG = "GeminiAiService"
    private const val MODEL_NAME = "gemini-3.8-flash"

    private val viModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 700
            },
            systemInstruction = content {
                text(
                    "Bạn là trợ lý Google AI thông minh trên Android TV (VietLauncher). " +
                        "Hãy trả lời chính xác, dễ hiểu, súc tích bằng tiếng Việt (hoặc tiếng Anh nếu người dùng hỏi bằng tiếng Anh), " +
                        "trình bày văn bản thuần gọn đẹp để hiển thị trên màn hình TV và đọc thành tiếng tự nhiên " +
                        "(khoảng 3–6 câu ngắn hoặc gạch đầu dòng đơn giản bằng dấu '-'). Không dùng ký hiệu markdown ** hay ###."
                )
            }
        )
    }

    private val enModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.4f
                maxOutputTokens = 700
            },
            systemInstruction = content {
                text(
                    "You are a helpful Google AI smart voice assistant on an Android TV launcher (VietLauncher). " +
                        "Give clear, accurate, concise answers in plain text suitable for a TV screen and voice readback " +
                        "(about 3-6 short sentences or brief bullet points using '-'). Avoid markdown headers or asterisks."
                )
            }
        )
    }

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
        onPartialText: (String) -> Unit
    ): AiResponse {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return AiResponse(
                errorMessage = if (isEnglish) "Empty question." else "Câu hỏi trống."
            )
        }

        val accumulated = StringBuilder()

        return try {
            if (FirebaseApp.getApps(context).isEmpty() && FirebaseApp.initializeApp(context) == null) {
                return AiResponse(
                    errorMessage = if (isEnglish) {
                        "Firebase is not configured in this APK build (missing app/google-services.json). Build from AI Studio or add GOOGLE_SERVICES_JSON to GitHub Secrets."
                    } else {
                        "Bản APK này chưa có tệp cấu hình app/google-services.json. Hãy build từ AI Studio hoặc thêm secret GOOGLE_SERVICES_JSON trên GitHub."
                    }
                )
            }
            val activeModel = if (isEnglish) enModel else viModel
            withTimeout(60_000L) {
                activeModel.generateContentStream(cleanQuery).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        onPartialText(cleanForTvDisplay(accumulated.toString()))
                    }
                }
            }
            val finalDisplay = cleanForTvDisplay(accumulated.toString())
            if (finalDisplay.isBlank()) {
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
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI generation timed out", e)
            AiResponse(
                errorMessage = if (isEnglish) {
                    "Google AI request timed out after 60 seconds. Please try again."
                } else {
                    "Hết thời gian chờ phản hồi từ Google AI (60 giây). Hãy thử lại."
                }
            )
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
            val appCheckRelated = rawMsg != null && (
                rawMsg.contains("App Check", ignoreCase = true) ||
                    rawMsg.contains("AppCheck", ignoreCase = true) ||
                    rawMsg.contains("attestation", ignoreCase = true) ||
                    rawMsg.contains("token is invalid", ignoreCase = true) ||
                    rawMsg.contains("403")
                )
            if (appCheckRelated) {
                val sdkDetail = diagnoseAppCheckTokenError(context)
                val mode = currentAppCheckMode()
                val modeLabel = when (mode) {
                    AppCheckMode.RECAPTCHA -> "reCAPTCHA Enterprise"
                    AppCheckMode.DEBUG_INTENT -> "Debug Token (Intent)"
                    AppCheckMode.DEBUG_CUSTOM -> "Debug Token (TV)"
                    AppCheckMode.DEBUG_AUTO_UUID -> "Debug Token (Auto UUID)"
                    AppCheckMode.UNCONFIGURED -> if (isEnglish) "Unconfigured" else "Chưa cấu hình"
                }
                val activeSecret = getActiveDebugSecret(context)
                val builtMsg = buildString {
                    append(rawMsg)
                    append("\n\n")
                    append(if (isEnglish) "App Check mode: $modeLabel" else "Chế độ App Check: $modeLabel")
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
                            "If this TV box lacks Google Play Services or reCAPTCHA fails, press \"App Check\" below to enter a registered Firebase App Check Debug Token."
                        } else {
                            "Nếu TV không có Google Play Services hoặc chưa đăng ký Debug Token, bấm nút \"App Check\" bên dưới để xem/nhập mã Debug Token từ Firebase Console."
                        }
                    )
                }
                AiResponse(errorMessage = builtMsg, isAppCheckError = true)
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
