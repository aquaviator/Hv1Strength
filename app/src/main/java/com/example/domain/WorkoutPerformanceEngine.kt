package com.example.domain

import com.example.catalogue.MeasurementCapability

data class PerformanceSet(
    val stableId: String,
    val sessionId: String,
    val exerciseId: String,
    val profileId: String,
    val sessionEndedAt: Long,
    val setNumber: Int,
    val setType: String = "WORKING",
    val completed: Boolean = true,
    val deleted: Boolean = false,
    val loadKg: Double? = null,
    val repetitions: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMetres: Double? = null,
    val rpe: Int? = null,
    val assistanceKg: Double? = null,
    val addedWeightKg: Double? = null
)

data class PerformanceBest(
    val heaviestLoadKg: Double? = null,
    val mostRepetitions: Int? = null,
    val highestSetVolumeKg: Double? = null,
    val estimatedOneRepMaxKg: Double? = null,
    val longestDurationSeconds: Int? = null,
    val greatestDistanceMetres: Double? = null,
    val fastestPaceSecondsPerKm: Double? = null,
    val bestAddedWeightKg: Double? = null,
    val leastAssistanceKg: Double? = null
)

enum class RecordResult { NEW, MATCHED, NONE }
data class PersonalRecords(val results: Map<String, RecordResult>) {
    val newRecords get() = results.filterValues { it == RecordResult.NEW }.keys
    val matchedRecords get() = results.filterValues { it == RecordResult.MATCHED }.keys
}

data class CopiedSetValues(
    val loadKg: Double? = null,
    val repetitions: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMetres: Double? = null,
    val rpe: Int? = null,
    val completed: Boolean = false
)

enum class SuggestionKind { REPEAT, ADD_LOAD, ADD_REPETITIONS, ADD_DURATION, ADD_DISTANCE, REDUCE_ASSISTANCE, NONE }
data class ProgressionSuggestion(
    val kind: SuggestionKind,
    val reason: String,
    val loadKg: Double? = null,
    val repetitions: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMetres: Double? = null,
    val assistanceKg: Double? = null
)

data class SessionComparison(
    val previousSessionId: String?,
    val currentCompletedSets: Int,
    val previousCompletedSets: Int,
    val currentVolumeKg: Double,
    val previousVolumeKg: Double,
    val volumeDifferenceKg: Double
)

enum class TrendMetric(val label: String) {
    LOAD("Load"), ESTIMATED_1RM("Estimated 1RM"), SET_VOLUME("Set volume"), REPETITIONS("Repetitions"),
    ADDED_WEIGHT("Added weight"), ASSISTANCE("Assistance"), DURATION("Duration"), DISTANCE("Distance"), PACE("Pace")
}
data class TrendPoint(val sessionId: String, val timestamp: Long, val value: Double, val record: RecordResult)
data class TrendSeries(val metric: TrendMetric, val unit: String, val points: List<TrendPoint>) { val hasTrend get() = points.size >= 2 }

data class LiveRecordEvent(val eventId: String, val announcement: String, val records: PersonalRecords)

class RecordEventLedger(acknowledged: Set<String> = emptySet()) {
    private val seen = acknowledged.toMutableSet()
    fun consume(event: LiveRecordEvent?): LiveRecordEvent? = event?.takeIf { seen.add(it.eventId) }
    fun acknowledgedIds(): Set<String> = seen.toSet()
}

data class SuggestionEnvelope(
    val exerciseId: String,
    val capabilitySignature: String,
    val historyFingerprint: String,
    val suggestion: ProgressionSuggestion
)
data class EditableTarget(val exerciseId: String, val completed: Boolean, val values: CopiedSetValues)
sealed interface SuggestionApplyResult {
    data class Applied(val index: Int, val target: EditableTarget, val explanation: String) : SuggestionApplyResult
    data class Unavailable(val reason: String) : SuggestionApplyResult
}

