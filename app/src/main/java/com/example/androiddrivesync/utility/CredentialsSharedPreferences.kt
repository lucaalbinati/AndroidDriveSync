package com.example.androiddrivesync.utility

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.res.Resources
import com.example.androiddrivesync.R
import org.json.JSONObject
import java.io.BufferedReader

class CredentialsSharedPreferences {
    companion object {
        const val CREDENTIALS_SHARED_PREFERENCES = "credentials"
        const val CLIENT_ID_KEY_NAME = "client_id"
        const val CLIENT_SECRET_KEY_NAME = "client_secret"

        fun setupCredentialsSharedPreferences(context: Context) {
            val sharedPreferences = context.getSharedPreferences(CREDENTIALS_SHARED_PREFERENCES, MODE_PRIVATE)

            val inputStream = try {
                context.resources.openRawResource(R.raw.credentials)
            } catch (e: Resources.NotFoundException) {
                throw Exception("The 'credentials.json' file could not be found in R.raw. Please add it.", e)
            }
            val jsonString = inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(jsonString)

            val clientId = (json.get("web") as JSONObject).get(CLIENT_ID_KEY_NAME).toString()
            val clientSecret = (json.get("web") as JSONObject).get(CLIENT_SECRET_KEY_NAME).toString()

            putIfNotPresent(sharedPreferences, CLIENT_ID_KEY_NAME, clientId)
            putIfNotPresent(sharedPreferences, CLIENT_SECRET_KEY_NAME, clientSecret)
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

            if (!sharedPreferences.contains(CLIENT_ID_KEY_NAME)) {
                setupCredentialsSharedPreferences(context)
            }

            return sharedPreferences.getString(CLIENT_ID_KEY_NAME, null)!!
        }
    }
}