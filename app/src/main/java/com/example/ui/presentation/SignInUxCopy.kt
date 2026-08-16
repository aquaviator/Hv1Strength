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
        listOf("Export its data", "Sign out")
    )
    AuthErrorKind.NETWORK -> SignInDialogCopy(
        "Connection required for first sign-in",
        "Sign in to start your one-month Human V1 trial. After setup, your workouts are saved on this phone and synchronize automatically when you reconnect.",
        listOf("Sign in with Google", "Try again")
    )
    AuthErrorKind.APP_CHECK -> SignInDialogCopy("We couldn’t finish signing in", "Your saved data has not been changed. Please try again or sign out.", listOf("Try again", "Sign out"))
    AuthErrorKind.TRUSTED_IDENTITY -> SignInDialogCopy("We couldn’t finish signing in", "Your saved data has not been changed. Please try again or sign out.", listOf("Try again", "Sign out"))
    AuthErrorKind.UNKNOWN -> SignInDialogCopy(
        "We couldn’t finish signing in",
        "Your saved data has not been changed. Please try again or sign out.",
        listOf("Try again", "Sign out")
    )
}
