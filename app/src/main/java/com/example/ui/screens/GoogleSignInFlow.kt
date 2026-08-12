package com.example.ui.screens

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

internal class GoogleSignInConfigurationException(message: String) : IllegalArgumentException(message)
internal class MalformedGoogleCredentialException : IllegalArgumentException("Google returned an invalid sign-in credential")
internal fun developerGoogleSignInToolsVisible(isDebugBuild: Boolean): Boolean = isDebugBuild

internal fun requireGeneratedGoogleServerClientId(serverClientId: String): String {
    val value = serverClientId.trim()
    if (value.isBlank() || !value.endsWith(".apps.googleusercontent.com") || value.substringBefore('.').length < 10) {
        throw GoogleSignInConfigurationException("Google sign-in is not configured for this build")
    }
    return value
}

internal fun buildExplicitGoogleSignInRequest(serverClientId: String): GetCredentialRequest =
    GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(requireGeneratedGoogleServerClientId(serverClientId)).build())
        .build()

internal fun parseGoogleIdTokenCredential(credential: Credential): GoogleIdTokenCredential {
    if (credential !is CustomCredential || credential.type !in setOf(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
        )) throw MalformedGoogleCredentialException()
    return try {
        GoogleIdTokenCredential.createFrom(credential.data).also {
            if (it.idToken.isBlank()) throw MalformedGoogleCredentialException()
        }
    } catch (_: MalformedGoogleCredentialException) {
        throw MalformedGoogleCredentialException()
    } catch (_: Exception) {
        throw MalformedGoogleCredentialException()
    }
}

internal suspend fun <T> runExplicitGoogleSignIn(
    serverClientId: String,
    requestCredential: suspend (GetCredentialRequest) -> T,
    exchangeCredential: suspend (T) -> Unit
) {
    exchangeCredential(requestCredential(buildExplicitGoogleSignInRequest(serverClientId)))
}

internal suspend fun requestExplicitGoogleCredential(context: Context, serverClientId: String): GoogleIdTokenCredential {
    val response = CredentialManager.create(context).getCredential(context, buildExplicitGoogleSignInRequest(serverClientId))
    return parseGoogleIdTokenCredential(response.credential)
}