object WorkoutPerformanceEngine {
    fun metricOptions(capabilities: Set<MeasurementCapability>): List<TrendMetric> = buildList {
        if (MeasurementCapability.LOAD in capabilities && MeasurementCapability.REPETITIONS in capabilities) addAll(listOf(TrendMetric.LOAD, TrendMetric.ESTIMATED_1RM, TrendMetric.SET_VOLUME))
        if (MeasurementCapability.REPETITIONS in capabilities) add(TrendMetric.REPETITIONS)
        if (MeasurementCapability.WEIGHTED_BODYWEIGHT in capabilities) add(TrendMetric.ADDED_WEIGHT)
        if (MeasurementCapability.ASSISTED_LOAD in capabilities) add(TrendMetric.ASSISTANCE)
        if (MeasurementCapability.DURATION in capabilities) add(TrendMetric.DURATION)
        if (MeasurementCapability.DISTANCE in capabilities) add(TrendMetric.DISTANCE)
        if (MeasurementCapability.DURATION in capabilities && MeasurementCapability.DISTANCE in capabilities) add(TrendMetric.PACE)
    }.distinct()

    fun trend(history: List<PerformanceSet>, profileId: String, exerciseId: String, metric: TrendMetric): TrendSeries {
        val eligible = valid(history, profileId, exerciseId).filter { it.setType != "WARMUP" }
        val sessions = eligible.groupBy { it.sessionId }.values.sortedBy { values -> values.maxOf { it.sessionEndedAt } }
        var previousBest: Double? = null
        val lowerWins = metric == TrendMetric.ASSISTANCE || metric == TrendMetric.PACE
        val points = sessions.mapNotNull { values ->
            val value = when (metric) {
                TrendMetric.LOAD -> values.mapNotNull { it.loadKg?.takeIf { n -> n > 0 } }.maxOrNull()
                TrendMetric.ESTIMATED_1RM -> values.mapNotNull { estimatedOneRepMaxKg(it.loadKg, it.repetitions) }.maxOrNull()
                TrendMetric.SET_VOLUME -> values.mapNotNull { v -> v.loadKg?.let { l -> v.repetitions?.let { r -> if (l > 0 && r > 0) l*r else null } } }.maxOrNull()
                TrendMetric.REPETITIONS -> values.mapNotNull { it.repetitions?.takeIf { n -> n > 0 }?.toDouble() }.maxOrNull()
                TrendMetric.ADDED_WEIGHT -> values.mapNotNull { it.addedWeightKg?.takeIf { n -> n > 0 } }.maxOrNull()
                TrendMetric.ASSISTANCE -> values.mapNotNull { it.assistanceKg?.takeIf { n -> n >= 0 } }.minOrNull()
                TrendMetric.DURATION -> values.mapNotNull { it.durationSeconds?.takeIf { n -> n > 0 }?.toDouble() }.maxOrNull()
                TrendMetric.DISTANCE -> values.mapNotNull { it.distanceMetres?.takeIf { n -> n > 0 } }.maxOrNull()
                TrendMetric.PACE -> values.mapNotNull { v -> if ((v.durationSeconds?:0)>0 && (v.distanceMetres?:0.0)>0) v.durationSeconds!!/(v.distanceMetres!!/1000.0) else null }.minOrNull()
            } ?: return@mapNotNull null
            val record = if (previousBest == null) RecordResult.NEW else compare(value, previousBest, !lowerWins)
            if (record == RecordResult.NEW) previousBest = value
            TrendPoint(values.first().sessionId, values.maxOf { it.sessionEndedAt }, value, record)
        }
        val unit = when (metric) { TrendMetric.LOAD,TrendMetric.ESTIMATED_1RM,TrendMetric.SET_VOLUME,TrendMetric.ADDED_WEIGHT,TrendMetric.ASSISTANCE -> "kg"; TrendMetric.REPETITIONS -> "reps"; TrendMetric.DURATION -> "sec"; TrendMetric.DISTANCE -> "m"; TrendMetric.PACE -> "sec/km" }
        return TrendSeries(metric, unit, points)
    }

