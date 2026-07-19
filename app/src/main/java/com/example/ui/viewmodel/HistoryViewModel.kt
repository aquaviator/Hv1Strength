package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HistoryFilters(
    val query: String,
    val sort: String,
    val dateRange: String,
    val exerciseId: String?,
    val routineName: String?
)

class HistoryViewModel(
    private val repository: StrengthRepository,
    private val context: Context,
    private val authViewModel: AuthViewModel,
    private val routineViewModel: RoutineViewModel
) : ViewModel() {

    // Workout Sessions State
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sessions: StateFlow<List<WorkoutSession>> = authViewModel.activeUserId.flatMapLatest { userId ->
        repository.getSessionsForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allLoggedSets: StateFlow<List<LoggedSet>> = authViewModel.activeUserId.flatMapLatest { userId ->
        repository.getLoggedSetsForUser(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History Search & Filters
    val historySearchQuery = MutableStateFlow("")
    val historySelectedSort = MutableStateFlow("Newest") // Newest, Oldest, Highest Volume, Longest Session
    val historyDateRange = MutableStateFlow("All") // All, Last 7 Days, Last 30 Days, Last 90 Days
    val historySelectedExerciseId = MutableStateFlow<String?>(null)
    val historySelectedRoutineName = MutableStateFlow<String?>(null)

    private val filtersFlow: Flow<HistoryFilters> = combine(
        historySearchQuery,
        historySelectedSort,
        historyDateRange,
        historySelectedExerciseId,
        historySelectedRoutineName
    ) { query, sort, dateRange, exerciseId, routineName ->
        HistoryFilters(query, sort, dateRange, exerciseId, routineName)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val streakStats: StateFlow<StreakStats> = allLoggedSets.mapLatest { sets ->
        val completedSets = sets.filter { it.isCompleted }
        if (completedSets.isEmpty()) return@mapLatest StreakStats(0, 0, 0f, 0f, emptySet())

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Find session dates
        val sessionIds = completedSets.map { it.sessionId }.distinct()
        val workoutSessions = mutableListOf<WorkoutSession>()
        sessionIds.forEach { sid ->
            val sess = repository.getSessionById(sid)
            if (sess != null) {
                workoutSessions.add(sess)
            }
        }
        val workoutDates = workoutSessions.map { sdf.format(Date(it.startTime)) }.toSet()

        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)

        val current = StreakCalculator.calculateStreak(workoutDates, todayStr, yesterdayStr, sdf)
        val longest = StreakCalculator.calculateLongestStreak(workoutDates.toList(), sdf)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val totalDaysInCurrentMonthSoFar = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val totalDaysInYearSoFar = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        val (monthlyPct, yearlyPct) = StreakCalculator.calculateConsistency(
            workoutDates,
            currentYear,
            currentMonth,
            totalDaysInCurrentMonthSoFar,
            totalDaysInYearSoFar,
            sdf
        )

        StreakStats(
            currentStreak = current,
            longestStreak = longest,
            monthlyConsistencyPct = monthlyPct,
            yearlyConsistencyPct = yearlyPct,
            workoutDates = workoutDates
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakStats(0, 0, 0f, 0f, emptySet()))

    // Computed Filtered Sessions Flow (Uses type-safe combined filtersFlow)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredSessions: StateFlow<List<EnrichedSession>> = combine(
        sessions,
        allLoggedSets,
        routineViewModel.exercises,
        filtersFlow
    ) { sessionsList, setsList, exercisesList, filters ->
        val exercisesMap = exercisesList.associateBy { it.id }
        val setsBySession = setsList.groupBy { it.sessionId }
        
        val enriched = sessionsList.map { session ->
            val sessionSets = setsBySession[session.id] ?: emptyList()
            val volume = VolumeCalculator.calculateTotalVolume(
                sessionSets.map { set ->
                    object : SetVolumeData {
                        override val weight: Float = set.weight
                        override val reps: Int = set.reps
                        override val isCompleted: Boolean = set.isCompleted
                    }
                }
            )
            val durationMs = session.endTime - session.startTime
            val durationMin = maxOf(1L, durationMs / 60000)
            val exerciseNames = sessionSets.mapNotNull { exercisesMap[it.exerciseId]?.name }.distinct()
            EnrichedSession(
                session = session,
                sets = sessionSets,
                totalVolume = volume,
                durationMinutes = durationMin,
                exerciseNames = exerciseNames
            )
        }

        var result = enriched

        if (filters.exerciseId != null) {
            result = result.filter { s -> s.sets.any { it.exerciseId == filters.exerciseId } }
        }

        if (filters.routineName != null) {
            result = result.filter { s -> s.session.templateName == filters.routineName }
        }

        val now = System.currentTimeMillis()
        when (filters.dateRange) {
            "Last 7 Days" -> {
                val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
                result = result.filter { s -> s.session.startTime >= sevenDaysAgo }
            }
            "Last 30 Days" -> {
                val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
                result = result.filter { s -> s.session.startTime >= thirtyDaysAgo }
            }
            "Last 90 Days" -> {
                val ninetyDaysAgo = now - 90L * 24 * 60 * 60 * 1000
                result = result.filter { s -> s.session.startTime >= ninetyDaysAgo }
            }
        }

        if (filters.query.isNotBlank()) {
            val q = filters.query.lowercase()
            result = result.filter { s ->
                s.session.templateName.lowercase().contains(q) ||
                s.exerciseNames.any { it.lowercase().contains(q) } ||
                s.sets.any { it.notes?.lowercase()?.contains(q) == true }
            }
        }

        when (filters.sort) {
            "Newest" -> result = result.sortedByDescending { it.session.startTime }
            "Oldest" -> result = result.sortedBy { it.session.startTime }
            "Highest Volume" -> result = result.sortedByDescending { it.totalVolume }
            "Longest Session" -> result = result.sortedByDescending { it.durationMinutes }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getCompletedSetsForExercise(exerciseId: String): Flow<List<LoggedSet>> {
        return repository.getCompletedSetsForExercise(exerciseId)
    }

    fun getSetsForSession(sessionId: Int): Flow<List<LoggedSet>> {
        return repository.getSetsForSession(sessionId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getEnrichedSession(sessionId: Int): Flow<EnrichedSession?> = flow {
        val session = repository.getSessionById(sessionId)
        if (session == null) {
            emit(null)
            return@flow
        }
        val setsFlow = repository.getSetsForSession(sessionId)
        val exercisesFlow = repository.allExercises
        emitAll(combine(setsFlow, exercisesFlow) { sessionSets, allExercises ->
            val exercisesMap = allExercises.associateBy { it.id }
            val volume = VolumeCalculator.calculateTotalVolume(
                sessionSets.map { set ->
                    object : SetVolumeData {
                        override val weight: Float = set.weight
                        override val reps: Int = set.reps
                        override val isCompleted: Boolean = set.isCompleted
                    }
                }
            )
            val durationMs = session.endTime - session.startTime
            val durationMin = maxOf(1L, durationMs / 60000)
            val exerciseNames = sessionSets.mapNotNull { exercisesMap[it.exerciseId]?.name }.distinct()
            EnrichedSession(
                session = session,
                sets = sessionSets,
                totalVolume = volume,
                durationMinutes = durationMin,
                exerciseNames = exerciseNames
            )
        })
    }

    fun deleteSession(sessionId: Int, onUndoBackup: (WorkoutSession, List<LoggedSet>) -> Unit) {
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            if (session != null) {
                val sets = repository.getSetsForSessionSync(sessionId)
                repository.deleteSession(sessionId)
                onUndoBackup(session, sets)
            }
        }
    }
}
