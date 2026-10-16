package com.expensetracker.app.data.model

enum class RecurrenceFrequency(val displayName: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    companion object {
        fun fromString(value: String): RecurrenceFrequency {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: MONTHLY
        }
    }
}
