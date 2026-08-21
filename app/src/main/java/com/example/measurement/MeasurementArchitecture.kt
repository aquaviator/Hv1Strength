package com.example.measurement

import com.example.catalogue.CatalogueExercise
import com.example.catalogue.MeasurementCapability

enum class MetricValueKind { INTEGER, DECIMAL, DURATION, TEXT, BOOLEAN }
enum class MetricSemantic { COUNT, TIME, DISTANCE, RESISTANCE, ASSISTANCE, MECHANICAL_WORK, METABOLIC_ENERGY, POWER, RATE, PHYSIOLOGY, PERCEPTION, DIMENSIONLESS }
enum class MetricSource { USER, MACHINE, SENSOR, DERIVED }
enum class MetricRole { PRIMARY, SECONDARY, OPTIONAL, DERIVED, PRESCRIPTION, RECORDING, UNSUPPORTED }

data class UnitDefinition(
    val key: String,
    val symbol: String,
    val dimension: String,
    val toCanonicalFactor: Double = 1.0,
    val toCanonicalOffset: Double = 0.0,
    val manufacturerSpecific: Boolean = false
) {
    fun toCanonical(value: Double): Double = value * toCanonicalFactor + toCanonicalOffset
    fun fromCanonical(value: Double): Double = (value - toCanonicalOffset) / toCanonicalFactor
}

object CanonicalUnitRegistry {
    val units = listOf(
        UnitDefinition("count", "", "count"), UnitDefinition("second", "s", "time"),
        UnitDefinition("metre", "m", "distance"), UnitDefinition("kilometre", "km", "distance", 1000.0),
        UnitDefinition("mile", "mi", "distance", 1609.344), UnitDefinition("kilogram", "kg", "mass"),
        UnitDefinition("pound", "lb", "mass", 0.45359237), UnitDefinition("watt", "W", "power"),
        UnitDefinition("joule", "J", "mechanical_work"), UnitDefinition("kilocalorie", "kcal", "metabolic_energy"),
        UnitDefinition("metre_per_second", "m/s", "speed"), UnitDefinition("second_per_metre", "s/m", "pace"),
        UnitDefinition("revolution_per_minute", "rpm", "cadence"), UnitDefinition("beat_per_minute", "bpm", "heart_rate"),
        UnitDefinition("percent", "%", "ratio"), UnitDefinition("level", "level", "manufacturer_scale", manufacturerSpecific = true),
        UnitDefinition("drag_factor", "drag", "manufacturer_scale", manufacturerSpecific = true)
    ).associateBy { it.key }
    fun require(key: String) = requireNotNull(units[key]) { "Unknown canonical unit: $key" }
}

data class MetricDefinition(
    val key: String,
    val label: String,
    val semantic: MetricSemantic,
    val valueKind: MetricValueKind,
    val canonicalUnit: String?,
    val allowedSources: Set<MetricSource>,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val derivedFrom: Set<String> = emptySet()
)

