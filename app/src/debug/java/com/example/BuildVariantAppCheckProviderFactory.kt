package com.example

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object BuildVariantAppCheckProviderFactory {
    fun create(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
