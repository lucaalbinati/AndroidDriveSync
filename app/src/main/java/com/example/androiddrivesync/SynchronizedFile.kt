package com.example.androiddrivesync

class SynchronizedFile(val name: String, val syncStatus: Utility.FileSyncStatus) {

    fun withNewSyncStatus(syncStatus: Utility.FileSyncStatus): SynchronizedFile {
        return SynchronizedFile(name, syncStatus)
    }
}