package com.example.resqmesh.model

import java.util.Calendar
import java.util.Date

data class UserProfile(
    val name: String = "CIVILIAN_RESCUER",
    val birthdateEpochMs: Long = Calendar.getInstance().apply { set(2000, Calendar.JANUARY, 1) }.timeInMillis
) {
    /**
     * Automatically calculates exact current age from birthdate
     */
    val age: Int
        get() {
            val now = Calendar.getInstance()
            val dob = Calendar.getInstance().apply { timeInMillis = birthdateEpochMs }
            var ageYears = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (now.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                ageYears--
            }
            return ageYears.coerceAtLeast(0)
        }

    val displayTag: String
        get() = if (name.isNotBlank()) "$name (${age}y)" else "UNKNOWN (${age}y)"
}
