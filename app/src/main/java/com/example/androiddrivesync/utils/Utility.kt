package com.example.androiddrivesync.utils

import android.webkit.MimeTypeMap
import java.io.File

class Utility {
    companion object {
        fun getTypeFromFilename(filename: String): String {
            val extension = File(filename).extension

            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: throw MimeTypeNotFoundException("MimeType not found for extension '$extension', from filename '$filename'")
        }

        fun getSizeUnit(size: Int): String {
            return if (size < 1024) {
                "B"
            } else if (1024 <= size && size < 1024*1024) {
                "KB"
            } else if (1024*1024 <= size && size < 1024*1024*1024) {
                "MB"
            } else {
                "GB"
            }
        }

        fun convertToSizeUnit(value: Int, sizeUnit: String): Double {
            return when (sizeUnit) {
                "KB" -> value.div(1024.0)
                "MB" -> value.div(1024.0 * 1024.0)
                "GB" -> value.div(1024.0 * 1024.0 * 1024.0)
                else -> value.toDouble()
            }
        }
    }

    enum class FileSyncStatus {
        SYNCED, OUT_OF_SYNC, NOT_PRESENT, TO_BE_DELETED, TO_BE_DELETED_FROM_BASE_DIR, UNKNOWN
    }

    class FileSyncAction(val syncStatus: FileSyncStatus, val filename: String, val localRelativeFilepath: String?, val parentFolderId: String?) {
        constructor(syncStatus: FileSyncStatus, filename: String, parentFolderId: String): this(syncStatus, filename, null, parentFolderId)

        init {
            when (syncStatus) {
                FileSyncStatus.NOT_PRESENT, FileSyncStatus.OUT_OF_SYNC -> {
                    assert(localRelativeFilepath != null)
                    assert(parentFolderId != null)
                }
                FileSyncStatus.TO_BE_DELETED, FileSyncStatus.TO_BE_DELETED_FROM_BASE_DIR -> {
                    assert(parentFolderId != null)
                }
                FileSyncStatus.SYNCED, FileSyncStatus.UNKNOWN -> {}
            }
        }
    }
}