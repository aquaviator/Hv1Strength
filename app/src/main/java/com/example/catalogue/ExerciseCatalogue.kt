package com.example.catalogue

import android.content.Context
import com.example.data.Exercise
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer

enum class MeasurementCapability(val wireName: String) {
    REPETITIONS("repetitions"), LOAD("load"), DURATION("duration"), DISTANCE("distance"),
    BODYWEIGHT("bodyweight"), ASSISTED_LOAD("assisted_load"), WEIGHTED_BODYWEIGHT("weighted_bodyweight"),
    RPE("rpe"), TEMPO("tempo")
}

data class CatalogueMetadata(val contractVersion: Int, val catalogueVersion: String, val releaseChannel: String,
    val releasedAt: String, val sourceId: String, val exerciseCount: Int, val payloadChecksum: String)

data class CatalogueExercise(val id: String, val name: String, val aliases: List<String>, val category: String,
    val primaryMuscles: List<String>, val secondaryMuscles: List<String>, val equipment: List<String>,
    val type: String, val capabilities: Set<MeasurementCapability>, val laterality: String,
    val bodyweight: Boolean, val active: Boolean, val replacementId: String? = null,
    val movementPattern: String = "", val setup: String = "", val steps: List<String> = emptyList(),
    val breathing: String = "", val cues: List<String> = emptyList(), val mistakes: List<String> = emptyList(),
    val safety: String = "", val regressionId: String? = null, val progressionId: String? = null,
    val relatedIds: List<String> = emptyList()) {
    val searchableText = normalize(listOf(name, category, movementPattern) + aliases + primaryMuscles + secondaryMuscles + equipment)
    fun toRoom(now: Long) = Exercise(id, name, category, false, id, "global", now, now, null, 1, "SYNCED")
}

data class CatalogueValidation(val valid: Boolean, val errors: List<String>)
data class CatalogueSnapshot(val metadata: CatalogueMetadata, val exercises: List<CatalogueExercise>,
    val validation: CatalogueValidation, val fallbackActive: Boolean = false)

interface ExerciseLibrarySource {
    val snapshot: CatalogueSnapshot
    fun find(id: String): CatalogueExercise? = snapshot.exercises.firstOrNull { it.id == id }
    fun search(query: String = "", category: String? = null, muscle: String? = null,
        equipment: String? = null, capability: MeasurementCapability? = null): List<CatalogueExercise>
}

class PackagedExerciseLibrarySource private constructor(override val snapshot: CatalogueSnapshot) : ExerciseLibrarySource {
    override fun search(query: String, category: String?, muscle: String?, equipment: String?, capability: MeasurementCapability?) =
        snapshot.exercises.asSequence().filter { it.active }
            .filter { query.isBlank() || it.searchableText.contains(normalize(listOf(query))) }
            .filter { category == null || it.category.equals(category, true) }
            .filter { muscle == null || (it.primaryMuscles + it.secondaryMuscles).any { m -> m.equals(muscle, true) } }
            .filter { equipment == null || it.equipment.any { e -> e.equals(equipment, true) } }
            .filter { capability == null || capability in it.capabilities }
            .sortedWith(compareBy<CatalogueExercise> { it.name.lowercase() }.thenBy { it.id }).toList()

