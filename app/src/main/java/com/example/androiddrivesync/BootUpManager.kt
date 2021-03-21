package com.example.androiddrivesync

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.core.content.ContextCompat.startActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import org.json.JSONObject
import java.io.BufferedReader
import java.lang.Exception

class BootUpManager {
    companion object {
        const val CREDENTIALS_SHARED_PREFERENCES = "credentials"
        const val CLIENT_ID_KEY_NAME = "client_id"
        const val CLIENT_SECRET_KEY_NAME = "client_secret"

        fun onBootUp(context: Context) {
            setupCredentialsSharedPreferences(context)
            googleSignIn(context)
        }

        private fun setupCredentialsSharedPreferences(context: Context) {
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

        private fun googleSignIn(context: Context) {
            if (GoogleSignIn.getLastSignedInAccount(context) == null) {
                startActivity(context, Intent(context, SignInActivity::class.java), null)
            } else {
                GoogleSignIn.getClient(context, SignInActivity.getGoogleSignInOptions(context, getClientId(context))).silentSignIn()
            }
        }

        fun getClientId(context: Context): String {
            val sharedPreferences = context.getSharedPreferences(CREDENTIALS_SHARED_PREFERENCES, MODE_PRIVATE)
            return sharedPreferences.getString(CLIENT_ID_KEY_NAME, null)!!
        }
    }
}