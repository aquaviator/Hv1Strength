package com.example

import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.ui.screens.*
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class GoogleSignInFlowRepairTest {
    private val clientId = "123456789012-example.apps.googleusercontent.com"

    @Test fun explicitRequestUsesSignInWithGoogleOption() {
        val option=buildExplicitGoogleSignInRequest(clientId).credentialOptions.single()
        assertTrue(option is GetSignInWithGoogleOption); assertEquals(clientId,(option as GetSignInWithGoogleOption).serverClientId)
    }
    @Test fun generatedServerClientIdIsRequired() {
        listOf("", "not-an-oauth-client", "x.apps.googleusercontent.com").forEach { value ->
            assertTrue(runCatching { buildExplicitGoogleSignInRequest(value) }.exceptionOrNull() is GoogleSignInConfigurationException)
        }
    }
    @Test fun unexpectedCredentialIsRejected() {
        assertTrue(runCatching { parseGoogleIdTokenCredential(PasswordCredential("id","password")) }.exceptionOrNull() is MalformedGoogleCredentialException)
    }
    @Test fun malformedGoogleTokenIsRejected() {
        val malformed=androidx.credentials.CustomCredential(com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, android.os.Bundle())
        assertTrue(runCatching { parseGoogleIdTokenCredential(malformed) }.exceptionOrNull() is MalformedGoogleCredentialException)
    }
    @Test fun cancellationIsDistinctAndNeverExchanged()=runTest {
        val expected=GetCredentialCancellationException("cancelled"); var exchanged=false
        val actual=runCatching { runExplicitGoogleSignIn(clientId,{throw expected},{exchanged=true}) }.exceptionOrNull()
        assertSame(expected,actual); assertFalse(exchanged)
    }
    @Test fun noCredentialIsDistinctAndNeverExchanged()=runTest {
        val expected=NoCredentialException("no account"); var exchanged=false
        val actual=runCatching { runExplicitGoogleSignIn(clientId,{throw expected},{exchanged=true}) }.exceptionOrNull()
        assertSame(expected,actual); assertFalse(exchanged)
    }
    @Test fun successfulProviderResultReachesFirebaseBoundary()=runTest {
        var token:String?=null; runExplicitGoogleSignIn(clientId,{"id-token"},{token=it}); assertEquals("id-token",token)
    }
    @Test fun releaseUiContainsNoSimulationOrClientOverrideTools() = assertFalse(developerGoogleSignInToolsVisible(false))
}
