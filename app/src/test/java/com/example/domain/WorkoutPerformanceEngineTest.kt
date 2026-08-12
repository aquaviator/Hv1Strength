package com.example.domain

import com.example.catalogue.MeasurementCapability.*
import com.example.core.util.UnitConverter
import org.junit.Assert.*
import org.junit.Test

class WorkoutPerformanceEngineTest {
    private fun set(id:String, session:String="old", profile:String="a", exercise:String="bench_press", end:Long=100,
        load:Double?=100.0, reps:Int?=5, duration:Int?=null, distance:Double?=null, completed:Boolean=true,
        deleted:Boolean=false, type:String="WORKING", assistance:Double?=null, added:Double?=null, rpe:Int?=8, number:Int=1) =
        PerformanceSet(id,session,exercise,profile,end,number,type,completed,deleted,load,reps,duration,distance,rpe,assistance,added)

    @Test fun previousSessionSelectsLatestChronologically() {
        val result=WorkoutPerformanceEngine.previousSession(listOf(set("1",session="a",end=100),set("2",session="b",end=300)),"a","bench_press","current")
        assertEquals("b",result.single().sessionId)
    }
    @Test fun previousSessionExcludesCurrentSession() = assertEquals("old", WorkoutPerformanceEngine.previousSession(listOf(set("1"),set("2",session="current",end=500)),"a","bench_press","current").single().sessionId)
    @Test fun previousSessionIsProfileIsolated() = assertTrue(WorkoutPerformanceEngine.previousSession(listOf(set("1",profile="b")),"a","bench_press","current").isEmpty())
    @Test fun governedAndCustomIdsUseSameStableLogic() = assertEquals("custom_lift", WorkoutPerformanceEngine.previousSession(listOf(set("1",exercise="custom_lift")),"a","custom_lift","current").single().exerciseId)

    @Test fun quickCopyRespectsCapabilitiesAndNeverCompletes() {
        val copied=WorkoutPerformanceEngine.copyValues(set("1",duration=60,distance=500.0),setOf(REPETITIONS,LOAD,RPE))
        assertEquals(100.0,copied.loadKg!!,0.0); assertEquals(5,copied.repetitions); assertNull(copied.durationSeconds); assertNull(copied.distanceMetres); assertFalse(copied.completed)
    }
    @Test fun durationDistanceCopyExcludesUnsupportedLoadAndReps() {
        val copied=WorkoutPerformanceEngine.copyValues(set("1",duration=60,distance=500.0),setOf(DURATION,DISTANCE))
        assertNull(copied.loadKg); assertNull(copied.repetitions); assertEquals(60,copied.durationSeconds); assertEquals(500.0,copied.distanceMetres!!,0.0)
    }
    @Test fun epleyIsAuthoritativeConservativeAndDirectForOneRep() {
        assertEquals(100.0,WorkoutPerformanceEngine.estimatedOneRepMaxKg(100.0,1)!!,0.0)
        assertEquals(120.0,WorkoutPerformanceEngine.estimatedOneRepMaxKg(90.0,10)!!,0.0001)
        assertNull(WorkoutPerformanceEngine.estimatedOneRepMaxKg(100.0,13)); assertNull(WorkoutPerformanceEngine.estimatedOneRepMaxKg(0.0,5))
    }

