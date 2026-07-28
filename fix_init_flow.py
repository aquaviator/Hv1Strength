with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

old_init = """    init {
        externalScope.launch {
            combine(
                billingRepository.subscriptionState,
                authRepository.authState,
                _cachedEntitlement
            ) { subState, authState, cached ->
                val userProfile = (authState as? AuthState.Authenticated)?.profile
                resolveAccessState(subState, userProfile, cached)
            }.collect { newState ->
                _appAccessState.value = newState
            }
        }
    }"""

new_init = """    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    init {
        externalScope.launch {
            val userProfileFlow = authRepository.authState.flatMapLatest { state ->
                val userId = when (state) {
                    is AuthState.Authenticated -> state.profile.id
                    is AuthState.Offline -> "offline"
                    else -> "offline"
                }
                repository.getUserProfileFlow(userId)
            }

            combine(
                billingRepository.subscriptionState,
                userProfileFlow,
                _cachedEntitlement
            ) { subState, userProfile, cached ->
                resolveAccessState(subState, userProfile, cached)
            }.collect { newState ->
                _appAccessState.value = newState
            }
        }
    }"""

content = content.replace(old_init, new_init)

old_refresh = """    override fun refreshAccessState() {
        externalScope.launch {
            val userProfile = (authRepository.authState.value as? AuthState.Authenticated)?.profile
            val subState = billingRepository.subscriptionState.value
            val cached = _cachedEntitlement.value
            _appAccessState.value = resolveAccessState(subState, userProfile, cached)
        }
    }"""

new_refresh = """    override fun refreshAccessState() {
        externalScope.launch {
            val userId = when (val state = authRepository.authState.value) {
                is AuthState.Authenticated -> state.profile.id
                is AuthState.Offline -> "offline"
                else -> "offline"
            }
            val userProfile = repository.getUserProfile(userId)
            val subState = billingRepository.subscriptionState.value
            val cached = _cachedEntitlement.value
            _appAccessState.value = resolveAccessState(subState, userProfile, cached)
        }
    }"""

content = content.replace(old_refresh, new_refresh)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)
