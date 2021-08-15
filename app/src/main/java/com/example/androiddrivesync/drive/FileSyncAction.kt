package com.example.androiddrivesync.drive

class FileSyncAction(val syncStatus: FileSyncStatus, val filename: String, val localRelativeFilepath: String?, val parentFolderId: String?) {
    constructor(syncStatus: FileSyncStatus, filename: String, parentFolderId: String) : this(syncStatus, filename, null, parentFolderId)

    init {
        when (syncStatus) {
            FileSyncStatus.NOT_PRESENT, FileSyncStatus.OUT_OF_SYNC -> {
                assert(localRelativeFilepath != null)
                assert(parentFolderId != null)
            }
            FileSyncStatus.TO_BE_DELETED, FileSyncStatus.TO_BE_DELETED_FROM_BASE_DIR -> {
                assert(parentFolderId != null)
            }
            FileSyncStatus.SYNCED, FileSyncStatus.UNKNOWN -> {
            }
        }
    }
}