package com.example

import com.example.data.UserProfile
import com.example.data.initials
import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileInitialsTest {
    @Test
    fun initialsUseFirstAndLastNames() {
        assertEquals("AC", UserProfile(id = "1", displayName = "Andy Clarke").initials)
    }

    @Test
    fun initialsUseSingleNameSafely() {
        assertEquals("A", UserProfile(id = "1", displayName = "Andy").initials)
    }

    @Test
    fun initialsFallBackToEmailPrefix() {
        assertEquals("LE", UserProfile(id = "1", email = "leatfield@example.com").initials)
    }

    @Test
    fun initialsNeverReturnLegacyPlaceholder() {
        assertEquals("U", UserProfile(id = "1").initials)
    }
}
