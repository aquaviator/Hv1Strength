package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StrengthApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        try {
            // Check if Firebase is available/can be initialized
            val app = FirebaseApp.initializeApp(this)
            if (app != null) {
                isFirebaseConfigured = true
                Log.i(TAG, "Firebase initialized successfully")
                
                // Initialize App Check
                initializeAppCheck(isDebug)

                // Configure emulators if in debug build
                if (isDebug) {
                    configureEmulators()
                }
            } else {
                Log.w(TAG, "Firebase initialization returned null (missing google-services.json?)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed. Running in offline fallback mode.", e)
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
