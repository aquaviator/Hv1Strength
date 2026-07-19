package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

object StreakCalculator {
    fun calculateStreak(
        workoutDates: Set<String>, // "yyyy-MM-dd" sorted
        todayStr: String,
        yesterdayStr: String,
        sdf: SimpleDateFormat
    ): Int {
        if (workoutDates.isEmpty()) return 0

        var current = 0
        val hasWorkoutToday = workoutDates.contains(todayStr)
        val hasWorkoutYesterday = workoutDates.contains(yesterdayStr)

        if (hasWorkoutToday || hasWorkoutYesterday) {
            val cal = Calendar.getInstance()
            try {
                val startStr = if (hasWorkoutToday) todayStr else yesterdayStr
                cal.time = sdf.parse(startStr) ?: Date()
                while (true) {
                    val dateStr = sdf.format(cal.time)
                    if (workoutDates.contains(dateStr)) {
                        current++
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return current
    }

    fun calculateLongestStreak(workoutDatesList: List<String>, sdf: SimpleDateFormat): Int {
        if (workoutDatesList.isEmpty()) return 0
        var longest = 1
        var currentRun = 1
        for (i in 1 until workoutDatesList.size) {
            try {
                val d1 = sdf.parse(workoutDatesList[i - 1])!!
                val d2 = sdf.parse(workoutDatesList[i])!!
                val diffMs = d2.time - d1.time
                val diffDays = diffMs / (1000 * 60 * 60 * 24)
                if (diffDays <= 1L) {
                    currentRun++
                    if (currentRun > longest) {
                        longest = currentRun
                    }
                } else if (diffDays > 1L) {
                    currentRun = 1
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return longest
    }

    fun calculateConsistency(
        workoutDates: Set<String>,
        currentYear: Int,
        currentMonth: Int,
        totalDaysInCurrentMonthSoFar: Int,
        totalDaysInYearSoFar: Int,
        sdf: SimpleDateFormat
    ): Pair<Float, Float> {
        var daysInMonthWithWorkout = 0
        var daysInYearWithWorkout = 0

        workoutDates.forEach { dateStr ->
            try {
                val date = sdf.parse(dateStr)!!
                val itemCal = Calendar.getInstance()
                itemCal.time = date
                if (itemCal.get(Calendar.YEAR) == currentYear) {
                    daysInYearWithWorkout++
                    if (itemCal.get(Calendar.MONTH) == currentMonth) {
                        daysInMonthWithWorkout++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val monthlyPct = if (totalDaysInCurrentMonthSoFar > 0) {
            (daysInMonthWithWorkout.toFloat() / totalDaysInCurrentMonthSoFar.toFloat()) * 100f
        } else 0f

        val yearlyPct = if (totalDaysInYearSoFar > 0) {
            (daysInYearWithWorkout.toFloat() / totalDaysInYearSoFar.toFloat()) * 100f
        } else 0f

        return Pair(monthlyPct, yearlyPct)
    }
}
