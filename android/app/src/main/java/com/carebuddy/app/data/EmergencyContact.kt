package com.carebuddy.app.data

data class EmergencyContact(
    val id: String = "primary",
    val name: String,
    val phoneNumber: String,
    val isPrimary: Boolean = true
)
