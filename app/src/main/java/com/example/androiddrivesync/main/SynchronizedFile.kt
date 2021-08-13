package com.example.androiddrivesync.main

import com.example.androiddrivesync.utils.Utility

class SynchronizedFile(val name: String, val syncStatus: Utility.FileSyncStatus) {

    fun withNewSyncStatus(syncStatus: Utility.FileSyncStatus): SynchronizedFile {
        return SynchronizedFile(name, syncStatus)
    }
}