package com.example.data

import android.content.Context

internal object BuildVariantSyncIdentityFactory {
    suspend fun resolve(context: Context, repository: StrengthRepository): com.example.core.sync.SyncIdentityResolution? = null
}
