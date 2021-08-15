package com.example.androiddrivesync.synchronizeservice

import java.time.Duration

enum class SynchronizePeriodicity {
    NEVER, ON_TAP, DAILY, WEEKLY, MONTHLY;

    fun format(): String {
        return when (this) {
            ON_TAP -> "Only when I tap \"Synchronize Now\""
            else -> this.toString().lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    fun isRecurrent(): Boolean {
        return listOf(DAILY, WEEKLY, MONTHLY).contains(this)
    }

    fun convertToDuration(): Duration {
        return when (this) {
            NEVER, ON_TAP -> throw UnsupportedOperationException("Cannot convert ${SynchronizePeriodicity::class.java.simpleName} '$this' to a ${Duration::class.java.simpleName} object")
            DAILY -> Duration.ofDays(1)
            WEEKLY -> Duration.ofDays(7)
            MONTHLY -> Duration.ofDays(31)
        }
    }
}