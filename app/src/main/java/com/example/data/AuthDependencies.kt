package com.example.data

import com.google.firebase.auth.FirebaseAuth

data class AuthDependencies(
    val firebaseAuth: FirebaseAuth?,
    val identityClient: HumanIdentityClient
)
