package com.example.core.identity

import android.content.Context
import java.util.UUID

object DeviceIdGenerator {
    private const val PREFS_NAME = "device_identity_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    @Volatile
    var appContext: Context? = null

    fun getOrGenerateDeviceId(context: Context? = null): String {
        val targetContext = context?.applicationContext ?: appContext
        if (targetContext == null) {
            return "device_fallback"
        }
        val prefs = targetContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            // Generates stable 12 lowercase alphanumeric characters after prefix
            val randomPart = UUID.randomUUID().toString().replace("-", "").lowercase().take(12)
            id = "device_$randomPart"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }
}
