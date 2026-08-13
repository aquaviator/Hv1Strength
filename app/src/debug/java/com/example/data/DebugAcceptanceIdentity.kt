package com.example.data

import android.content.Context
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth

internal object DebugAcceptanceIdentity {
    private const val PREFS = "v31_debug_acceptance"
    private const val PROJECT = "demo-hv1-planner-sync"
    private const val APP = "v31-debug-acceptance"
    private const val MAX_SESSION_MS = 15 * 60 * 1000L

    fun arm(context: Context, uid: String, humanId: String, now: Long = System.currentTimeMillis()) {
        require(BuildConfig.DEBUG && uid == SYNTHETIC_UID && humanId == SYNTHETIC_HUMAN)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("armed", true).putLong("expires", now + MAX_SESSION_MS)
            .putString("uid", uid).putString("human", humanId)
            .putString("project", PROJECT).putString("host", "10.0.2.2").apply()
    }

    fun disarm(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()

    fun hasValidAcceptanceMarker(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return BuildConfig.DEBUG && prefs.getBoolean("armed", false) &&
            prefs.getLong("expires", 0) > System.currentTimeMillis() &&
            prefs.getString("project", null) == PROJECT && prefs.getString("host", null) == "10.0.2.2" &&
            prefs.getString("uid", null) == SYNTHETIC_UID && prefs.getString("human", null) == SYNTHETIC_HUMAN
    }

    fun isValidAcceptanceSession(context: Context): Boolean {
        if (!hasValidAcceptanceMarker(context)) return false
        return runCatching { dependencies(context)?.firebaseAuth?.currentUser?.uid == SYNTHETIC_UID }.getOrDefault(false)
    }

    fun dependencies(context: Context): AuthDependencies? {
        if (!BuildConfig.DEBUG) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("armed", false) || prefs.getLong("expires", 0) <= System.currentTimeMillis()) return null
        val project = prefs.getString("project", null)
        val host = prefs.getString("host", null)
        val uid = prefs.getString("uid", null)
        val human = prefs.getString("human", null)
        if (project != PROJECT || !project.startsWith("demo-") || host != "10.0.2.2" ||
            uid != SYNTHETIC_UID || human != SYNTHETIC_HUMAN) return failClosed()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == APP } ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:123456789:android:v31acceptance")
                .setApiKey("local-emulator-only-key").build(), APP) ?: return failClosed()
        val auth = FirebaseAuth.getInstance(app).also { it.useEmulator(host, 9099) }
        val client = object : HumanIdentityClient {
            override suspend fun ensureHumanIdentity(): HumanIdentityResult {
                val current = auth.currentUser ?: return HumanIdentityResult.Unauthenticated
                if (current.uid != uid || !prefs.getBoolean("armed", false) ||
                    prefs.getLong("expires", 0) <= System.currentTimeMillis()) return HumanIdentityResult.IdentityConflict
                return HumanIdentityResult.Success(AuthoritativeHumanIdentity(human, ACTIVE_IDENTITY_STATUS, SUPPORTED_IDENTITY_SCHEMA_VERSION))
            }
        }
        return AuthDependencies(auth, client)
    }

    private fun failClosed(): AuthDependencies = AuthDependencies(null, object : HumanIdentityClient {
        override suspend fun ensureHumanIdentity() = HumanIdentityResult.IdentityConflict
    })

    const val SYNTHETIC_UID = "planner-client-owner"
    const val SYNTHETIC_HUMAN = "human_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}

internal object BuildVariantAuthDependenciesFactory {
    fun create(context: Context): AuthDependencies = DebugAcceptanceIdentity.dependencies(context)
        ?: AuthDependencies(runCatching { FirebaseAuth.getInstance() }.getOrNull(), FirebaseHumanIdentityClient())
}
