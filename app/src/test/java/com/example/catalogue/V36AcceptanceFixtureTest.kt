package com.example.catalogue

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class V36AcceptanceFixtureTest {
    @Test
    fun fixtureReceiptSpecHasGovernedChecksum() {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(V36CatalogueAcceptanceController.FIXTURE_SPEC.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(V36CatalogueAcceptanceController.FIXTURE_CHECKSUM, actual)
    }
}