    fun liveRecordEvent(sessionId: String, setLocalId: String, current: PerformanceSet, prior: List<PerformanceSet>): LiveRecordEvent? {
        if (!current.completed || current.deleted) return null
        val records = personalRecords(listOf(current), prior.filter { it.stableId != current.stableId }, current.profileId, current.exerciseId)
        val categories = (records.newRecords.map { "New $it personal record" } + records.matchedRecords.map { "Matched $it personal record" })
        if (categories.isEmpty()) return null
        return LiveRecordEvent("$sessionId:${current.exerciseId}:$setLocalId", categories.joinToString(". "), records)
    }

    fun capabilitySignature(capabilities: Set<MeasurementCapability>) = capabilities.map { it.wireName }.sorted().joinToString("|")
    fun historyFingerprint(history: List<PerformanceSet>) = history.filter { it.completed && !it.deleted }.distinctBy { it.stableId }
        .sortedBy { it.stableId }.joinToString("|") { "${it.stableId}:${it.loadKg}:${it.repetitions}:${it.durationSeconds}:${it.distanceMetres}" }

    fun applySuggestion(
        envelope: SuggestionEnvelope,
        activeExerciseId: String,
        capabilities: Set<MeasurementCapability>,
        currentHistoryFingerprint: String,
        targets: List<EditableTarget>
    ): SuggestionApplyResult {
        if (envelope.exerciseId != activeExerciseId) return SuggestionApplyResult.Unavailable("Suggestion belongs to another exercise.")
        if (envelope.capabilitySignature != capabilitySignature(capabilities)) return SuggestionApplyResult.Unavailable("Exercise tracking changed; suggestion was not applied.")
        if (envelope.historyFingerprint != currentHistoryFingerprint) return SuggestionApplyResult.Unavailable("History changed; refresh the suggestion.")
        val index = targets.indexOfFirst { it.exerciseId == activeExerciseId && !it.completed }
        if (index < 0) return SuggestionApplyResult.Unavailable("No uncompleted set is available. Add a set first.")
        val applied = applySuggestion(targets[index].values, envelope.suggestion)
        return SuggestionApplyResult.Applied(index, targets[index].copy(values = applied), "Suggestion applied; values remain editable.")
    }
    private fun valid(history: List<PerformanceSet>, profileId: String, exerciseId: String, excludeSessionId: String? = null) =
        history.asSequence().filter { it.profileId == profileId && it.exerciseId == exerciseId && it.completed && !it.deleted && it.sessionId != excludeSessionId }
            .filter { (it.loadKg ?: 0.0) >= 0 && (it.repetitions ?: 0) >= 0 && (it.durationSeconds ?: 0) >= 0 && (it.distanceMetres ?: 0.0) >= 0 }
            .distinctBy { it.stableId }.toList()

    fun previousSession(history: List<PerformanceSet>, profileId: String, exerciseId: String, currentSessionId: String): List<PerformanceSet> {
        val eligible = valid(history, profileId, exerciseId, currentSessionId)
        val session = eligible.groupBy { it.sessionId }.maxWithOrNull(compareBy({ it.value.maxOfOrNull(PerformanceSet::sessionEndedAt) ?: 0L }, { it.key }))?.key
        return eligible.filter { it.sessionId == session }.sortedWith(compareBy<PerformanceSet> { it.setNumber }.thenBy { it.stableId })
    }

    fun copyValues(source: PerformanceSet, capabilities: Set<MeasurementCapability>): CopiedSetValues = CopiedSetValues(
        loadKg = source.loadKg?.takeIf { MeasurementCapability.LOAD in capabilities },
        repetitions = source.repetitions?.takeIf { MeasurementCapability.REPETITIONS in capabilities },
        durationSeconds = source.durationSeconds?.takeIf { MeasurementCapability.DURATION in capabilities },
        distanceMetres = source.distanceMetres?.takeIf { MeasurementCapability.DISTANCE in capabilities },
        rpe = source.rpe?.takeIf { MeasurementCapability.RPE in capabilities }
    )

