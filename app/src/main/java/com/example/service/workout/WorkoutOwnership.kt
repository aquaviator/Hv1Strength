package com.example.service.workout

data class WorkoutOwnership(val userId: String, val humanUserId: String) {
    fun remainsOwnedBy(otherUserId: String) = copy() // Authentication changes never rewrite workout ownership.
    fun isRedirectedTo(otherUserId: String) = userId != "offline" && userId == otherUserId
}