    @Test fun heaviestLoadPr() = assertNew("heaviest load",set("n",load=110.0),set("o",load=100.0))
    @Test fun estimatedOneRepMaxPr() = assertNew("estimated 1RM",set("n",load=100.0,reps=8),set("o",load=100.0,reps=5))
    @Test fun volumePr() = assertNew("set volume",set("n",load=80.0,reps=12),set("o",load=100.0,reps=5))
    @Test fun repetitionPr() = assertNew("repetitions",set("n",load=20.0,reps=20),set("o",load=20.0,reps=15))
    @Test fun weightedBodyweightPr() = assertNew("added weight",set("n",added=25.0),set("o",added=20.0))
    @Test fun assistedBodyweightPrefersLessAssistance() = assertNew("least assistance",set("n",assistance=20.0),set("o",assistance=25.0))
    @Test fun durationPr() = assertNew("duration",set("n",load=null,reps=null,duration=75),set("o",load=null,reps=null,duration=60))
    @Test fun distancePr() = assertNew("distance",set("n",load=null,reps=null,distance=1200.0),set("o",load=null,reps=null,distance=1000.0))
    @Test fun pacePr() = assertNew("pace",set("n",load=null,reps=null,duration=240,distance=1000.0),set("o",load=null,reps=null,duration=300,distance=1000.0))
    @Test fun equalResultIsMatchedNotNew() {
        val prs=WorkoutPerformanceEngine.personalRecords(listOf(set("n")),listOf(set("o")),"a","bench_press")
        assertEquals(RecordResult.MATCHED,prs.results["heaviest load"])
    }
    @Test fun unitConversionDoesNotChangeUnderlyingRecord() {
        val kg=WorkoutPerformanceEngine.best(listOf(set("1",load=100.0)),"a","bench_press").heaviestLoadKg!!
        val pounds=UnitConverter.kgToLb(kg); assertEquals(kg,UnitConverter.lbToKg(pounds),0.0001)
    }
    @Test fun malformedIncompleteWarmupAndDeletedSetsAreExcluded() {
        val best=WorkoutPerformanceEngine.best(listOf(set("1",load=200.0,completed=false),set("2",load=190.0,deleted=true),set("3",load=180.0,type="WARMUP"),set("4",load=-1.0)),"a","bench_press")
        assertNull(best.heaviestLoadKg)
    }
    @Test fun duplicateSyncReplayIsDeduplicated() {
        val comparison=WorkoutPerformanceEngine.compareSessions(listOf(set("same"),set("same")),emptyList())
        assertEquals(1,comparison.currentCompletedSets); assertEquals(500.0,comparison.currentVolumeKg,0.0)
    }

    @Test fun progressionAddsLoadOnlyAfterUpperRangeCompletion() {
        val s=WorkoutPerformanceEngine.suggestion(listOf(set("1",reps=10,number=1),set("2",reps=10,number=2)),setOf(REPETITIONS,LOAD,RPE))
        assertEquals(SuggestionKind.ADD_LOAD,s.kind); assertEquals(102.5,s.loadKg!!,0.0)
    }
    @Test fun progressionMaintainsAfterHighRpe() {
        val s=WorkoutPerformanceEngine.suggestion(listOf(set("1",rpe=10,number=1),set("2",rpe=9,number=2)),setOf(REPETITIONS,LOAD,RPE))
        assertEquals(SuggestionKind.REPEAT,s.kind)
    }
    @Test fun durationDistanceAndAssistanceSuggestionsAreCapabilitySpecific() {
        assertEquals(SuggestionKind.ADD_DURATION,WorkoutPerformanceEngine.suggestion(listOf(set("1",duration=60,number=1),set("2",duration=60,number=2)),setOf(DURATION)).kind)
        assertEquals(SuggestionKind.ADD_DISTANCE,WorkoutPerformanceEngine.suggestion(listOf(set("1",distance=1000.0,number=1),set("2",distance=1000.0,number=2)),setOf(DISTANCE)).kind)
        assertEquals(SuggestionKind.REDUCE_ASSISTANCE,WorkoutPerformanceEngine.suggestion(listOf(set("1",assistance=30.0,number=1),set("2",assistance=30.0,number=2)),setOf(REPETITIONS,BODYWEIGHT,ASSISTED_LOAD)).kind)
    }
    @Test fun insufficientDataProducesNoSuggestion() = assertEquals(SuggestionKind.NONE,WorkoutPerformanceEngine.suggestion(listOf(set("1")),setOf(REPETITIONS,LOAD)).kind)
    @Test fun applyingSuggestionIsExplicitEditableAndNotCompleted() {
        val result=WorkoutPerformanceEngine.applySuggestion(CopiedSetValues(loadKg=100.0,repetitions=8,completed=false),ProgressionSuggestion(SuggestionKind.ADD_LOAD,"basis",loadKg=102.5,repetitions=8))
        assertEquals(102.5,result.loadKg!!,0.0); assertFalse(result.completed)
    }
    @Test fun summaryComparisonIsNeutralAndExact() {
        val c=WorkoutPerformanceEngine.compareSessions(listOf(set("n1",load=100.0,reps=5),set("n2",load=50.0,reps=10)),listOf(set("o1",load=90.0,reps=5)))
        assertEquals(1000.0,c.currentVolumeKg,0.0); assertEquals(550.0,c.volumeDifferenceKg,0.0); assertEquals(2,c.currentCompletedSets)
    }

    private fun assertNew(category:String,current:PerformanceSet,prior:PerformanceSet) = assertEquals(RecordResult.NEW,WorkoutPerformanceEngine.personalRecords(listOf(current),listOf(prior),"a","bench_press").results[category])
}