    fun estimatedOneRepMaxKg(loadKg: Double?, repetitions: Int?): Double? {
        val load = loadKg ?: return null; val reps = repetitions ?: return null
        if (load <= 0.0 || reps !in 1..12) return null
        return if (reps == 1) load else load * (1.0 + reps / 30.0) // Epley
    }

    fun best(history: List<PerformanceSet>, profileId: String, exerciseId: String, excludeSessionId: String? = null): PerformanceBest {
        val sets = valid(history, profileId, exerciseId, excludeSessionId).filter { it.setType != "WARMUP" }
        fun pace(it: PerformanceSet) = if ((it.durationSeconds ?: 0) > 0 && (it.distanceMetres ?: 0.0) > 0) it.durationSeconds!! / (it.distanceMetres!! / 1000.0) else null
        return PerformanceBest(
            heaviestLoadKg = sets.mapNotNull { it.loadKg?.takeIf { value -> value > 0 } }.maxOrNull(),
            mostRepetitions = sets.mapNotNull { it.repetitions?.takeIf { value -> value > 0 } }.maxOrNull(),
            highestSetVolumeKg = sets.mapNotNull { set -> set.loadKg?.let { load -> set.repetitions?.let { reps -> if (load > 0 && reps > 0) load * reps else null } } }.maxOrNull(),
            estimatedOneRepMaxKg = sets.mapNotNull { estimatedOneRepMaxKg(it.loadKg, it.repetitions) }.maxOrNull(),
            longestDurationSeconds = sets.mapNotNull { it.durationSeconds?.takeIf { value -> value > 0 } }.maxOrNull(),
            greatestDistanceMetres = sets.mapNotNull { it.distanceMetres?.takeIf { value -> value > 0 } }.maxOrNull(),
            fastestPaceSecondsPerKm = sets.mapNotNull(::pace).minOrNull(),
            bestAddedWeightKg = sets.mapNotNull { it.addedWeightKg?.takeIf { value -> value > 0 } }.maxOrNull(),
            leastAssistanceKg = sets.mapNotNull { it.assistanceKg?.takeIf { value -> value >= 0 } }.minOrNull()
        )
    }

    fun personalRecords(current: List<PerformanceSet>, prior: List<PerformanceSet>, profileId: String, exerciseId: String): PersonalRecords {
        val now = best(current, profileId, exerciseId); val before = best(prior, profileId, exerciseId)
        fun higher(value: Double?, old: Double?) = compare(value, old, true)
        fun higherInt(value: Int?, old: Int?) = compare(value?.toDouble(), old?.toDouble(), true)
        fun lower(value: Double?, old: Double?) = compare(value, old, false)
        return PersonalRecords(linkedMapOf(
            "heaviest load" to higher(now.heaviestLoadKg, before.heaviestLoadKg),
            "estimated 1RM" to higher(now.estimatedOneRepMaxKg, before.estimatedOneRepMaxKg),
            "set volume" to higher(now.highestSetVolumeKg, before.highestSetVolumeKg),
            "repetitions" to higherInt(now.mostRepetitions, before.mostRepetitions),
            "added weight" to higher(now.bestAddedWeightKg, before.bestAddedWeightKg),
            "least assistance" to lower(now.leastAssistanceKg, before.leastAssistanceKg),
            "duration" to higherInt(now.longestDurationSeconds, before.longestDurationSeconds),
            "distance" to higher(now.greatestDistanceMetres, before.greatestDistanceMetres),
            "pace" to lower(now.fastestPaceSecondsPerKm, before.fastestPaceSecondsPerKm)
        ))
    }

    private fun compare(value: Double?, old: Double?, higherWins: Boolean): RecordResult {
        if (value == null || !value.isFinite() || value <= 0.0) return RecordResult.NONE
        if (old == null) return RecordResult.NEW
        val delta = value - old
        if (kotlin.math.abs(delta) < 0.000001) return RecordResult.MATCHED
        return if ((delta > 0) == higherWins) RecordResult.NEW else RecordResult.NONE
    }

