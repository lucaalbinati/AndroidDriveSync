package com.example.androiddrivesync

class SynchronizedFile(val name: String, val syncStatus: SyncStatus) {
    enum class SyncStatus {
        SYNCED, OUTDATED, NOT_PRESENT, UNKNOWN
    }
}