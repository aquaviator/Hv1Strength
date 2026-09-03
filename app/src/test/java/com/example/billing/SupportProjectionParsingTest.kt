package com.example.billing

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class SupportProjectionParsingTest {
    private val uid = "uid_test"
    private val now = 1_800_000_000_000L
    private fun projection(state: String = "ACTIVE_UNTIL_EXPIRY", source: String = "SUPPORT",
                           expiry: Long = now + 10_000, offlineUntil: Long = now - 1) = mapOf<String, Any?>(
        "schemaVersion" to 1L, "firebaseUid" to uid,
        "introductoryExpiredAt" to Timestamp(Date(now - 100_000)),
        "products" to mapOf("HUMAN_STRENGTH" to mapOf(
            "normalizedState" to state, "source" to source,
            "effectiveAt" to Timestamp(Date(now - 1_000)), "expiryAt" to Timestamp(Date(expiry)),
            "offlineReceiptValidUntil" to Timestamp(Date(offlineUntil))
        ))
    )

    @Test fun `fresh active server projection ignores expired offline receipt`() {
        val result = parseActiveStrengthSupportProjection(uid, projection(), now)
        assertTrue(result is AccountTrialResult.SupportActive)
        assertEquals(now + 10_000, result?.expiryAtMillis)
        assertEquals(now - 1, result?.offlineReceiptValidUntilMillis)
    }

    @Test fun `expired revoked missing malformed and identity mismatch fail closed`() {
        assertNull(parseActiveStrengthSupportProjection(uid, projection(expiry = now), now))
        assertNull(parseActiveStrengthSupportProjection(uid, projection(state = "REVOKED"), now))
        assertNull(parseActiveStrengthSupportProjection(uid, null, now))
        assertNull(parseActiveStrengthSupportProjection(uid, projection().minus("products"), now))
        assertNull(parseActiveStrengthSupportProjection("other", projection(), now))
    }

    @Test fun `server projection preserves expired introductory history without paid claim`() {
        val result = parseActiveStrengthSupportProjection(uid, projection(offlineUntil = now + 5_000), now)
        assertEquals(now - 100_000, result?.historicalTrialEndMillis)
        assertEquals(now + 5_000, result?.offlineReceiptValidUntilMillis)
        assertTrue(result is AccountTrialResult.SupportActive)
    }
}
