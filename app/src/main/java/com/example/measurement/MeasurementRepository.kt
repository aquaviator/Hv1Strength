package com.example.measurement

import com.example.data.*

data class SegmentInput(val startOffsetMillis: Long, val endOffsetMillis: Long, val numericValue: Double?, val canonicalUnit: String?, val label: String? = null)
data class SampleInput(val offsetMillis: Long, val numericValue: Double, val canonicalUnit: String)
data class ObservationInput(
    val value: MetricInput,
    val source: MetricSource = MetricSource.USER,
    val originalValue: Double? = null,
    val originalUnit: String? = null,
    val manufacturer: String? = null,
    val deviceModel: String? = null,
    val deviceIdentifier: String? = null,
    val protocol: String? = null,
    val segments: List<SegmentInput> = emptyList(),
    val samples: List<SampleInput> = emptyList()
)

class MeasurementRepository(private val dao: StrengthDao) {
    suspend fun savePrescription(templateSetGlobalId: String, profile: ExerciseMetricProfile, values: List<MetricInput>, now: Long) {
        values.flatMap { MetricValidation.validate(profile, it, forPrescription = true) }.also { require(it.isEmpty()) { it.joinToString() } }
        dao.upsertMetricPrescriptions(values.mapIndexed { index, value ->
            MetricPrescriptionEntity("$templateSetGlobalId:${value.metricKey}", templateSetGlobalId, value.metricKey,
                targetValue=value.numericValue, textValue=value.textValue, canonicalUnit=value.unitKey, position=index, createdAt=now, updatedAt=now)
        })
    }

    suspend fun saveObservations(
        loggedSetGlobalId: String,
        profile: ExerciseMetricProfile,
        values: List<ObservationInput>,
        now: Long,
        humanUserId: String = "",
        originDeviceId: String = ""
    ): List<MetricObservationEntity> {
        values.flatMap { MetricValidation.validate(profile, it.value) }.also { require(it.isEmpty()) { it.joinToString() } }
        return values.map { input ->
                val id="$loggedSetGlobalId:${input.value.metricKey}"
                val previous = dao.getMetricObservation(id)
                val observation=MetricObservationEntity(
                    globalId=id, loggedSetGlobalId=loggedSetGlobalId, metricKey=input.value.metricKey,
                    numericValue=input.value.numericValue, textValue=input.value.textValue, canonicalUnit=input.value.unitKey,
                    originalValue=input.originalValue, originalUnit=input.originalUnit, source=input.source.name,
                    manufacturer=input.manufacturer, deviceModel=input.deviceModel,
                    deviceIdentifier=input.deviceIdentifier, protocol=input.protocol, capturedAt=now,
                    humanUserId=humanUserId, createdAt=previous?.createdAt ?: now, updatedAt=now,
                    revision=(previous?.revision ?: 0L) + 1L, syncStatus="PENDING_UPLOAD",
                    originDeviceId=originDeviceId
                )
                val segments=input.segments.mapIndexed { index, segment ->
                    require(segment.endOffsetMillis >= segment.startOffsetMillis)
                    MetricSegmentEntity("$id:segment:$index",id,index,segment.startOffsetMillis,segment.endOffsetMillis,segment.numericValue,segment.canonicalUnit,segment.label)
                }
                val samples=input.samples.sortedBy { it.offsetMillis }.map { sample ->
                    MetricSampleEntity("$id:sample:${sample.offsetMillis}",id,sample.offsetMillis,sample.numericValue,sample.canonicalUnit)
                }
                dao.replaceMetricObservationGraph(observation,segments,samples)
                observation
        }
    }
}
