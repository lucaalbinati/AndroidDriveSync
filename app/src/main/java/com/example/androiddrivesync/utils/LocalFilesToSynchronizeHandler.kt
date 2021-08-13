package com.example.androiddrivesync.utils

import android.content.Context
import java.util.*
import kotlin.collections.ArrayList

class LocalFilesToSynchronizeHandler {
    companion object {
        private const val SYNC_FILES_SHARED_PREFERENCES = "syncFiles"
        private const val FILES_KEY = "files"

        fun getLocalFilesToSynchronize(context: Context): List<String> {
            val pref = context.getSharedPreferences(SYNC_FILES_SHARED_PREFERENCES, Context.MODE_PRIVATE)
            val set = pref.getStringSet(FILES_KEY, Collections.emptySet())

            // TODO remove once we have functionality to add and remove entries
            if (set != null && set.isEmpty()) {
                //return listOf("Signal", "QTAudioEngine", "swiss_simple.mbtiles", "MapsWithMe/210201")
                return listOf("QTAudioEngine", "swiss_simple.mbtiles", "MapsWithMe/210201")
            }

            return ArrayList(set)
        }

        fun save(context: Context, localFilesToSynchronize: List<String>) {
            val pref = context.getSharedPreferences(SYNC_FILES_SHARED_PREFERENCES, Context.MODE_PRIVATE)
            val editor = pref.edit()
            editor.clear()
            editor.putStringSet(FILES_KEY, localFilesToSynchronize.toSet())
            editor.apply()
        }
    }
}