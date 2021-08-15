package com.example.androiddrivesync.synchronizeservice

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log

class SynchronizeSettingsSharedPreferencesHelper {
    companion object {
        private const val TAG = "SynchronizeSettingsSharedPreferencesHelper"
        private const val SYNCHRONIZE_SETTINGS_SHARED_PREFERENCES = "synchronizeSettings"
        private const val PERIODICITY = "periodicity"
        private val PERIODICITY_DEFAULT = SynchronizePeriodicity.DAILY.toString()

        fun setPeriodicity(context: Context, periodicity: SynchronizePeriodicity) {
            val editor = context.getSharedPreferences(SYNCHRONIZE_SETTINGS_SHARED_PREFERENCES, MODE_PRIVATE).edit()
            editor.putString(PERIODICITY, periodicity.toString())
            editor.apply()
        }

        fun getPeriodicity(context: Context): SynchronizePeriodicity {
            val pref = context.getSharedPreferences(SYNCHRONIZE_SETTINGS_SHARED_PREFERENCES, MODE_PRIVATE)

            if (!pref.contains(PERIODICITY)) {
                Log.i(TAG, "synchronize setting '$PERIODICITY' is missing; creating it and setting it to '$PERIODICITY_DEFAULT'")
                val editor = pref.edit()
                editor.putString(PERIODICITY, PERIODICITY_DEFAULT)
                editor.apply()
            }

            val periodicityString = pref.getString(PERIODICITY, PERIODICITY_DEFAULT)
            val periodicity = SynchronizePeriodicity.valueOf(periodicityString!!)
            Log.i(TAG, "synchronize periodicity is set to '$periodicity'")
            return periodicity
        }
    }
}