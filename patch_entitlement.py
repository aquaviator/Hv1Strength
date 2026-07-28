import re

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

# Replace constructor
old_constructor = """class PlayEntitlementRepository(
    private val context: Context,
    private val billingRepository: BillingRepository,
    private val repository: StrengthRepository,
    private val verificationClient: EntitlementVerificationClient = PlayEntitlementVerificationClient(context),
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : EntitlementRepository {"""

new_constructor = """import com.example.data.AuthRepository
import com.example.data.AuthState

class PlayEntitlementRepository(
    private val context: Context,
    private val billingRepository: BillingRepository,
    private val repository: StrengthRepository,
    private val authRepository: AuthRepository,
    private val verificationClient: EntitlementVerificationClient = PlayEntitlementVerificationClient(context),
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : EntitlementRepository {"""

content = content.replace(old_constructor, new_constructor)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)
