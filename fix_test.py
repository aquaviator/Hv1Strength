with open("app/src/test/java/com/example/Candidate70PhaseDEntitlementTest.kt", "r") as f:
    content = f.read()

old_call = """        val entitlementRepository = PlayEntitlementRepository(
            context = context,
            billingRepository = fakeBillingRepository,
            repository = repository
        )"""

new_call = """        val entitlementRepository = PlayEntitlementRepository(
            context = context,
            billingRepository = fakeBillingRepository,
            repository = repository,
            authRepository = authRepository
        )"""

content = content.replace(old_call, new_call)

with open("app/src/test/java/com/example/Candidate70PhaseDEntitlementTest.kt", "w") as f:
    f.write(content)
