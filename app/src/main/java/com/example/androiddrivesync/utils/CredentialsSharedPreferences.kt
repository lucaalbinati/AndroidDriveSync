package com.example.androiddrivesync.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import com.example.androiddrivesync.BuildConfig

class CredentialsSharedPreferences {
    companion object {
        const val CREDENTIALS_SHARED_PREFERENCES = "credentials"
        const val CLIENT_ID_KEY = "clientId"
        const val CLIENT_SECRET = "clientSecret"

        fun setupCredentialsSharedPreferences(context: Context) {
            val sharedPreferences = context.getSharedPreferences(CREDENTIALS_SHARED_PREFERENCES, MODE_PRIVATE)

            putIfNotPresent(sharedPreferences, CLIENT_ID_KEY, BuildConfig.CLIENT_ID)
            putIfNotPresent(sharedPreferences, CLIENT_SECRET, BuildConfig.CLIENT_SECRET)
        }

        private fun putIfNotPresent(sharedPreferences: SharedPreferences, key: String, value: String) {
            if (!sharedPreferences.contains(key)) {
                val edit = sharedPreferences.edit()
                edit.putString(key, value)
                edit.apply()
            }
        }

        fun getClientId(context: Context): String {
            val sharedPreferences = context.getSharedPreferences(CREDENTIALS_SHARED_PREFERENCES, MODE_PRIVATE)

            if (!sharedPreferences.contains(CLIENT_ID_KEY)) {
                setupCredentialsSharedPreferences(context)
            }

            return sharedPreferences.getString(CLIENT_ID_KEY, null)!!
        }
    }
}