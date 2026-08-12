package com.example.data

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal const val ACTIVE_IDENTITY_STATUS = "ACTIVE"
internal const val SUPPORTED_IDENTITY_SCHEMA_VERSION = 1L
private val HUMAN_ID_PATTERN = Regex("^human_[a-z0-9]{8,64}$")

data class AuthoritativeHumanIdentity(
    val humanUserId: String,
    val status: String,
    val schemaVersion: Long
)

sealed interface HumanIdentityResult {
    data class Success(val identity: AuthoritativeHumanIdentity) : HumanIdentityResult
    data object Unauthenticated : HumanIdentityResult
    data object IdentityUnavailable : HumanIdentityResult
    data object IdentityConflict : HumanIdentityResult
    data object IdentityDisabled : HumanIdentityResult
    data object UnsupportedSchema : HumanIdentityResult
    data object MissingSchema : HumanIdentityResult
    data object NetworkError : HumanIdentityResult
    data object BackendError : HumanIdentityResult
    data object MalformedResponse : HumanIdentityResult
}

internal fun isValidAuthoritativeHumanId(value: String?): Boolean =
    value != null && HUMAN_ID_PATTERN.matches(value)

internal fun parseHumanIdentityResponse(data: Any?): HumanIdentityResult {
    val response = data as? Map<*, *> ?: return HumanIdentityResult.MalformedResponse
    val humanUserId = response["humanUserId"] as? String ?: return HumanIdentityResult.MalformedResponse
    val status = response["status"] as? String ?: return HumanIdentityResult.MalformedResponse
    val schemaVersion = (response["schemaVersion"] as? Number)?.toLong()
        ?: return HumanIdentityResult.MissingSchema
    if (!isValidAuthoritativeHumanId(humanUserId)) return HumanIdentityResult.MalformedResponse
    if (schemaVersion != SUPPORTED_IDENTITY_SCHEMA_VERSION) return HumanIdentityResult.UnsupportedSchema
    if (status != ACTIVE_IDENTITY_STATUS) {
        return if (status in setOf("DISABLED", "DELETION_PENDING", "DELETED")) {
            HumanIdentityResult.IdentityDisabled
        } else HumanIdentityResult.MalformedResponse
    }
    return HumanIdentityResult.Success(AuthoritativeHumanIdentity(humanUserId, status, schemaVersion))
}

internal fun mapHumanIdentityBackendError(code: FirebaseFunctionsException.Code): HumanIdentityResult = when (code) {
    FirebaseFunctionsException.Code.UNAUTHENTICATED -> HumanIdentityResult.Unauthenticated
    FirebaseFunctionsException.Code.FAILED_PRECONDITION,
    FirebaseFunctionsException.Code.ALREADY_EXISTS,
    FirebaseFunctionsException.Code.ABORTED -> HumanIdentityResult.IdentityConflict
    FirebaseFunctionsException.Code.UNAVAILABLE,
    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> HumanIdentityResult.NetworkError
    FirebaseFunctionsException.Code.NOT_FOUND -> HumanIdentityResult.IdentityUnavailable
    else -> HumanIdentityResult.BackendError
}

interface HumanIdentityClient { suspend fun ensureHumanIdentity(): HumanIdentityResult }

internal class FirebaseHumanIdentityClient(
    private val isAuthenticated: () -> Boolean = {
        runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)
    },
    private val callable: suspend () -> Any? = {
        Tasks.await(FirebaseFunctions.getInstance("europe-west1")
            .getHttpsCallable("ensureHumanIdentity").call(emptyMap<String, Any>())).data
    },
    private val timeoutMillis: Long = 10_000L
) : HumanIdentityClient {
    override suspend fun ensureHumanIdentity(): HumanIdentityResult = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.w("TrustedIdentity", "stage=identity result=UNAUTHENTICATED")
            return@withContext HumanIdentityResult.Unauthenticated
        }
        try {
            val result = parseHumanIdentityResponse(withTimeout(timeoutMillis) { callable() })
            Log.i("TrustedIdentity", "stage=identity result=${result.safeResultName()}")
            result
        } catch (_: TimeoutCancellationException) {
            Log.w("TrustedIdentity", "stage=identity result=NETWORK_TIMEOUT")
            HumanIdentityResult.NetworkError
        } catch (error: FirebaseFunctionsException) {
            val result = mapHumanIdentityBackendError(error.code)
            Log.w("TrustedIdentity", "stage=identity result=${result.safeResultName()} functions_code=${error.code.name}")
            result
        } catch (_: java.io.IOException) {
            Log.w("TrustedIdentity", "stage=identity result=NETWORK_ERROR")
            HumanIdentityResult.NetworkError
        } catch (_: Exception) {
            Log.e("TrustedIdentity", "stage=identity result=BACKEND_ERROR")
            HumanIdentityResult.BackendError
        }
    }
}

internal fun HumanIdentityResult.safeMessage(): String = when (this) {
    is HumanIdentityResult.Success -> "Human identity ready"
    HumanIdentityResult.Unauthenticated -> "Firebase authentication is required"
    HumanIdentityResult.IdentityUnavailable -> "Human identity is unavailable"
    HumanIdentityResult.IdentityConflict -> "Human identity binding conflict"
    HumanIdentityResult.IdentityDisabled -> "Human identity is not active"
    HumanIdentityResult.UnsupportedSchema -> "Human identity version is not supported"
    HumanIdentityResult.MissingSchema -> "BACKEND CONTRACT UPDATE REQUIRED: schemaVersion is missing"
    HumanIdentityResult.NetworkError -> "Human identity service is temporarily unavailable"
    HumanIdentityResult.BackendError -> "Human identity service rejected the request"
    HumanIdentityResult.MalformedResponse -> "Human identity response was invalid"
}

internal fun HumanIdentityResult.safeResultName(): String = when (this) {
    is HumanIdentityResult.Success -> "SUCCESS"
    HumanIdentityResult.Unauthenticated -> "UNAUTHENTICATED"
    HumanIdentityResult.IdentityUnavailable -> "CALLABLE_UNAVAILABLE"
    HumanIdentityResult.IdentityConflict -> "IDENTITY_CONFLICT"
    HumanIdentityResult.IdentityDisabled -> "IDENTITY_DISABLED"
    HumanIdentityResult.UnsupportedSchema -> "UNSUPPORTED_SCHEMA"
    HumanIdentityResult.MissingSchema -> "MISSING_SCHEMA"
    HumanIdentityResult.NetworkError -> "NETWORK_ERROR"
    HumanIdentityResult.BackendError -> "BACKEND_REJECTION"
    HumanIdentityResult.MalformedResponse -> "MALFORMED_RESPONSE"
}
