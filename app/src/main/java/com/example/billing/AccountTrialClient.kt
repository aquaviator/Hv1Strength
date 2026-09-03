package com.example.billing

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed class AccountTrialResult {
    data class SupportActive(
        val uid: String,
        val effectiveAtMillis: Long,
        val expiryAtMillis: Long,
        val historicalTrialEndMillis: Long,
        val serverNowMillis: Long,
        val offlineReceiptValidUntilMillis: Long = expiryAtMillis
    ) : AccountTrialResult()
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
    private val endpointUrl: String = CommercialConfig.ACCOUNT_TRIAL_ENDPOINT_URL,
    private val firestore: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
) : AccountTrialClient {
    override suspend fun initializeOrGetTrial(): AccountTrialResult = withContext(Dispatchers.IO) {
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
            ?: return@withContext AccountTrialResult.Unauthenticated
        val idToken = runCatching { Tasks.await(user.getIdToken(false)).token }.getOrNull()
            ?: return@withContext AccountTrialResult.Unavailable

        // A fresh server projection is authoritative. Its offlineReceiptValidUntil
        // limits cached/offline use only; it must not invalidate this live server read.
        val projection = runCatching {
            firestore?.let {
                Tasks.await(it.collection("accounts").document(user.uid)
                    .collection("entitlements").document("current").get(Source.SERVER))
            }
        }.getOrNull()
        parseActiveStrengthSupportProjection(user.uid, projection?.data, System.currentTimeMillis())
            ?.let { return@withContext it }

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
            if (status == "SUPPORT_ACTIVE") {
                val effective = json.optLong("supportEffectiveAtMillis", Long.MIN_VALUE)
                val expiry = json.optLong("supportExpiryAtMillis", Long.MIN_VALUE)
                val historicalEnd = json.optLong("trialEndsAtMillis", Long.MIN_VALUE)
                val serverNow = json.optLong("serverNowMillis", Long.MIN_VALUE)
                val offlineUntil = json.optLong("offlineReceiptValidUntilMillis", serverNow)
                if (effective <= 0L || expiry <= effective || historicalEnd <= 0L || serverNow <= 0L)
                    return@withContext AccountTrialResult.Unavailable
                return@withContext AccountTrialResult.SupportActive(user.uid, effective, expiry, historicalEnd,
                    serverNow, offlineUntil)
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

internal fun parseActiveStrengthSupportProjection(
    uid: String,
    data: Map<String, Any?>?,
    serverObservedAtMillis: Long
): AccountTrialResult.SupportActive? {
    if (data?.get("schemaVersion") != 1L || data["firebaseUid"] != uid) return null
    val products = data["products"] as? Map<*, *> ?: return null
    val strength = products["HUMAN_STRENGTH"] as? Map<*, *> ?: return null
    if (strength["normalizedState"] != "ACTIVE_UNTIL_EXPIRY" || strength["source"] != "SUPPORT") return null
    val effective = (strength["effectiveAt"] as? Timestamp)?.toDate()?.time ?: return null
    val expiry = (strength["expiryAt"] as? Timestamp)?.toDate()?.time ?: return null
    val historicalEnd = (data["introductoryExpiredAt"] as? Timestamp)?.toDate()?.time ?: return null
    val offlineUntil = (strength["offlineReceiptValidUntil"] as? Timestamp)?.toDate()?.time ?: return null
    if (effective <= 0L || expiry <= effective || expiry <= serverObservedAtMillis || historicalEnd <= 0L) return null
    return AccountTrialResult.SupportActive(uid, effective, expiry, historicalEnd, serverObservedAtMillis, offlineUntil)
}
