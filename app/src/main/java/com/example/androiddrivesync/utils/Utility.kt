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
}