    companion object {
        fun load(context: Context): PackagedExerciseLibrarySource = fromJson(context.assets.open("strength-exercise-catalogue.json").bufferedReader().use { it.readText() })
        fun fromJson(json: String): PackagedExerciseLibrarySource {
            val root = JSONObject(json); val payload = root.getJSONArray("exercises")
            val metadata = CatalogueMetadata(root.getInt("contractVersion"), root.getString("catalogueVersion"),
                root.getString("releaseChannel"), root.getString("releasedAt"), root.getString("sourceId"),
                root.getInt("exerciseCount"), root.getString("payloadChecksum"))
            val exercises = (0 until payload.length()).map { i ->
                val o = payload.getJSONObject(i)
                CatalogueExercise(o.getString("id"), o.getString("name"), o.strings("aliases"), o.getString("category"),
                    o.strings("primaryMuscles"), o.strings("secondaryMuscles"), o.strings("equipment"), o.getString("type"),
                    o.strings("capabilities").mapNotNull { value -> MeasurementCapability.entries.find { it.wireName == value } }.toSet(),
                    o.getString("laterality"), o.getBoolean("bodyweight"), o.getBoolean("active"), o.optString("replacementId").ifBlank { null },
                    o.optString("movementPattern"), o.optString("setup"), o.optStrings("steps"), o.optString("breathing"),
                    o.optStrings("cues"), o.optStrings("mistakes"), o.optString("safety"),
                    o.optString("regressionId").ifBlank { null }, o.optString("progressionId").ifBlank { null }, o.optStrings("relatedIds"))
            }
            val errors = validate(metadata, exercises, sha256(canonicalPayload(json)))
            return PackagedExerciseLibrarySource(CatalogueSnapshot(metadata, exercises, CatalogueValidation(errors.isEmpty(), errors)))
        }

        private fun validate(m: CatalogueMetadata, items: List<CatalogueExercise>, actualChecksum: String): List<String> = buildList {
            if (m.contractVersion != 1) add("Unsupported contract version")
            if (m.catalogueVersion.isBlank()) add("Missing catalogue version")
            if (m.exerciseCount != items.size) add("Exercise count mismatch")
            if (!m.payloadChecksum.equals(actualChecksum, true)) add("Checksum mismatch: $actualChecksum")
            if (items.map { it.id }.distinct().size != items.size) add("Duplicate exercise IDs")
            if (items.any { !it.id.matches(Regex("[a-z][a-z0-9_]{2,63}")) || it.name.isBlank() }) add("Malformed exercise identity")
            if (items.map { normalize(listOf(it.name)) }.distinct().size != items.size) add("Duplicate canonical exercise names")
            if (items.any { item -> item.aliases.any { it.isBlank() } || item.aliases.map { normalize(listOf(it)) }.distinct().size != item.aliases.size }) add("Invalid aliases")
            if (items.any { it.type !in setOf("strength", "bodyweight", "conditioning", "cardio") }) add("Invalid exercise type")
            if (items.any { it.laterality !in setOf("bilateral", "unilateral") }) add("Invalid laterality")
            if (items.any { it.capabilities.isEmpty() }) add("Exercise without measurement capabilities")
            if (m.sourceId == "human-v1-strength" && items.any { it.movementPattern.isBlank() || it.setup.isBlank() || it.steps.isEmpty() || it.breathing.isBlank() || it.cues.isEmpty() || it.mistakes.isEmpty() || it.safety.isBlank() }) add("Exercise without required detail content")
            if (items.any { (MeasurementCapability.ASSISTED_LOAD in it.capabilities || MeasurementCapability.WEIGHTED_BODYWEIGHT in it.capabilities) && MeasurementCapability.BODYWEIGHT !in it.capabilities }) add("Bodyweight load capability requires bodyweight")
            val ids = items.map { it.id }.toSet()
            if (items.any { it.replacementId != null && it.replacementId !in ids }) add("Invalid replacement ID")
            if (items.any { item -> item.regressionId?.let { it !in ids || it == item.id } == true || item.progressionId?.let { it !in ids || it == item.id } == true || item.relatedIds.any { it !in ids || it == item.id } || item.relatedIds.distinct().size != item.relatedIds.size }) add("Invalid related exercise reference")
            items.forEach { start -> var next = start.replacementId; val seen = mutableSetOf(start.id); while (next != null) {
                if (!seen.add(next)) { add("Replacement cycle"); break }; next = items.firstOrNull { it.id == next }?.replacementId
            }}
        }
        private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun canonicalPayload(json: String): String {
            val key = json.indexOf("\"exercises\"")
            val start = json.indexOf('[', key)
            require(key >= 0 && start >= 0) { "Missing exercise payload" }
            var depth = 0; var quoted = false; var escaped = false; var end = -1
            for (index in start until json.length) {
                val char = json[index]
                if (quoted) {
                    if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
                } else if (char == '"') quoted = true else if (char == '[') depth++ else if (char == ']') {
                    depth--; if (depth == 0) { end = index; break }
                }
            }
            require(end >= start) { "Malformed exercise payload" }
            val result = StringBuilder(); quoted = false; escaped = false
            for (char in json.substring(start, end + 1)) {
                if (quoted) {
                    result.append(char)
                    if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
                } else if (char == '"') { quoted = true; result.append(char) } else if (!char.isWhitespace()) result.append(char)
            }
            return result.toString()
        }
    }
}

private fun JSONObject.strings(name: String) = getJSONArray(name).let { array -> (0 until array.length()).map(array::getString) }
private fun JSONObject.optStrings(name: String) = optJSONArray(name)?.let { array -> (0 until array.length()).map(array::getString) } ?: emptyList()
fun normalize(parts: List<String>): String = Normalizer.normalize(parts.joinToString(" "), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase()
    .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

object ExerciseCatalogueRuntime {
    @Volatile var snapshot: CatalogueSnapshot? = null
        private set
    fun load(context: Context): CatalogueSnapshot = try {
        PackagedExerciseLibrarySource.load(context).snapshot.also { snapshot = it }
    } catch (error: Throwable) {
        fallback(error.message ?: "Package could not be read").also { snapshot = it }
    }
    fun fallback(reason: String) = CatalogueSnapshot(CatalogueMetadata(1, "fallback-1", "embedded", "", "safe-fallback", 3, ""),
        listOf(
            CatalogueExercise("bench_press", "Bench Press", emptyList(), "Chest", listOf("chest"), emptyList(), listOf("barbell"), "strength", setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.LOAD), "bilateral", false, true),
            CatalogueExercise("squat", "Barbell Squat", listOf("squat"), "Legs", listOf("quadriceps"), listOf("glutes"), listOf("barbell"), "strength", setOf(MeasurementCapability.REPETITIONS, MeasurementCapability.LOAD), "bilateral", false, true),
            CatalogueExercise("plank", "Plank", emptyList(), "Abs", listOf("core"), emptyList(), listOf("bodyweight"), "bodyweight", setOf(MeasurementCapability.DURATION), "bilateral", true, true)),
        CatalogueValidation(false, listOf(reason)), true)
}

data class LibraryItem(val exercise: Exercise, val catalogue: CatalogueExercise?)
data class LibraryOverlay(val items: List<LibraryItem>, val rejectedCustomCollisions: List<String>)

fun overlayLibrary(governed: List<CatalogueExercise>, roomExercises: List<Exercise>): LibraryOverlay {
    val governedById = governed.associateBy { it.id }
    val collisions = roomExercises.filter { it.isCustom && it.id in governedById }.map { it.id }
    val custom = roomExercises.filter { it.isCustom && it.id !in governedById }.map { LibraryItem(it, null) }
    val builtIns = governed.map { LibraryItem(it.toRoom(0), it) }
    return LibraryOverlay((builtIns + custom).sortedWith(compareBy<LibraryItem> { it.exercise.name.lowercase() }.thenBy { it.exercise.id }), collisions)
}

fun exerciseMatches(exercise: Exercise, query: String, governed: CatalogueExercise?): Boolean {
    if (query.isBlank()) return true
    return (governed?.searchableText ?: normalize(listOf(exercise.name, exercise.category)))
        .contains(normalize(listOf(query)))
}
