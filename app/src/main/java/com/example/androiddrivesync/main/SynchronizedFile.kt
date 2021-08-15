package com.example.androiddrivesync.main

import com.example.androiddrivesync.drive.FileSyncStatus

class SynchronizedFile(val name: String, val syncStatus: FileSyncStatus) {

    fun withNewSyncStatus(syncStatus: FileSyncStatus): SynchronizedFile {
        return SynchronizedFile(name, syncStatus)
    }
}