package com.example.data

import android.util.Log
import com.example.HumanStrengthApplication
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class TrialPolicy(
    val trialEnabled: Boolean = true,
    val trialDurationDays: Int = 30
)

class PlatformConfigRepository {
    private val TAG = "PlatformConfig"
    
    suspend fun getTrialPolicy(): TrialPolicy {
        return try {
            if (!HumanStrengthApplication.isFirebaseConfigured) {
                return TrialPolicy()
            }
            val db = FirebaseFirestore.getInstance()
            val snapshot = db.collection("platform_config").document("trial_policy").get().await()
            if (snapshot.exists()) {
                val enabled = snapshot.getBoolean("trialEnabled") ?: true
                val duration = snapshot.getLong("trialDurationDays")?.toInt() ?: 30
                TrialPolicy(enabled, duration)
            } else {
                TrialPolicy()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch trial policy, using default", e)
            TrialPolicy()
        }
    }
}
