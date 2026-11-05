package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Error from the model service. [retryable] is true for overload / rate-limit / timeouts. */
class GeminiException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
    /** Quota/rate limit hit: don't retry this model, go straight to the next one. */
    val rateLimited: Boolean = false,
    /** The model isn't available on this key's plan at all (e.g. image models on the free tier). */
    val notInPlan: Boolean = false
) : Exception(message, cause)

/** One way of talking to Gemini: a user API key over REST, or Firebase AI Logic. */
interface GeminiEngine {
    val label: String

    /** Returns the model's JSON text. [schema] is an OpenAPI-style schema (REST only). */
    suspend fun generateJson(
        model: String,
        prompt: String,
        images: List<Bitmap>,
        schema: JSONObject?,
        temperature: Float? = null
    ): String

    /** Returns the first image the model produced, or null. */
    suspend fun generateImage(model: String, prompt: String, images: List<Bitmap>): Bitmap?
}

/** Calls the Gemini API directly with the API key entered in Settings. */
class RestGeminiEngine(
    private val apiKey: String,
    private val http: OkHttpClient = sharedClient
) : GeminiEngine {

    override val label = "Gemini API key"

    override suspend fun generateJson(
        model: String,
        prompt: String,
        images: List<Bitmap>,
        schema: JSONObject?,
        temperature: Float?
    ): String {
        val config = JSONObject().put("responseMimeType", "application/json")
        if (schema != null) config.put("responseSchema", schema)
        if (temperature != null) config.put("temperature", temperature.toDouble())
        val response = post(model, buildBody(prompt, images, config))
        val text = partsOf(response).mapNotNull { it.optString("text").takeIf { t -> t.isNotBlank() } }
            .joinToString("")
        if (text.isBlank()) throw GeminiException(blockedReason(response) ?: "The model returned no text.", retryable = true)
        return text
    }

    override suspend fun generateImage(model: String, prompt: String, images: List<Bitmap>): Bitmap? {
        val config = JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
        val response = post(model, buildBody(prompt, images, config))
        for (part in partsOf(response)) {
            val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data") ?: continue
            val data = inline.optString("data")
            if (data.isBlank()) continue
            val bytes = Base64.decode(data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
        }
        return null
    }

    /** Lists model ids this key can use for generateContent. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val names = mutableListOf<String>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$BASE_URL/models?pageSize=200")
                if (pageToken != null) append("&pageToken=").append(pageToken)
            }
            val request = Request.Builder().url(url).header("x-goog-api-key", apiKey).get().build()
            val root = http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw GeminiException(errorMessage(resp.code, body), retryable = false)
                JSONObject(body)
            }
            val models = root.optJSONArray("models") ?: JSONArray()
            for (i in 0 until models.length()) {
                val m = models.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")
                var canGenerate = false
                if (methods != null) {
                    for (j in 0 until methods.length()) {
                        if (methods.getString(j) == "generateContent") canGenerate = true
                    }
                }
                if (canGenerate) names.add(m.getString("name").removePrefix("models/"))
            }
            pageToken = root.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        names.sorted()
    }

    private fun buildBody(prompt: String, images: List<Bitmap>, config: JSONObject): JSONObject {
        val parts = JSONArray()
        images.forEach { bmp ->
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject().put("mimeType", "image/jpeg").put("data", jpegBase64(bmp))
                )
            )
        }
        parts.put(JSONObject().put("text", prompt))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", config)
    }

    private suspend fun post(model: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    if (resp.code == 429 && isNotInPlan(text)) {
                        throw GeminiException(
                            "$model isn't included in your Gemini plan (free tier). Turn on billing for this API key to use it.",
                            retryable = false, rateLimited = true, notInPlan = true
                        )
                    }
                    val retryable = resp.code == 429 || resp.code >= 500
                    throw GeminiException(errorMessage(resp.code, text), retryable, rateLimited = resp.code == 429)
                }
                JSONObject(text)
            }
        } catch (e: GeminiException) {
            throw e
        } catch (e: java.io.IOException) {
            throw GeminiException("Network problem: ${e.localizedMessage ?: "no connection"}", retryable = true, cause = e)
        }
    }

    private fun partsOf(response: JSONObject): List<JSONObject> {
        val parts = response.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return emptyList()
        return (0 until parts.length()).mapNotNull { parts.optJSONObject(it) }
    }

    private fun blockedReason(response: JSONObject): String? {
        val feedback = response.optJSONObject("promptFeedback")?.optString("blockReason")
        if (!feedback.isNullOrBlank()) return "The request was blocked ($feedback)."
        val finish = response.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason")
        if (!finish.isNullOrBlank() && finish != "STOP") return "The model stopped early ($finish)."
        return null
    }

    /** A free-tier quota with no limit value means the model isn't offered on this plan. */
    private fun isNotInPlan(body: String): Boolean = try {
        val details = JSONObject(body).optJSONObject("error")?.optJSONArray("details") ?: JSONArray()
        var freeTier = false
        var hasLimit = false
        for (i in 0 until details.length()) {
            val violations = details.optJSONObject(i)?.optJSONArray("violations") ?: continue
            for (j in 0 until violations.length()) {
                val v = violations.optJSONObject(j) ?: continue
                if (v.optString("quotaId").contains("FreeTier")) freeTier = true
                val value = v.optString("quotaValue")
                if (value.isNotBlank() && value != "0") hasLimit = true
            }
        }
        freeTier && !hasLimit
    } catch (e: Exception) {
        false
    }

    private fun errorMessage(code: Int, body: String): String {
        val apiMessage = try {
            JSONObject(body).optJSONObject("error")?.optString("message")
        } catch (e: Exception) {
            null
        }
        return when (code) {
            400 -> "Gemini rejected the request: ${apiMessage ?: "bad request"}"
            401, 403 -> "Your Gemini API key was refused. Check it in Settings → API keys."
            404 -> "Model not found. Pick another model in Settings → API keys. (${apiMessage.orEmpty()})"
            429 -> "Gemini quota or rate limit reached. Try again in a minute."
            else -> "Gemini error $code${apiMessage?.let { ": $it" }.orEmpty()}"
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        fun jpegBase64(bitmap: Bitmap, quality: Int = 90): String {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }
}

/** Uses Firebase AI Logic (no API key in the app). Used when no Gemini key is saved. */
class FirebaseGeminiEngine : GeminiEngine {

    override val label = "Firebase AI Logic"

    override suspend fun generateJson(
        model: String,
        prompt: String,
        images: List<Bitmap>,
        schema: JSONObject?,
        temperature: Float?
    ): String {
        val generative = Firebase.ai.generativeModel(
            modelName = model,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                if (temperature != null) this.temperature = temperature
            }
        )
        val response = try {
            generative.generateContent(
                content {
                    images.forEach { image(it) }
                    text(prompt)
                }
            )
        } catch (e: Exception) {
            throw GeminiException(e.localizedMessage ?: "Firebase AI request failed.", retryable = true, cause = e)
        }
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw GeminiException("The model returned no text.", retryable = true)
    }

    override suspend fun generateImage(model: String, prompt: String, images: List<Bitmap>): Bitmap? {
        val generative = Firebase.ai.generativeModel(
            modelName = model,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.TEXT, ResponseModality.IMAGE)
            }
        )
        val response = try {
            generative.generateContent(
                content {
                    images.forEach { image(it) }
                    text(prompt)
                }
            )
        } catch (e: Exception) {
            throw GeminiException(e.localizedMessage ?: "Firebase AI request failed.", retryable = true, cause = e)
        }
        return response.candidates.firstOrNull()?.content?.parts
            ?.filterIsInstance<ImagePart>()?.firstOrNull()?.image
    }
}
