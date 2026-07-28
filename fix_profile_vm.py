with open("app/src/main/java/com/example/ui/viewmodel/ProfileViewModel.kt", "r") as f:
    content = f.read()

old_val = "val entitlementRepository: com.example.billing.EntitlementRepository = com.example.billing.PlayEntitlementRepository(context, billingRepository, repository)"
new_val = "val entitlementRepository: com.example.billing.EntitlementRepository = com.example.billing.PlayEntitlementRepository(context, billingRepository, repository, authViewModel.authRepository)"

content = content.replace(old_val, new_val)

with open("app/src/main/java/com/example/ui/viewmodel/ProfileViewModel.kt", "w") as f:
    f.write(content)
