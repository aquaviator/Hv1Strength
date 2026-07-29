package com.example.billing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

sealed class VerificationResult {
    data class Success(val entitlement: VerifiedEntitlement) : VerificationResult()
    data class Failed(val reason: String) : VerificationResult()
    object NetworkError : VerificationResult()
}

/**
 * Backend verification boundary for Google Play subscription purchases.
 * Communicates with the hv1-platform Google Cloud / Firebase environment (Project 596361666131).
 */
interface EntitlementVerificationClient {
    suspend fun verifyPurchase(
        purchaseToken: String,
        productId: String,
        orderId: String?
    ): VerificationResult
}

/**
 * Default implementation of the verification client boundary.
 * Sends purchase parameters to the secure hv1-platform Cloud Function verification service
 * (`https://europe-west1-hv1-platform.cloudfunctions.net/verifyPurchase`), which holds Google Play Developer API credentials.
 */
class PlayEntitlementVerificationClient(
    private val context: Context,
    private val endpointUrl: String = CommercialConfig.VERIFICATION_ENDPOINT_URL,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : EntitlementVerificationClient {

    private val TAG = "EntitlementVerification"

    private fun safeLogI(tag: String, msg: String) {
        runCatching { Log.i(tag, msg) }
    }

    private fun safeLogW(tag: String, msg: String, tr: Throwable? = null) {
        runCatching {
            if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        }
    }

    override suspend fun verifyPurchase(
        purchaseToken: String,
        productId: String,
        orderId: String?
    ): VerificationResult = withContext(Dispatchers.IO) {
        if (purchaseToken.isBlank() || productId.isBlank()) {
            return@withContext VerificationResult.Failed("Invalid purchase token or product ID")
        }

        safeLogI(TAG, "Submitting purchase token to hv1-platform verification endpoint: $endpointUrl")

        try {
            val url = URL(endpointUrl)
            val conn = connectionFactory(url).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
            }

            val bodyJson = JSONObject().apply {
                put("purchaseToken", purchaseToken)
                put("productId", productId)
                put("packageName", CommercialConfig.PACKAGE_NAME)
                if (orderId != null) put("orderId", orderId)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(bodyJson.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val verifiedEntitlement = parseVerifiedEntitlement(responseText, productId)
                    ?: return@withContext VerificationResult.Failed("Malformed backend entitlement response")
                safeLogI(TAG, "Backend verification response successful: status=${verifiedEntitlement.status}")
                return@withContext VerificationResult.Success(verifiedEntitlement)
            } else if (responseCode in 400..499) {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                safeLogW(TAG, "Backend rejected verification request ($responseCode): $errorText")
                return@withContext VerificationResult.Failed("Verification failed ($responseCode): $errorText")
            } else {
                safeLogW(TAG, "Backend server returned HTTP $responseCode")
                return@withContext VerificationResult.NetworkError
            }
        } catch (e: Exception) {
            safeLogW(TAG, "Network or HTTP exception during backend verification", e)
            return@withContext VerificationResult.NetworkError
        }
    }

    private fun parseVerifiedEntitlement(responseText: String, expectedProductId: String): VerifiedEntitlement? {
        return runCatching {
            val json = JSONObject(responseText)
            val productId = json.getString("productId")
            val status = json.getString("status")
            val expiryTimestampMillis = json.getLong("expiryTimestampMillis")
            val autoRenewEnabled = json.getBoolean("autoRenewEnabled")
            val verificationTimestampMillis = json.getLong("verificationTimestampMillis")
            val source = json.getString("source")
            val allowedStatuses = setOf(
                "ACTIVE", "TRIAL_ACTIVE", "CANCELLED_ACTIVE", "GRACE_PERIOD",
                "ACCOUNT_HOLD", "PAUSED", "EXPIRED", "REVOKED", "PENDING"
            )
            require(productId == expectedProductId)
            require(status in allowedStatuses)
            require(expiryTimestampMillis > 0L)
            require(verificationTimestampMillis > 0L)
            require(source == "GOOGLE_PLAY_BACKEND")
            VerifiedEntitlement(
                productId = productId,
                status = status,
                expiryTimestampMillis = expiryTimestampMillis,
                autoRenewEnabled = autoRenewEnabled,
                verificationTimestampMillis = verificationTimestampMillis,
                source = source
            )
        }.getOrNull()
    }
}

