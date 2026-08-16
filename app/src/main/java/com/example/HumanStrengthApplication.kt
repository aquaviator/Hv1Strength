package com.example

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

class HumanStrengthApplication : Application(), Configuration.Provider {

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
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                val prefs = getSharedPreferences("strength_settings", MODE_PRIVATE)
                if (com.example.core.sync.UnattendedSyncPolicy.shouldRequestForegroundSync(
                        prefs.getBoolean("auth_is_logged_in", false),
                        prefs.getString("auth_provider", null),
                        prefs.getBoolean("auth_profile_handoff_complete", false)
                    )
                ) com.example.core.sync.SyncScheduler.scheduleImmediate(this@HumanStrengthApplication)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun initializeFirebase() {
        isFirebaseConfigured = determineFirebaseAvailability {
            val firebaseApp = FirebaseApp.getApps(this).firstOrNull()
                ?: FirebaseApp.initializeApp(this)
            if (firebaseApp != null) {
                Log.i(TAG, "stage=firebase result=READY")
                initializeAppCheck(BuildConfig.DEBUG)
                true
            } else {
                Log.w(TAG, "Firebase configuration was not found. Cloud authentication is unavailable.")
                false
            }
        }
    }

    private fun initializeAppCheck(isDebug: Boolean): AppCheckInitializationState {
        appCheckInitializationState = try {
            val factory = BuildVariantAppCheckProviderFactory.create()
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
            Log.i(TAG, "stage=app_check result=READY provider=${if (isDebug) "DEBUG" else "PLAY_INTEGRITY"}")
            if (isDebug) Log.i(TAG, "stage=app_check_debug result=REGISTER_TOKEN_IN_FIREBASE_CONSOLE token_not_logged=true")
            AppCheckInitializationState.READY
        } catch (_: IllegalStateException) {
            Log.e(TAG, "stage=app_check result=UNAVAILABLE")
            AppCheckInitializationState.UNAVAILABLE
        } catch (_: Exception) {
            Log.e(TAG, "stage=app_check result=FAILED")
            AppCheckInitializationState.FAILED
        }
        return appCheckInitializationState
    }

    companion object {
        private const val TAG = "HumanStrengthApplication"
        
        // Dynamic flag indicating if Firebase configuration is missing
        var isFirebaseConfigured: Boolean = false
            private set
        @Volatile var appCheckInitializationState: AppCheckInitializationState = AppCheckInitializationState.UNAVAILABLE
            private set

        internal fun determineFirebaseAvailability(initializer: () -> Boolean): Boolean {
            return try {
                initializer()
            } catch (error: Exception) {
                Log.e(TAG, "Firebase initialization failed. Cloud authentication is unavailable.", error)
                false
            }
        }
    }
}

enum class AppCheckInitializationState { READY, UNAVAILABLE, FAILED }
enum class AppCheckProviderKind { DEBUG, PLAY_INTEGRITY }
internal fun appCheckProviderKind(isDebug: Boolean) = if (isDebug) AppCheckProviderKind.DEBUG else AppCheckProviderKind.PLAY_INTEGRITY