object CanonicalMetricDictionary {
    val definitions = listOf(
        MetricDefinition("repetitions", "Repetitions", MetricSemantic.COUNT, MetricValueKind.INTEGER, "count", setOf(MetricSource.USER, MetricSource.MACHINE), 0.0),
        MetricDefinition("duration", "Duration", MetricSemantic.TIME, MetricValueKind.DURATION, "second", setOf(MetricSource.USER, MetricSource.MACHINE, MetricSource.SENSOR), 0.0),
        MetricDefinition("distance", "Distance", MetricSemantic.DISTANCE, MetricValueKind.DECIMAL, "metre", setOf(MetricSource.USER, MetricSource.MACHINE, MetricSource.SENSOR), 0.0),
        MetricDefinition("external_load", "Load", MetricSemantic.RESISTANCE, MetricValueKind.DECIMAL, "kilogram", setOf(MetricSource.USER, MetricSource.MACHINE, MetricSource.SENSOR), 0.0),
        MetricDefinition("assistance", "Assistance", MetricSemantic.ASSISTANCE, MetricValueKind.DECIMAL, "kilogram", setOf(MetricSource.USER, MetricSource.MACHINE), 0.0),
        MetricDefinition("manufacturer_resistance", "Resistance level", MetricSemantic.RESISTANCE, MetricValueKind.DECIMAL, "level", setOf(MetricSource.USER, MetricSource.MACHINE), 0.0),
        MetricDefinition("incline", "Incline", MetricSemantic.DIMENSIONLESS, MetricValueKind.DECIMAL, "percent", setOf(MetricSource.USER, MetricSource.MACHINE), -50.0, 100.0),
        MetricDefinition("pace", "Pace", MetricSemantic.RATE, MetricValueKind.DECIMAL, "second_per_metre", setOf(MetricSource.DERIVED, MetricSource.MACHINE, MetricSource.SENSOR), 0.0, derivedFrom = setOf("duration", "distance")),
        MetricDefinition("speed", "Speed", MetricSemantic.RATE, MetricValueKind.DECIMAL, "metre_per_second", setOf(MetricSource.DERIVED, MetricSource.MACHINE, MetricSource.SENSOR), 0.0, derivedFrom = setOf("duration", "distance")),
        MetricDefinition("power", "Power", MetricSemantic.POWER, MetricValueKind.DECIMAL, "watt", setOf(MetricSource.MACHINE, MetricSource.SENSOR), 0.0),
        MetricDefinition("mechanical_work", "Mechanical work", MetricSemantic.MECHANICAL_WORK, MetricValueKind.DECIMAL, "joule", setOf(MetricSource.DERIVED, MetricSource.MACHINE), 0.0),
        MetricDefinition("energy", "Energy", MetricSemantic.METABOLIC_ENERGY, MetricValueKind.DECIMAL, "kilocalorie", setOf(MetricSource.MACHINE, MetricSource.SENSOR), 0.0),
        MetricDefinition("cadence", "Cadence", MetricSemantic.RATE, MetricValueKind.DECIMAL, "revolution_per_minute", setOf(MetricSource.MACHINE, MetricSource.SENSOR), 0.0),
        MetricDefinition("stroke_rate", "Stroke rate", MetricSemantic.RATE, MetricValueKind.DECIMAL, "count", setOf(MetricSource.MACHINE, MetricSource.SENSOR), 0.0),
        MetricDefinition("heart_rate", "Heart rate", MetricSemantic.PHYSIOLOGY, MetricValueKind.INTEGER, "beat_per_minute", setOf(MetricSource.SENSOR, MetricSource.MACHINE), 0.0),
        MetricDefinition("rpe", "RPE", MetricSemantic.PERCEPTION, MetricValueKind.INTEGER, null, setOf(MetricSource.USER), 1.0, 10.0),
        MetricDefinition("rir", "Reps in reserve", MetricSemantic.PERCEPTION, MetricValueKind.INTEGER, "count", setOf(MetricSource.USER), 0.0),
        MetricDefinition("tempo", "Tempo", MetricSemantic.DIMENSIONLESS, MetricValueKind.TEXT, null, setOf(MetricSource.USER)),
        MetricDefinition("intervals", "Intervals", MetricSemantic.COUNT, MetricValueKind.INTEGER, "count", setOf(MetricSource.USER, MetricSource.MACHINE), 1.0),
        MetricDefinition("side", "Side", MetricSemantic.DIMENSIONLESS, MetricValueKind.TEXT, null, setOf(MetricSource.USER))
    ).associateBy { it.key }
    fun require(key: String) = requireNotNull(definitions[key]) { "Unknown metric: $key" }
}

data class ExerciseMetricProfile(
    val profileId: String,
    val primary: Set<String>,
    val secondary: Set<String> = emptySet(),
    val optional: Set<String> = emptySet(),
    val derived: Set<String> = emptySet(),
    val prescription: Set<String> = primary + secondary,
    val recording: Set<String> = primary + secondary + optional,
    val unsupported: Set<String> = CanonicalMetricDictionary.definitions.keys - recording - derived,
    val schemaVersion: Int = 1
) {
    init {
        val roles = listOf(primary, secondary, optional, derived)
        require(roles.flatten().all { it in CanonicalMetricDictionary.definitions })
        require(primary.isNotEmpty())
        require(roles.indices.all { i -> roles.indices.all { j -> i == j || roles[i].intersect(roles[j]).isEmpty() } })
        require(prescription.all { it in recording })
        require(unsupported.intersect(recording + derived).isEmpty())
    }
    fun role(metric: String) = when (metric) {
        in primary -> MetricRole.PRIMARY; in secondary -> MetricRole.SECONDARY; in optional -> MetricRole.OPTIONAL
        in derived -> MetricRole.DERIVED; in unsupported -> MetricRole.UNSUPPORTED; else -> MetricRole.UNSUPPORTED
    }
}

data class EquipmentCapabilityProfile(
    val equipmentKey: String,
    val emittedMetrics: Set<String>,
    val manufacturerSpecificMetrics: Set<String> = emptySet(),
    val supportsTimeSeries: Boolean = false,
    val supportsSegments: Boolean = false
)

