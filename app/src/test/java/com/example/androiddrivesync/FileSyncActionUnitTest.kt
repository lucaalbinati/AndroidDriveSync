package com.example.androiddrivesync

import com.example.androiddrivesync.drive.FileSyncAction
import com.example.androiddrivesync.drive.FileSyncStatus
import org.junit.Assert
import org.junit.Test

class FileSyncActionUnitTest {

    @Test
    fun invalidFileSyncAction() {
        Assert.assertThrows(AssertionError::class.java) {
            FileSyncAction(FileSyncStatus.NOT_PRESENT, "someFilename", null, null)
            FileSyncAction(FileSyncStatus.OUT_OF_SYNC, "someFilename", null, null)
        }
        Assert.assertThrows(AssertionError::class.java) {
            FileSyncAction(FileSyncStatus.NOT_PRESENT, "someFilename", "someLocalRelativePath", null)
            FileSyncAction(FileSyncStatus.OUT_OF_SYNC, "someFilename", "someLocalRelativePath", null)
        }
        FileSyncAction(FileSyncStatus.NOT_PRESENT, "someFilename", "someLocalRelativePath", "someParentFolderId")
        FileSyncAction(FileSyncStatus.OUT_OF_SYNC, "someFilename", "someLocalRelativePath", "someParentFolderId")
    }

    @Test
    fun invalidFileSyncAction2() {
        Assert.assertThrows(AssertionError::class.java) {
            FileSyncAction(FileSyncStatus.TO_BE_DELETED, "someFilename", "someLocalRelativePath", null)
            FileSyncAction(FileSyncStatus.TO_BE_DELETED_FROM_BASE_DIR, "someFilename", "someLocalRelativePath", null)
        }
        FileSyncAction(FileSyncStatus.TO_BE_DELETED, "someFilename", "someLocalRelativePath", "someParentFolderId")
        FileSyncAction(FileSyncStatus.TO_BE_DELETED_FROM_BASE_DIR, "someFilename", "someLocalRelativePath", "someParentFolderId")
    }

    @Test
    fun invalidFileSyncAction3() {
        FileSyncAction(FileSyncStatus.SYNCED, "someFilename", null, null)
        FileSyncAction(FileSyncStatus.UNKNOWN, "someFilename", null, null)

        FileSyncAction(FileSyncStatus.SYNCED, "someFilename", "someLocalRelativePath", null)
        FileSyncAction(FileSyncStatus.UNKNOWN, "someFilename", "someLocalRelativePath", null)

        FileSyncAction(FileSyncStatus.SYNCED, "someFilename", null, "someParentFolderId")
        FileSyncAction(FileSyncStatus.UNKNOWN, "someFilename", null, "someParentFolderId")

        FileSyncAction(FileSyncStatus.SYNCED, "someFilename", "someLocalRelativePath", "someParentFolderId")
        FileSyncAction(FileSyncStatus.UNKNOWN, "someFilename", "someLocalRelativePath", "someParentFolderId")
    }
}