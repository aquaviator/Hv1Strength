package com.example.core.identity

import android.content.Context
import java.util.UUID

object HumanUserIdGenerator {
    private const val PREFS_NAME = "human_identity_prefs"
    private const val KEY_OFFLINE_HUMAN_ID = "offline_human_user_id"

    @Volatile
    var appContext: Context? = null

    fun getOrGenerateOfflineHumanId(context: Context? = null): String {
        val targetContext = context?.applicationContext ?: appContext
        if (targetContext == null) {
            // Fallback during migrations/tests where context is not immediately available
            android.util.Log.w("HumanUserIdGenerator", "getOrGenerateOfflineHumanId: context was null! Returning fallback 'human_offlineusr'")
            return "human_offlineusr"
        }
        val prefs = targetContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_OFFLINE_HUMAN_ID, null)
        if (id == null) {
            // Generates stable 12 lowercase alphanumeric characters after prefix
            val randomPart = UUID.randomUUID().toString().replace("-", "").lowercase().take(12)
            id = "human_$randomPart"
            prefs.edit().putString(KEY_OFFLINE_HUMAN_ID, id).apply()
            android.util.Log.i("HumanUserIdGenerator", "Generated new offline human ID: $id")
        } else {
            android.util.Log.d("HumanUserIdGenerator", "Loaded existing offline human ID: $id")
        }
        return id
    }

    fun mapUserIdToHumanUserId(userId: String?, context: Context? = null): String {
        if (userId.isNullOrEmpty() || userId == "offline") {
            return getOrGenerateOfflineHumanId(context)
        }
        if (userId.startsWith("human_")) return userId
        val hash = userId.hashCode().toString().replace("-", "n").padEnd(12, 'x').take(12)
        return "human_$hash"
    }
}
