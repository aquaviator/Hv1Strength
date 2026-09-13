package com.example

import com.example.billing.*
import org.junit.Assert.*
import org.junit.Test

class Billing91MigrationPolicyTest {
    private fun offer(base: String, id: String?, token: String, free: Boolean = false) =
        BillingOfferCandidate(base, id, token, free)

    @Test fun `deterministic selection prefers eligible trial then stable identifiers`() {
        val selected = BillingPolicy.selectOffer(listOf(offer("z", "b", "3", true), offer("a", "b", "2", true), offer("a", "a", "1", true)))
        assertEquals("1", selected?.offerToken)
    }

    @Test fun `selection rejects missing and blank offer tokens`() {
        assertNull(BillingPolicy.selectOffer(listOf(offer("base", null, ""), offer("base", null, "  "))))
    }

    @Test fun `selection falls back deterministically when no trial exists`() {
        assertEquals("a", BillingPolicy.selectOffer(listOf(offer("b", null, "b"), offer("a", null, "a")))?.offerToken)
    }

    @Test fun `purchased response remains unverified locally`() {
        assertEquals(LocalPurchaseDisposition.PURCHASED_UNVERIFIED, BillingPolicy.classifyPurchase(listOf(CommercialConfig.PRODUCT_ID_ANNUAL), 1, false))
    }

    @Test fun `pending purchase never becomes purchased`() {
        assertEquals(LocalPurchaseDisposition.PENDING, BillingPolicy.classifyPurchase(listOf(CommercialConfig.PRODUCT_ID_ANNUAL), 2, false))
    }

    @Test fun `suspended subscription fails closed`() {
        assertEquals(LocalPurchaseDisposition.SUSPENDED, BillingPolicy.classifyPurchase(listOf(CommercialConfig.PRODUCT_ID_ANNUAL), 1, true))
    }

    @Test fun `wrong product is rejected`() {
        assertEquals(LocalPurchaseDisposition.REJECTED, BillingPolicy.classifyPurchase(listOf("wrong_product"), 1, false))
    }

    @Test fun `malformed empty product list is rejected`() {
        assertEquals(LocalPurchaseDisposition.REJECTED, BillingPolicy.classifyPurchase(emptyList(), 1, false))
    }

    @Test fun `unspecified cancelled or expired state is rejected`() {
        assertEquals(LocalPurchaseDisposition.REJECTED, BillingPolicy.classifyPurchase(listOf(CommercialConfig.PRODUCT_ID_ANNUAL), 0, false))
    }

    @Test fun `user cancellation remains non-authoritative`() = assertEquals(
        BillingResponseDisposition.CANCELLED, BillingPolicy.classifyResponse(1))

    @Test fun `already owned requests a governed restore query`() = assertEquals(
        BillingResponseDisposition.REQUERY_OWNED, BillingPolicy.classifyResponse(7))

    @Test fun `billing unavailable including blocked Play Store fails closed`() = assertEquals(
        BillingResponseDisposition.UNAVAILABLE, BillingPolicy.classifyResponse(3))

    @Test fun `network and service disconnection results remain retryable errors`() {
        assertEquals(BillingResponseDisposition.RETRYABLE_ERROR, BillingPolicy.classifyResponse(2))
        assertEquals(BillingResponseDisposition.RETRYABLE_ERROR, BillingPolicy.classifyResponse(12))
    }

    @Test fun `success response does not itself grant entitlement`() {
        assertEquals(BillingResponseDisposition.SUCCESS, BillingPolicy.classifyResponse(0))
        assertFalse(AppAccessState.Unentitled.hasAppAccess)
    }
}
