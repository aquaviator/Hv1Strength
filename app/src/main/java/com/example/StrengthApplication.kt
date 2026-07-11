package com.example

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StrengthApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Install global crash logger to output any fatal unhandled crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CRASH_LOGGER", "FATAL EXCEPTION in thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        Log.i(TAG, "onCreate: Application initialization started. Global crash handler installed.")
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            val firebaseApp = FirebaseApp.initializeApp(this)
            isFirebaseConfigured = firebaseApp != null

            if (isFirebaseConfigured) {
                Log.i(TAG, "Firebase initialized for project ${firebaseApp?.options?.projectId}")

                // App Check is intentionally best-effort. Authentication and the offline-first
                // Room experience must continue even if a provider is unavailable on a test device.
                initializeAppCheck(BuildConfig.DEBUG)
            } else {
                Log.w(TAG, "Firebase configuration was not found. Running in offline mode.")
            }
        } catch (e: Exception) {
            isFirebaseConfigured = false
            Log.e(TAG, "Firebase initialization failed. Running in offline mode.", e)
        }
    }

    private fun initializeAppCheck(isDebug: Boolean) {
        try {
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            if (isDebug) {
                try {
                    val debugFactoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    val getInstanceMethod = debugFactoryClass.getMethod("getInstance")
                    val factory = getInstanceMethod.invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
                    firebaseAppCheck.installAppCheckProviderFactory(factory)
                    Log.i(TAG, "Firebase App Check initialized with Debug Provider via reflection")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load DebugAppCheckProviderFactory via reflection. Proceeding without App Check debug provider.", e)
                }
            } else {
                try {
                    // Try Play Integrity
                    val playIntegrityClass = Class.forName("com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory")
                    val getInstanceMethod = playIntegrityClass.getMethod("getInstance")
                    val factory = getInstanceMethod.invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
                    firebaseAppCheck.installAppCheckProviderFactory(factory)
                    Log.i(TAG, "Firebase App Check initialized with Play Integrity Provider")
                } catch (e: Exception) {
                    try {
                        // Fall back to ReCaptcha Enterprise
                        val recaptchaClass = Class.forName("com.google.firebase.appcheck.recaptcha.ReCaptchaEnterpriseAppCheckProviderFactory")
                        val getInstanceMethod = recaptchaClass.getMethod("getInstance")
                        val factory = getInstanceMethod.invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
                        firebaseAppCheck.installAppCheckProviderFactory(factory)
                        Log.i(TAG, "Firebase App Check initialized with ReCaptcha Enterprise Provider")
                    } catch (e2: Exception) {
                        Log.w(TAG, "Could not load any App Check production provider via reflection", e2)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "App Check initialization failed", e)
        }
    }

    private fun configureEmulators() {
        try {
            val auth = FirebaseAuth.getInstance()
            auth.useEmulator("10.0.2.2", 9099)
            Log.i(TAG, "Using Firebase Auth Emulator at 10.0.2.2:9099")
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure Auth Emulator", e)
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.useEmulator("10.0.2.2", 8080)
            Log.i(TAG, "Using Firestore Emulator at 10.0.2.2:8080")
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure Firestore Emulator", e)
        }
    }

    companion object {
        private const val TAG = "StrengthApplication"
        
        // Dynamic flag indicating if Firebase configuration is missing
        var isFirebaseConfigured: Boolean = false
            private set
    }
}
