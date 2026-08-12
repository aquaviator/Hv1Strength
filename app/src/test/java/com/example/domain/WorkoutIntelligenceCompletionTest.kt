package com.example.domain

import com.example.catalogue.MeasurementCapability.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutIntelligenceCompletionTest {
    private fun set(id:String="a", session:String="s1", profile:String="p", time:Long=1, load:Double?=100.0, reps:Int?=5,
                    duration:Int?=null, distance:Double?=null, assistance:Double?=null, deleted:Boolean=false, completed:Boolean=true) =
        PerformanceSet(id,session,"squat",profile,time,1,completed=completed,deleted=deleted,loadKg=load,repetitions=reps,
            durationSeconds=duration,distanceMetres=distance,assistanceKg=assistance)

    @Test fun weightedMetricsAreStableAndUnique() = assertEquals(listOf(TrendMetric.LOAD,TrendMetric.ESTIMATED_1RM,TrendMetric.SET_VOLUME,TrendMetric.REPETITIONS), WorkoutPerformanceEngine.metricOptions(setOf(LOAD,REPETITIONS)))
    @Test fun bodyweightMetricsUseRepetitions() = assertEquals(listOf(TrendMetric.REPETITIONS), WorkoutPerformanceEngine.metricOptions(setOf(REPETITIONS)))
    @Test fun assistedBodyweightUsesReductionTrend() { val s=WorkoutPerformanceEngine.trend(listOf(set(assistance=40.0,load=null),set("b","s2",time=2,assistance=35.0,load=null)),"p","squat",TrendMetric.ASSISTANCE); assertEquals(35.0,s.points.last().value,0.0); assertEquals(RecordResult.NEW,s.points.last().record) }
    @Test fun durationTrend() = assertEquals(65.0, WorkoutPerformanceEngine.trend(listOf(set(duration=65,load=null,reps=null)),"p","squat",TrendMetric.DURATION).points.single().value,0.0)
    @Test fun distanceTrend() = assertEquals(5000.0, WorkoutPerformanceEngine.trend(listOf(set(distance=5000.0,load=null,reps=null)),"p","squat",TrendMetric.DISTANCE).points.single().value,0.0)
    @Test fun paceTrend() = assertEquals(300.0, WorkoutPerformanceEngine.trend(listOf(set(duration=1500,distance=5000.0,load=null,reps=null)),"p","squat",TrendMetric.PACE).points.single().value,0.0)
    @Test fun underlyingUnitsStayMetric() = assertEquals(100.0, WorkoutPerformanceEngine.trend(listOf(set()),"p","squat",TrendMetric.LOAD).points.single().value,0.0)
    @Test fun onePointIsInsufficientForLine() = assertFalse(WorkoutPerformanceEngine.trend(listOf(set()),"p","squat",TrendMetric.LOAD).hasTrend)
    @Test fun profileIsolation() = assertEquals(1, WorkoutPerformanceEngine.trend(listOf(set(),set("b",profile="other")),"p","squat",TrendMetric.LOAD).points.size)
    @Test fun replayIsDeduplicated() = assertEquals(1, WorkoutPerformanceEngine.trend(listOf(set(),set()),"p","squat",TrendMetric.LOAD).points.size)
    @Test fun liveNewPrIsAnnounced() = assertTrue(WorkoutPerformanceEngine.liveRecordEvent("now","1",set(session="now",load=110.0),listOf(set()))!!.announcement.contains("New"))
    @Test fun liveMatchedPrIsAnnounced() = assertTrue(WorkoutPerformanceEngine.liveRecordEvent("now","1",set("current",session="now"),listOf(set()))!!.announcement.contains("Matched"))
    @Test fun multipleRecordsUseOneEvent() { val e=WorkoutPerformanceEngine.liveRecordEvent("now","1",set(session="now",load=110.0,reps=6),listOf(set()))!!; assertTrue(e.records.newRecords.size>1); assertEquals("now:squat:1",e.eventId) }
    @Test fun ledgerDoesNotReplayOnRecompositionOrRestoration() { val e=WorkoutPerformanceEngine.liveRecordEvent("now","1",set(session="now",load=110.0),listOf(set()))!!; val l=RecordEventLedger(); assertNotNull(l.consume(e)); assertNull(l.consume(e)); assertNull(RecordEventLedger(l.acknowledgedIds()).consume(e)) }
    @Test fun editingRecomputesTruthfully() { val prior=listOf(set()); assertNotNull(WorkoutPerformanceEngine.liveRecordEvent("now","1",set("current",session="now",load=110.0),prior)); assertNull(WorkoutPerformanceEngine.liveRecordEvent("now","1",set("current",session="now",load=90.0,reps=4),prior)) }
    @Test fun deletionChangesDerivedTrend() = assertEquals(1, WorkoutPerformanceEngine.trend(listOf(set(),set("b","s2",time=2,load=120.0,deleted=true)),"p","squat",TrendMetric.LOAD).points.size)
    @Test fun applyIsEditableAndIncomplete() { val e=envelope(); val r=WorkoutPerformanceEngine.applySuggestion(e,"squat",setOf(LOAD,REPETITIONS),"h",listOf(EditableTarget("squat",false,CopiedSetValues()))) as SuggestionApplyResult.Applied; assertFalse(r.target.values.completed); assertEquals(102.5,r.target.values.loadKg!!,0.0) }
    @Test fun applyDoesNotMutateOtherTargets() { val e=envelope(); val other=EditableTarget("squat",false,CopiedSetValues(loadKg=20.0)); val targets=listOf(EditableTarget("squat",true,CopiedSetValues(loadKg=10.0)),other); val r=WorkoutPerformanceEngine.applySuggestion(e,"squat",setOf(LOAD,REPETITIONS),"h",targets) as SuggestionApplyResult.Applied; assertEquals(1,r.index); assertEquals(10.0,targets[0].values.loadKg!!,0.0) }
    @Test fun staleSuggestionIsRejected() = assertTrue(WorkoutPerformanceEngine.applySuggestion(envelope(),"squat",setOf(LOAD,REPETITIONS),"changed",listOf(EditableTarget("squat",false,CopiedSetValues()))) is SuggestionApplyResult.Unavailable)
    @Test fun capabilityMismatchIsRejected() = assertTrue(WorkoutPerformanceEngine.applySuggestion(envelope(),"squat",setOf(REPETITIONS),"h",listOf(EditableTarget("squat",false,CopiedSetValues()))) is SuggestionApplyResult.Unavailable)
    @Test fun wrongExerciseIsRejected() = assertTrue(WorkoutPerformanceEngine.applySuggestion(envelope(),"bench",setOf(LOAD,REPETITIONS),"h",listOf(EditableTarget("bench",false,CopiedSetValues()))) is SuggestionApplyResult.Unavailable)
    @Test fun noIncompleteTargetIsRejected() = assertTrue(WorkoutPerformanceEngine.applySuggestion(envelope(),"squat",setOf(LOAD,REPETITIONS),"h",listOf(EditableTarget("squat",true,CopiedSetValues()))) is SuggestionApplyResult.Unavailable)
    @Test fun malformedSetHasNoLiveAnnouncement() = assertNull(WorkoutPerformanceEngine.liveRecordEvent("now","1",set(session="now",load=0.0,reps=0),emptyList()))
    @Test fun announcementUsesAccessibleText() { val a=WorkoutPerformanceEngine.liveRecordEvent("now","1",set(session="now",load=110.0),listOf(set()))!!.announcement; assertTrue(a.contains("personal record")) }
    private fun envelope() = SuggestionEnvelope("squat",WorkoutPerformanceEngine.capabilitySignature(setOf(LOAD,REPETITIONS)),"h",ProgressionSuggestion(SuggestionKind.ADD_LOAD,"Add load",102.5,5))
}
