package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserPreferencesRepository(private val dao: StrengthDao) {

    val userPreferencesFlow: Flow<UserPreferences> = dao.getUserPreferencesFlow()
        .map { it ?: UserPreferences() }

    suspend fun getPreferences(): UserPreferences = withContext(Dispatchers.IO) {
        dao.getUserPreferences() ?: UserPreferences()
    }

    suspend fun updatePreferences(updater: (UserPreferences) -> UserPreferences) = withContext(Dispatchers.IO) {
        val current = getPreferences()
        val updated = updater(current)
        dao.insertUserPreferences(updated)
    }

    suspend fun setMetric(isMetric: Boolean) {
        updatePreferences { it.copy(isMetric = isMetric) }
    }

    suspend fun setTheme(theme: String) {
        updatePreferences { it.copy(theme = theme) }
    }

    suspend fun setKeepScreenAwake(keepScreenAwake: Boolean) {
        updatePreferences { it.copy(keepScreenAwake = keepScreenAwake) }
    }

    suspend fun setDefaultRestTimerDuration(duration: Int) {
        updatePreferences { it.copy(defaultRestTimerDuration = duration) }
    }

    suspend fun setSoundOn(soundOn: Boolean) {
        updatePreferences { it.copy(soundOn = soundOn) }
    }

    suspend fun setVibrationOn(vibrationOn: Boolean) {
        updatePreferences { it.copy(vibrationOn = vibrationOn) }
    }

    suspend fun setDefaultWarmupSets(sets: Int) {
        updatePreferences { it.copy(defaultWarmupSets = sets) }
    }

    suspend fun setAutoCompleteBehavior(behavior: Boolean) {
        updatePreferences { it.copy(autoCompleteBehavior = behavior) }
    }

    suspend fun setAutoScroll(autoScroll: Boolean) {
        updatePreferences { it.copy(autoScroll = autoScroll) }
    }

    suspend fun setTimerPreferences(timerPrefs: String) {
        updatePreferences { it.copy(timerPreferences = timerPrefs) }
    }
}
