package com.example.billing

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed class AccountTrialResult {
    data class Active(
        val uid: String,
        val trialStartedAtMillis: Long,
        val trialEndsAtMillis: Long,
        val serverNowMillis: Long
    ) : AccountTrialResult()

    data class Expired(
        val uid: String,
        val trialStartedAtMillis: Long,
        val trialEndsAtMillis: Long,
        val serverNowMillis: Long
    ) : AccountTrialResult()

    object Disabled : AccountTrialResult()
    object Unauthenticated : AccountTrialResult()
    object Unavailable : AccountTrialResult()
}

interface AccountTrialClient {
    suspend fun initializeOrGetTrial(): AccountTrialResult
}

class FirebaseAccountTrialClient(
    private val endpointUrl: String = CommercialConfig.ACCOUNT_TRIAL_ENDPOINT_URL
) : AccountTrialClient {
    override suspend fun initializeOrGetTrial(): AccountTrialResult = withContext(Dispatchers.IO) {
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
            ?: return@withContext AccountTrialResult.Unauthenticated
        val idToken = runCatching { Tasks.await(user.getIdToken(false)).token }.getOrNull()
            ?: return@withContext AccountTrialResult.Unavailable

        try {
            val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write("{}") }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w("AccountTrialClient", "Trial endpoint returned HTTP ${connection.responseCode}")
                return@withContext AccountTrialResult.Unavailable
            }

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val status = json.optString("status")
            if (status == "DISABLED") {
                return@withContext AccountTrialResult.Disabled
            }

            val startedAt = json.optLong("trialStartedAtMillis", Long.MIN_VALUE)
            val endsAt = json.optLong("trialEndsAtMillis", Long.MIN_VALUE)
            val serverNow = json.optLong("serverNowMillis", Long.MIN_VALUE)
            if (startedAt <= 0L || endsAt <= startedAt || serverNow <= 0L) {
                return@withContext AccountTrialResult.Unavailable
            }

            when (status) {
                "ACTIVE" -> AccountTrialResult.Active(user.uid, startedAt, endsAt, serverNow)
                "EXPIRED" -> AccountTrialResult.Expired(user.uid, startedAt, endsAt, serverNow)
                else -> AccountTrialResult.Unavailable
            }
        } catch (error: Exception) {
            Log.w("AccountTrialClient", "Trial endpoint unavailable", error)
            AccountTrialResult.Unavailable
        }
    }
}
