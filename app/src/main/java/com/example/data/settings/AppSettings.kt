package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.weather.CityLocation
import com.google.firebase.FirebaseOptions
import org.json.JSONObject
import java.io.File

/**
 * Persistent app settings (API keys, Firebase config, preferences).
 *
 * Stored in private SharedPreferences, which survive app updates, so keys only have to be
 * entered once. Excluded from cloud backup (see res/xml/backup_rules.xml).
 */
class AppSettings private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- Gemini ----------

    /** Saved key, else a key bundled into debug builds (app/src/debug/res/values/local_keys.xml). */
    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "").orEmpty().trim()
            .ifBlank { bundledString("gemini_api_key_default").orEmpty() }
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var textModel: String
        get() = prefs.getString(KEY_TEXT_MODEL, null)?.trim()?.ifBlank { null } ?: DEFAULT_TEXT_MODEL
        set(value) = prefs.edit().putString(KEY_TEXT_MODEL, value.trim()).apply()

    var liteModel: String
        get() = prefs.getString(KEY_LITE_MODEL, null)?.trim()?.ifBlank { null } ?: DEFAULT_LITE_MODEL
        set(value) = prefs.edit().putString(KEY_LITE_MODEL, value.trim()).apply()

    var imageModel: String
        get() = prefs.getString(KEY_IMAGE_MODEL, null)?.trim()?.ifBlank { null } ?: DEFAULT_IMAGE_MODEL
        set(value) = prefs.edit().putString(KEY_IMAGE_MODEL, value.trim()).apply()

    // ---------- Firebase ----------

    var firebaseConfig: FirebaseConfig
        get() = FirebaseConfig(
            apiKey = prefs.getString(KEY_FB_API_KEY, "").orEmpty(),
            appId = prefs.getString(KEY_FB_APP_ID, "").orEmpty(),
            projectId = prefs.getString(KEY_FB_PROJECT_ID, "").orEmpty(),
            storageBucket = prefs.getString(KEY_FB_STORAGE_BUCKET, "").orEmpty(),
            senderId = prefs.getString(KEY_FB_SENDER_ID, "").orEmpty(),
            firestoreDatabaseId = prefs.getString(KEY_FB_DATABASE_ID, "").orEmpty(),
            webClientId = prefs.getString(KEY_FB_WEB_CLIENT_ID, "").orEmpty()
        )
        set(value) {
            prefs.edit()
                .putString(KEY_FB_API_KEY, value.apiKey.trim())
                .putString(KEY_FB_APP_ID, value.appId.trim())
                .putString(KEY_FB_PROJECT_ID, value.projectId.trim())
                .putString(KEY_FB_STORAGE_BUCKET, value.storageBucket.trim())
                .putString(KEY_FB_SENDER_ID, value.senderId.trim())
                .putString(KEY_FB_DATABASE_ID, value.firestoreDatabaseId.trim())
                .putString(KEY_FB_WEB_CLIENT_ID, value.webClientId.trim())
                .commit()
        }

    /** Firestore database id: saved value, else the one bundled with the build, else "(default)". */
    fun effectiveFirestoreDatabaseId(): String {
        firebaseConfig.firestoreDatabaseId.trim().takeIf { it.isNotBlank() }?.let { return it }
        return bundledString("firestore_database_id") ?: "(default)"
    }

    /** OAuth web client id used for Google Sign-In: saved value, else the bundled one. */
    fun effectiveWebClientId(): String? {
        firebaseConfig.webClientId.trim().takeIf { it.isNotBlank() }?.let { return it }
        return bundledString("default_web_client_id")
    }

    /** True when the app is running on Firebase options the user entered (not the bundled ones). */
    fun usesCustomFirebase(): Boolean = firebaseConfig.isComplete()

    private fun bundledString(name: String): String? {
        val id = appContext.resources.getIdentifier(name, "string", appContext.packageName)
        if (id == 0) return null
        return appContext.getString(id).trim().ifBlank { null }
    }

    // ---------- Preferences ----------

    var useFahrenheit: Boolean
        get() = prefs.getBoolean(KEY_FAHRENHEIT, false)
        set(value) = prefs.edit().putBoolean(KEY_FAHRENHEIT, value).apply()

    var preferFastLiteModel: Boolean
        get() = prefs.getBoolean(KEY_PREFER_LITE, false)
        set(value) = prefs.edit().putBoolean(KEY_PREFER_LITE, value).apply()

    var autoFallbackOnHighDemand: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FALLBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_FALLBACK, value).apply()

    /** Re-render uploaded garment photos as clean studio product shots with the Gemini image model. */
    var aiStudioPhotos: Boolean
        get() = prefs.getBoolean(KEY_AI_STUDIO_PHOTOS, true)
        set(value) = prefs.edit().putBoolean(KEY_AI_STUDIO_PHOTOS, value).apply()

    var vtonFitNotes: String
        get() = prefs.getString(KEY_VTON_FIT_NOTES, null) ?: "Relaxed tailored silhouette"
        set(value) = prefs.edit().putString(KEY_VTON_FIT_NOTES, value).apply()

    var lastCity: CityLocation?
        get() {
            val raw = prefs.getString(KEY_LAST_CITY, null) ?: return null
            return try {
                val o = JSONObject(raw)
                CityLocation(
                    name = o.getString("name"),
                    regionOrCountry = o.optString("region"),
                    latitude = o.getDouble("lat"),
                    longitude = o.getDouble("lon"),
                    isCurrentGps = o.optBoolean("gps", false)
                )
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_LAST_CITY).apply()
            } else {
                val o = JSONObject()
                    .put("name", value.name)
                    .put("region", value.regionOrCountry)
                    .put("lat", value.latitude)
                    .put("lon", value.longitude)
                    .put("gps", value.isCurrentGps)
                prefs.edit().putString(KEY_LAST_CITY, o.toString()).apply()
            }
        }

    private val vtonPhotoFile get() = File(appContext.filesDir, "vton_reference.jpg")

    fun loadVtonPhoto(): Bitmap? = try {
        if (vtonPhotoFile.exists()) BitmapFactory.decodeFile(vtonPhotoFile.absolutePath) else null
    } catch (e: Exception) {
        null
    }

    fun saveVtonPhoto(bitmap: Bitmap?) {
        try {
            if (bitmap == null) {
                vtonPhotoFile.delete()
            } else {
                vtonPhotoFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
        } catch (ignored: Exception) {
        }
    }

    companion object {
        const val DEFAULT_TEXT_MODEL = "gemini-3.8-flash"
        const val DEFAULT_LITE_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_IMAGE_MODEL = "gemini-nano-banana-2.1"

        private const val PREFS_NAME = "atelier_settings"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_TEXT_MODEL = "gemini_text_model"
        private const val KEY_LITE_MODEL = "gemini_lite_model"
        private const val KEY_IMAGE_MODEL = "gemini_image_model"
        private const val KEY_FB_API_KEY = "fb_api_key"
        private const val KEY_FB_APP_ID = "fb_app_id"
        private const val KEY_FB_PROJECT_ID = "fb_project_id"
        private const val KEY_FB_STORAGE_BUCKET = "fb_storage_bucket"
        private const val KEY_FB_SENDER_ID = "fb_sender_id"
        private const val KEY_FB_DATABASE_ID = "fb_database_id"
        private const val KEY_FB_WEB_CLIENT_ID = "fb_web_client_id"
        private const val KEY_FAHRENHEIT = "use_fahrenheit"
        private const val KEY_PREFER_LITE = "prefer_lite_model"
        private const val KEY_AUTO_FALLBACK = "auto_fallback"
        private const val KEY_AI_STUDIO_PHOTOS = "ai_studio_photos"
        private const val KEY_VTON_FIT_NOTES = "vton_fit_notes"
        private const val KEY_LAST_CITY = "last_city"

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
    }
}

