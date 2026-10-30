package com.carebuddy.app.data

import java.util.UUID

enum class RepeatOption {
    ONCE, DAILY, WEEKDAYS, CUSTOM
}

data class Reminder(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val time: String, // e.g. "10:00 AM"
    val minutesOfDay: Int, // minutes since midnight (0..1439)
    val repeat: RepeatOption = RepeatOption.DAILY,
    val enabled: Boolean = true,
    val isAcknowledgedToday: Boolean = false,
    val category: String = "Medication" // Medication, Water, Activity, Custom
)
