package com.example.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

internal object BuildVariantAuthDependenciesFactory {
    fun create(context: Context): AuthDependencies = AuthDependencies(
        firebaseAuth = runCatching { FirebaseAuth.getInstance() }.getOrNull(),
        identityClient = FirebaseHumanIdentityClient()
    )
}
