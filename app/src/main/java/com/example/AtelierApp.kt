package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.settings.AppSettings
import com.google.firebase.FirebaseApp

class AtelierApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseBootstrap.initialize(this)
        com.example.util.GarmentPhotoProcessor.warmUp(this)
        if (BuildConfig.USE_FIREBASE_EMULATORS && !BuildConfig.LOCAL_ONLY && FirebaseBootstrap.isReady()) {
            // Local test mode: Auth emulator (reached through `adb reverse tcp:9099 tcp:9099`).
            com.google.firebase.auth.FirebaseAuth.getInstance().useEmulator("127.0.0.1", 9099)
        }
    }
}

object FirebaseBootstrap {
    private const val TAG = "FirebaseBootstrap"

    /** Null when Firebase is ready, otherwise a short reason shown on the setup screen. */
    @Volatile
    var setupProblem: String? = null
        private set

    fun isReady(): Boolean = setupProblem == null

    /**
     * Starts Firebase from the keys saved in Settings when they exist, otherwise from the
     * google-services.json bundled with the build. Runs once in Application.onCreate.
     */
    fun initialize(context: Context) {
        // Phone-only build: Firebase isn't used at all.
        if (BuildConfig.LOCAL_ONLY) {
            setupProblem = null
            return
        }
        val settings = AppSettings.get(context)
        val custom = settings.firebaseConfig
        setupProblem = try {
            if (custom.isComplete()) {
                val options = custom.toOptions()
                val existing = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                if (existing != null && existing.options.projectId != options.projectId) {
                    // The bundled config auto-initialised a different project; replace it.
                    existing.delete()
                }
                if (FirebaseApp.getApps(context).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
                    FirebaseApp.initializeApp(context, options)
                }
                null
            } else {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                if (FirebaseApp.getApps(context).isEmpty()) {
                    "No Firebase keys yet. Enter them below (or paste your google-services.json)."
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed", e)
            "Firebase could not start with these keys: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    /** Relaunches the app so new Firebase keys take effect. */
    fun restartApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val restart = Intent.makeRestartActivityTask(launch.component)
        context.startActivity(restart)
        Runtime.getRuntime().exit(0)
    }
}
