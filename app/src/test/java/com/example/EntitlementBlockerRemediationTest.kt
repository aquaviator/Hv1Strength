package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.billing.CommercialConfig
import com.example.billing.PlayEntitlementVerificationClient
import com.example.billing.VerificationResult
import com.example.billing.AppAccessState
import com.example.ui.screens.retryAccessVerification
import com.example.ui.screens.subscriptionAccessContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EntitlementBlockerRemediationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun configuredAndUnavailableFirebasePathsAreSafe() {
        assertTrue(HumanStrengthApplication.determineFirebaseAvailability { true })
        assertFalse(HumanStrengthApplication.determineFirebaseAvailability { error("missing config") })
    }

    @Test
    fun authoritativeBackendResponseStillProducesPaidEntitlement() = runBlocking {
        val response = """
            {
              "productId":"${CommercialConfig.PRODUCT_ID_ANNUAL}",
              "status":"ACTIVE",
              "expiryTimestampMillis":1893456000000,
              "autoRenewEnabled":true,
              "verificationTimestampMillis":1767225600000,
              "source":"GOOGLE_PLAY_BACKEND"
            }
        """.trimIndent()
        val client = clientReturning(200, response)

        val result = client.verifyPurchase("real-token", CommercialConfig.PRODUCT_ID_ANNUAL, null)

        assertTrue(result is VerificationResult.Success)
    }

    @Test
    fun transportFailureAndTestLookingTokensDoNotManufacturePaidAccess() = runBlocking {
        var connectionAttempts = 0
        val client = PlayEntitlementVerificationClient(context, connectionFactory = {
            connectionAttempts += 1
            throw java.io.IOException("offline")
        })

        val ordinary = client.verifyPurchase("real-token", CommercialConfig.PRODUCT_ID_ANNUAL, null)
        val tokenPrefix = client.verifyPurchase("token_fake", CommercialConfig.PRODUCT_ID_ANNUAL, null)
        val testPrefix = client.verifyPurchase("test_fake", CommercialConfig.PRODUCT_ID_ANNUAL, null)

        assertTrue(ordinary is VerificationResult.NetworkError)
        assertTrue(tokenPrefix is VerificationResult.NetworkError)
        assertTrue(testPrefix is VerificationResult.NetworkError)
        assertEquals(3, connectionAttempts)
    }

    @Test
    fun httpFailureAndMalformedSuccessDoNotManufacturePaidAccess() = runBlocking {
        val httpFailure = clientReturning(503, "")
            .verifyPurchase("real-token", CommercialConfig.PRODUCT_ID_ANNUAL, null)
        val malformed = clientReturning(200, """{"status":"ACTIVE"}""")
            .verifyPurchase("real-token", CommercialConfig.PRODUCT_ID_ANNUAL, null)

        assertFalse(httpFailure is VerificationResult.Success)
        assertFalse(malformed is VerificationResult.Success)
    }

    @Test
    fun unavailableAndExpiredAccessCopyRemainDistinctAndRetryRefreshes() {
        val unavailable = subscriptionAccessContent(AppAccessState.VerificationUnavailable)
        val expired = subscriptionAccessContent(AppAccessState.Expired)
        var retries = 0

        retryAccessVerification { retries += 1 }

        assertFalse(unavailable.showsPurchaseRequirement)
        assertFalse(unavailable.title.contains("UNLOCK"))
        assertFalse(unavailable.title.contains("TRIAL HAS ENDED"))
        assertTrue(expired.showsPurchaseRequirement)
        assertEquals("YOUR TRIAL HAS ENDED", expired.title)
        assertEquals(1, retries)
    }

    private fun clientReturning(responseCode: Int, body: String) =
        PlayEntitlementVerificationClient(
            context = context,
            connectionFactory = { url -> FakeHttpURLConnection(url, responseCode, body) }
        )

    private class FakeHttpURLConnection(
        url: URL,
        private val code: Int,
        private val body: String
    ) : HttpURLConnection(url) {
        private val requestBody = ByteArrayOutputStream()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getOutputStream() = requestBody
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(body.toByteArray())
    }
}
