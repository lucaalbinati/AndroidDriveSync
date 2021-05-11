package com.example.androiddrivesync

import android.content.Context
import java.util.*
import kotlin.collections.ArrayList

class LocalFilesToSynchronizeHandler {
    companion object {
        private const val SYNC_FILES_SHARED_PREFERENCES = "syncFiles"
        private const val FILES_KEY = "files"

        fun getLocalFilesToSynchronize(context: Context): ArrayList<String> {
            // TODO change once we add functionality for the user to select the files to synchronize
            // return getRealLocalFilesToSynchronize(context)
            return getFakeLocalFilesToSynchronize()
        }

        private fun getRealLocalFilesToSynchronize(context: Context): ArrayList<String> {
            val pref = context.getSharedPreferences(SYNC_FILES_SHARED_PREFERENCES, Context.MODE_PRIVATE)
            val set = pref.getStringSet(FILES_KEY, Collections.emptySet())
            return ArrayList(set)
        }

        private fun getFakeLocalFilesToSynchronize(): ArrayList<String> {
            return arrayListOf("Signal", "QTAudioEngine")
        }

        fun save(context: Context, localFilesToSynchronize: ArrayList<String>) {
            val pref = context.getSharedPreferences(SYNC_FILES_SHARED_PREFERENCES, Context.MODE_PRIVATE)
            val editor = pref.edit()
            editor.clear()
            editor.putStringSet(FILES_KEY, localFilesToSynchronize.toSet())
            editor.apply()
        }
    }
}