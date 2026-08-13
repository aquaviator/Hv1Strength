package com.example.billing

import android.content.Context
import com.example.data.StrengthRepository

internal object BuildVariantAccessDependenciesFactory {
    fun create(context: Context, repository: StrengthRepository): AccessDependencies {
        val billing = PlayBillingRepository(context)
        return AccessDependencies(billing, PlayEntitlementRepository(context, billing, repository))
    }
}
