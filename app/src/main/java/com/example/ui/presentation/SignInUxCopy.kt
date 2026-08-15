package com.example.ui.presentation

import com.example.data.AuthErrorKind

data class SignInDialogCopy(val title: String, val message: String, val actions: List<String>)

fun signInDialogCopy(kind: AuthErrorKind, fallback: String): SignInDialogCopy = when (kind) {
    AuthErrorKind.DATA_CONFLICT -> SignInDialogCopy(
        "Some items need review",
        "This phone and your Human V1 online data contain different versions of the same routines or custom exercises. Nothing has been overwritten.",
        listOf("Review items", "Continue", "Sign out")
    )
    AuthErrorKind.DIFFERENT_ACCOUNT -> SignInDialogCopy(
        "Workouts from another profile are saved on this phone",
        "They will not be connected to or uploaded into the account you just signed in with.",
        listOf("Open the local profile", "Export its data", "Sign out")
    )
    AuthErrorKind.NETWORK -> SignInDialogCopy(
        "Connection needed to sign in",
        "Connect to the internet to sign in with Google. You can still start without an account.",
        listOf("Try again", "Continue without an account", "Cancel")
    )
    AuthErrorKind.APP_CHECK -> SignInDialogCopy("Device verification unavailable", fallback, listOf("Try again", "Continue without an account", "Cancel"))
    AuthErrorKind.TRUSTED_IDENTITY -> SignInDialogCopy("Account verification incomplete", fallback, listOf("Try again", "Sign out"))
    AuthErrorKind.UNKNOWN -> SignInDialogCopy("Sign-in could not finish", fallback, listOf("Try again", "Sign out"))
}
