package com.example.androiddrivesync

import android.content.Context
import android.webkit.MimeTypeMap
import java.io.File

class Utility {
    companion object {
        fun getTypeFromFilename(context: Context, filename: String): String {
            val extension = File(filename).extension

            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: throw MimeTypeNotFoundException(context.getString(R.string.mime_type_not_found_exception_message, extension, filename))
        }
    }
    enum class FileSyncStatus {
        SYNCED, OUT_OF_SYNC, NOT_PRESENT, UNKNOWN
    }
}