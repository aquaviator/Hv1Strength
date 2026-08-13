package com.example.billing

data class AccessDependencies(
    val billingRepository: BillingRepository,
    val entitlementRepository: EntitlementRepository
)