object EquipmentCapabilityRegistry {
    val profiles = listOf(
        EquipmentCapabilityProfile("treadmill", setOf("duration", "distance", "speed", "pace", "incline", "energy", "heart_rate"), supportsTimeSeries = true, supportsSegments = true),
        EquipmentCapabilityProfile("stair_climber", setOf("duration", "repetitions", "manufacturer_resistance", "energy", "heart_rate"), setOf("manufacturer_resistance"), true, true),
        EquipmentCapabilityProfile("stationary_bike", setOf("duration", "distance", "power", "cadence", "manufacturer_resistance", "energy", "heart_rate"), setOf("manufacturer_resistance"), true, true),
        EquipmentCapabilityProfile("air_bike", setOf("duration", "distance", "power", "cadence", "energy", "heart_rate"), supportsTimeSeries = true, supportsSegments = true),
        EquipmentCapabilityProfile("rowing_machine", setOf("duration", "distance", "pace", "power", "stroke_rate", "energy", "heart_rate", "manufacturer_resistance"), setOf("manufacturer_resistance"), true, true),
        EquipmentCapabilityProfile("ski_erg", setOf("duration", "distance", "pace", "power", "stroke_rate", "energy", "heart_rate", "manufacturer_resistance"), setOf("manufacturer_resistance"), true, true),
        EquipmentCapabilityProfile("elliptical", setOf("duration", "distance", "cadence", "manufacturer_resistance", "energy", "heart_rate"), setOf("manufacturer_resistance"), true, true)
    ).associateBy { it.equipmentKey }
}

object ExerciseMetricProfileResolver {
    fun resolve(exercise: CatalogueExercise): ExerciseMetricProfile {
        val c = exercise.capabilities
        val primary = linkedSetOf<String>()
        if (MeasurementCapability.REPETITIONS in c) primary += "repetitions"
        if (MeasurementCapability.DURATION in c) primary += "duration"
        if (MeasurementCapability.DISTANCE in c) primary += "distance"
        if (MeasurementCapability.LOAD in c || MeasurementCapability.WEIGHTED_BODYWEIGHT in c) primary += "external_load"
        if (MeasurementCapability.ASSISTED_LOAD in c) primary += "assistance"
        if (exercise.id == "rowing_machine_cardio") {
            primary.clear()
            primary += setOf("duration", "distance")
        }
        if (primary.isEmpty()) primary += "duration"
        val secondary = linkedSetOf<String>()
        if (MeasurementCapability.RPE in c) secondary += "rpe"
        if (MeasurementCapability.TEMPO in c) secondary += "tempo"
        if (MeasurementCapability.REPS_IN_RESERVE in c) secondary += "rir"
        if (MeasurementCapability.INTERVALS in c) secondary += "intervals"
        if (MeasurementCapability.SIDE in c) secondary += "side"
        val optional = linkedSetOf<String>()
        if (MeasurementCapability.CALORIES in c) optional += "energy"
        val equipmentProfile = when (exercise.id) {
            "treadmill_run", "incline_treadmill_walk" -> EquipmentCapabilityRegistry.profiles["treadmill"]
            "stair_climber" -> EquipmentCapabilityRegistry.profiles["stair_climber"]
            "stationary_bike" -> EquipmentCapabilityRegistry.profiles["stationary_bike"]
            "air_bike", "assault_bike_sprint" -> EquipmentCapabilityRegistry.profiles["air_bike"]
            "rowing_machine", "rowing_machine_cardio" -> EquipmentCapabilityRegistry.profiles["rowing_machine"]
            "ski_erg" -> EquipmentCapabilityRegistry.profiles["ski_erg"]
            "elliptical" -> EquipmentCapabilityRegistry.profiles["elliptical"]
            else -> null
        }
        optional += equipmentProfile?.emittedMetrics.orEmpty() - primary - secondary - setOf("pace", "speed")
        val derived = linkedSetOf<String>()
        if (MeasurementCapability.PACE in c && setOf("duration", "distance").all(primary::contains)) derived += "pace"
        if (equipmentProfile != null && setOf("duration", "distance").all(primary::contains)) derived += setOf("pace", "speed")
        return ExerciseMetricProfile("governed:${exercise.id}:v1", primary, secondary, optional, derived)
    }
}

data class MetricInput(val metricKey: String, val numericValue: Double? = null, val textValue: String? = null, val unitKey: String? = null)

object MetricValidation {
    fun validate(profile: ExerciseMetricProfile, input: MetricInput, forPrescription: Boolean = false): List<String> = buildList {
        val allowed = if (forPrescription) profile.prescription else profile.recording
        if (input.metricKey !in allowed) add("${input.metricKey} is not supported by ${profile.profileId}")
        val definition = CanonicalMetricDictionary.definitions[input.metricKey]
        if (definition == null) {
            add("Unknown metric")
            return@buildList
        }
        if (definition.valueKind == MetricValueKind.TEXT && input.textValue.isNullOrBlank()) add("Text value is required")
        if (definition.valueKind != MetricValueKind.TEXT && input.numericValue == null) add("Numeric value is required")
        input.numericValue?.let { value ->
            if (!value.isFinite()) add("Value must be finite")
            if (definition.minimum != null && value < definition.minimum) add("Value is below the supported minimum")
            if (definition.maximum != null && value > definition.maximum) add("Value is above the supported maximum")
        }
        if (input.unitKey != null) {
            val unit = CanonicalUnitRegistry.units[input.unitKey]
            if (unit == null) add("Unknown unit") else if (definition.canonicalUnit != null && unit.dimension != CanonicalUnitRegistry.require(definition.canonicalUnit).dimension) add("Unit dimension mismatch")
        }
    }
}
