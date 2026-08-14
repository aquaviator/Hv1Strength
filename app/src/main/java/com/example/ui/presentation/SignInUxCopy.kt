package com.example.ui.presentation

import com.example.data.AuthErrorKind

data class SignInDialogCopy(val title: String, val message: String, val actions: List<String>)

fun signInDialogCopy(kind: AuthErrorKind, fallback: String): SignInDialogCopy = when (kind) {
    AuthErrorKind.DATA_CONFLICT -> SignInDialogCopy(
        "Some saved items need your attention",
        "This phone and your Human V1 online data contain different versions of the same routines or custom exercises. Nothing has been overwritten. Your workouts and history are safe.",
        listOf("Review differences", "Use offline for now", "Sign out")
    )
    AuthErrorKind.DIFFERENT_ACCOUNT -> SignInDialogCopy(
        "Workouts from another profile were found on this phone",
        "To protect them, they have not been connected to your signed-in account or uploaded.",
        listOf("Use this data offline", "Export the data", "Sign out")
    )
    AuthErrorKind.NETWORK -> SignInDialogCopy(
        "We couldn’t finish signing in",
        "Check your connection and try again. Your saved workouts remain on this phone.",
        listOf("Try again", "Use offline", "Sign out")
    )
    AuthErrorKind.APP_CHECK -> SignInDialogCopy("Device verification unavailable", fallback, listOf("Try again", "Use offline", "Sign out"))
    AuthErrorKind.TRUSTED_IDENTITY -> SignInDialogCopy("Account verification incomplete", fallback, listOf("Try again", "Sign out"))
    AuthErrorKind.UNKNOWN -> SignInDialogCopy("Sign-in could not finish", fallback, listOf("Try again", "Sign out"))
}