    fun suggestion(previous: List<PerformanceSet>, capabilities: Set<MeasurementCapability>, loadIncrementKg: Double = 2.5): ProgressionSuggestion {
        val working = previous.filter { it.completed && !it.deleted && it.setType != "WARMUP" }
        if (working.size < 2) return ProgressionSuggestion(SuggestionKind.NONE, "No suggestion yet — complete more working sets first.")
        val last = working.maxByOrNull { it.setNumber } ?: return ProgressionSuggestion(SuggestionKind.NONE, "No suggestion yet.")
        val highRpe = working.mapNotNull { it.rpe }.average().takeUnless { it.isNaN() }?.let { it >= 9.0 } == true
        if (MeasurementCapability.ASSISTED_LOAD in capabilities && !highRpe && last.assistanceKg != null)
            return ProgressionSuggestion(SuggestionKind.REDUCE_ASSISTANCE, "All recent working sets were completed; try a small assistance reduction.", repetitions=last.repetitions, assistanceKg=(last.assistanceKg-loadIncrementKg).coerceAtLeast(0.0))
        if (MeasurementCapability.DURATION in capabilities && MeasurementCapability.REPETITIONS !in capabilities && last.durationSeconds != null)
            return if (highRpe) ProgressionSuggestion(SuggestionKind.REPEAT, "The previous effort was high; repeating is a valid next target.", durationSeconds=last.durationSeconds)
            else ProgressionSuggestion(SuggestionKind.ADD_DURATION, "Recent timed work was completed; try a small optional increase.", durationSeconds=last.durationSeconds + 5)
        if (MeasurementCapability.DISTANCE in capabilities && MeasurementCapability.REPETITIONS !in capabilities && last.distanceMetres != null)
            return if (highRpe) ProgressionSuggestion(SuggestionKind.REPEAT, "The previous effort was high; repeating is a valid next target.", distanceMetres=last.distanceMetres)
            else ProgressionSuggestion(SuggestionKind.ADD_DISTANCE, "Recent distance work was completed; try a modest optional increase.", distanceMetres=last.distanceMetres * 1.05)
        if (MeasurementCapability.LOAD in capabilities && last.loadKg != null) {
            if (highRpe) return ProgressionSuggestion(SuggestionKind.REPEAT, "The previous effort was high; maintain the load and reassess next time.", loadKg=last.loadKg, repetitions=last.repetitions)
            val reps = last.repetitions ?: 0
            return if (reps >= 10) ProgressionSuggestion(SuggestionKind.ADD_LOAD, "All recent working sets reached the upper repetition range.", loadKg=last.loadKg + loadIncrementKg, repetitions=reps)
            else ProgressionSuggestion(SuggestionKind.ADD_REPETITIONS, "Build repetitions before increasing load.", loadKg=last.loadKg, repetitions=reps + 1)
        }
        return ProgressionSuggestion(SuggestionKind.REPEAT, "Repeat the previous completed target.", repetitions=last.repetitions)
    }

    fun applySuggestion(target: CopiedSetValues, suggestion: ProgressionSuggestion): CopiedSetValues = target.copy(
        loadKg = suggestion.loadKg ?: target.loadKg,
        repetitions = suggestion.repetitions ?: target.repetitions,
        durationSeconds = suggestion.durationSeconds ?: target.durationSeconds,
        distanceMetres = suggestion.distanceMetres ?: target.distanceMetres,
        completed = false
    )

    fun compareSessions(current: List<PerformanceSet>, previous: List<PerformanceSet>): SessionComparison {
        fun validSets(values: List<PerformanceSet>) = values.filter { it.completed && !it.deleted }.distinctBy { it.stableId }
        val now = validSets(current); val before = validSets(previous)
        fun volume(values: List<PerformanceSet>) = values.sumOf { (it.loadKg ?: 0.0) * (it.repetitions ?: 0) }
        val currentVolume = volume(now); val previousVolume = volume(before)
        return SessionComparison(before.firstOrNull()?.sessionId, now.size, before.size, currentVolume, previousVolume, currentVolume - previousVolume)
    }
}