data class FirebaseConfig(
    val apiKey: String = "",
    val appId: String = "",
    val projectId: String = "",
    val storageBucket: String = "",
    val senderId: String = "",
    val firestoreDatabaseId: String = "",
    val webClientId: String = ""
) {
    /** The three values Firebase needs to start. */
    fun isComplete(): Boolean = apiKey.isNotBlank() && appId.isNotBlank() && projectId.isNotBlank()

    fun toOptions(): FirebaseOptions {
        val builder = FirebaseOptions.Builder()
            .setApiKey(apiKey.trim())
            .setApplicationId(appId.trim())
            .setProjectId(projectId.trim())
        if (storageBucket.isNotBlank()) builder.setStorageBucket(storageBucket.trim())
        if (senderId.isNotBlank()) builder.setGcmSenderId(senderId.trim())
        return builder.build()
    }

    class ParseResult(val config: FirebaseConfig?, val message: String)

    companion object {
        /**
         * Reads pasted or picked JSON and explains what was (or wasn't) found. Accepts
         * google-services.json; recognises firebase.json and takes only its database ID.
         */
        fun parseConfigJson(raw: String, packageName: String, current: FirebaseConfig): ParseResult {
            val text = raw.trim().removePrefix("﻿")
            if (text.isBlank()) return ParseResult(null, "Nothing to read: the clipboard or file is empty.")
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return ParseResult(null, "That isn't JSON. Copy the whole google-services.json file.")
            val root = try {
                JSONObject(text.substring(start, end + 1))
            } catch (e: Exception) {
                return ParseResult(null, "The JSON is incomplete or broken. Copy the whole file, including the first { and last }.")
            }
            if (root.has("project_info") && root.has("client")) {
                return try {
                    val parsed = fromGoogleServicesJson(root.toString(), packageName, current.firestoreDatabaseId)
                    ParseResult(parsed, "Filled in from google-services.json. Check the fields, then tap Save Firebase.")
                } catch (e: Exception) {
                    ParseResult(null, "google-services.json is missing fields: ${e.message}")
                }
            }
            val dbId = root.optJSONArray("firestore")?.optJSONObject(0)?.optString("database").orEmpty()
            if (root.has("firestore") || root.has("emulators")) {
                return if (dbId.isNotBlank()) {
                    ParseResult(
                        current.copy(firestoreDatabaseId = dbId),
                        "That's firebase.json. It has no app keys, only the database ID ($dbId), which was filled in. " +
                            "The keys are in google-services.json."
                    )
                } else {
                    ParseResult(null, "That's firebase.json. It has no app keys; use google-services.json instead.")
                }
            }
            return ParseResult(null, "This JSON doesn't look like google-services.json.")
        }

        /**
         * Fills the config from the contents of a google-services.json file, picking the Android
         * client that matches [packageName] when there are several.
         */
        fun fromGoogleServicesJson(json: String, packageName: String, keepDatabaseId: String): FirebaseConfig {
            val root = JSONObject(json)
            val projectInfo = root.getJSONObject("project_info")
            val clients = root.getJSONArray("client")
            var chosen: JSONObject? = null
            for (i in 0 until clients.length()) {
                val c = clients.getJSONObject(i)
                val pkg = c.optJSONObject("client_info")
                    ?.optJSONObject("android_client_info")
                    ?.optString("package_name")
                if (pkg == packageName) {
                    chosen = c
                    break
                }
            }
            val client = chosen ?: clients.getJSONObject(0)
            val appId = client.getJSONObject("client_info").getString("mobilesdk_app_id")
            val apiKey = client.optJSONArray("api_key")?.optJSONObject(0)?.optString("current_key").orEmpty()

            var webClientId = ""
            val oauth = client.optJSONArray("oauth_client")
            if (oauth != null) {
                for (i in 0 until oauth.length()) {
                    val o = oauth.getJSONObject(i)
                    if (o.optInt("client_type") == 3) {
                        webClientId = o.optString("client_id")
                        break
                    }
                }
            }
            if (webClientId.isBlank()) {
                val other = client.optJSONObject("services")
                    ?.optJSONObject("appinvite_service")
                    ?.optJSONArray("other_platform_oauth_client")
                if (other != null) {
                    for (i in 0 until other.length()) {
                        val o = other.getJSONObject(i)
                        if (o.optInt("client_type") == 3) {
                            webClientId = o.optString("client_id")
                            break
                        }
                    }
                }
            }

            return FirebaseConfig(
                apiKey = apiKey,
                appId = appId,
                projectId = projectInfo.optString("project_id"),
                storageBucket = projectInfo.optString("storage_bucket"),
                senderId = projectInfo.optString("project_number"),
                firestoreDatabaseId = keepDatabaseId,
                webClientId = webClientId
            )
        }
    }
}
