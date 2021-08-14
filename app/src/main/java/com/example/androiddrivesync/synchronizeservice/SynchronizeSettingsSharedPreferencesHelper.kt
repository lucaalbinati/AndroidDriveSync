package com.example.androiddrivesync.synchronizeservice

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log

class SynchronizeSettingsSharedPreferencesHelper {
    companion object {
        private const val TAG = "SynchronizeSettingsSharedPreferencesHelper"
        private const val SYNCHRONIZE_SETTINGS_SHARED_PREFERENCES = "synchronizeSettings"
        private const val ENABLED = "enabled"
        private const val ENABLED_DEFAULT = true
        private const val PERIODICITY = "periodicity"
        private val PERIODICITY_DEFAULT = SynchronizePeriodicity.DAILY.toString()

        fun setEnable(context: Context, enabled: Boolean) {
            val editor = context.getSharedPreferences(SYNCHRONIZE_SETTINGS_SHARED_PREFERENCES, MODE_PRIVATE).edit()
            editor.putBoolean(ENABLED, enabled)
            editor.apply()
        }

        fun isEnabled(context: Context): Boolean {
            val pref = context.getSharedPreferences(SYNCHRONIZE_SETTINGS_SHARED_PREFERENCES, MODE_PRIVATE)

            if (!pref.contains(ENABLED)) {
                Log.i(TAG, "synchronize setting '$ENABLED' is missing; creating it and setting it to '$ENABLED_DEFAULT'")
                val editor = pref.edit()
                editor.putBoolean(ENABLED, ENABLED_DEFAULT)
                editor.apply()
            }

            val isEnabled = pref.getBoolean(ENABLED, ENABLED_DEFAULT)
            Log.i(TAG, "synchronize is set to '$isEnabled'")
            return isEnabled
        }

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