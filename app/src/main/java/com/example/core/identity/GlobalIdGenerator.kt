package com.example.core.identity

import java.util.UUID

object GlobalIdGenerator {
    fun generate(prefix: String): String {
        val randomPart = UUID.randomUUID().toString().replace("-", "").lowercase().take(12)
        return "${prefix}_$randomPart"
    }
}
