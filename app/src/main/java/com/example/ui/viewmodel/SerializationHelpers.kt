package com.example.ui.viewmodel

fun serializeExerciseIdsList(ids: List<String>): String {
    return "[" + ids.joinToString(",") { "\"$it\"" } + "]"
}

fun deserializeExerciseIdsList(json: String): List<String> {
    return json.trim('[', ']')
        .split(',')
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
}
