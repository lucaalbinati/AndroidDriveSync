package com.example.androiddrivesync

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.util.*

class GoogleDriveClient(context: Context, private val authCode: String) {
    companion object {
        private const val DRIVE_SHARED_PREFERENCES = "drive"
        private const val ACCESS_TOKEN_KEY_NAME = "accessToken"
        private const val REFRESH_TOKEN_KEY_NAME = "refreshToken"
        private const val EXPIRES_IN_SECONDS_KEY_NAME = "expiresInSeconds"
    }

    private val httpTransport = NetHttpTransport()
    private val jacksonFactory = JacksonFactory.getDefaultInstance()
    private val service = getDriveService(context)

    private fun getDriveService(context: Context): Drive {
        val pref = context.getSharedPreferences(DRIVE_SHARED_PREFERENCES, MODE_PRIVATE)
        if (!pref.contains(ACCESS_TOKEN_KEY_NAME)) {
            val currTime = System.currentTimeMillis()
            val requestToken = runBlocking {
                return@runBlocking requestGoogleToken(context)
            }
            val edit = pref.edit()
            edit.putString(ACCESS_TOKEN_KEY_NAME, requestToken.accessToken)
            //edit.putString(REFRESH_TOKEN_KEY_NAME, requestToken.refreshToken)
            edit.putLong(EXPIRES_IN_SECONDS_KEY_NAME, currTime + 100 * requestToken.expiresInSeconds)
            edit.apply()
        }

        val expiresInSeconds = pref.getLong(EXPIRES_IN_SECONDS_KEY_NAME, 0)
        if (expiresInSeconds < Calendar.getInstance().time.time) {
            val currTime = System.currentTimeMillis()
            val requestToken = runBlocking {
                return@runBlocking requestGoogleToken(context)
            }
            val edit = pref.edit()
            edit.putString(ACCESS_TOKEN_KEY_NAME, requestToken.accessToken)
            //edit.putString(REFRESH_TOKEN_KEY_NAME, requestToken.refreshToken)
            edit.putLong(EXPIRES_IN_SECONDS_KEY_NAME, currTime + 100 * requestToken.expiresInSeconds)
            edit.apply()
        }
        val accessToken = pref.getString(ACCESS_TOKEN_KEY_NAME, null)

        val credentials = GoogleCredential().setAccessToken(accessToken)

        return Drive.Builder(httpTransport, jacksonFactory, credentials)
            .setApplicationName(context.resources.getString(R.string.app_name))
            .build()
    }

    private suspend fun requestGoogleToken(context: Context): GoogleTokenResponse {
        val pref = context.getSharedPreferences(BootUpManager.CREDENTIALS_SHARED_PREFERENCES, MODE_PRIVATE)
        val clientId = pref.getString(BootUpManager.CLIENT_ID_KEY_NAME, null)
        val clientSecret = pref.getString(BootUpManager.CLIENT_SECRET_KEY_NAME, null)

        return withContext(Dispatchers.IO) {
            return@withContext GoogleAuthorizationCodeTokenRequest(httpTransport, jacksonFactory, clientId, clientSecret, authCode, "").execute()
        }
    }

    suspend fun listAllFiles(): Result<FileList> {
        return withContext(Dispatchers.IO) {
            val response = try {
                service.files().list()
                    .setQ("trashed=False")
                    .setSpaces("drive")
                    .setPageSize(10)
                    .setFields("nextPageToken, files(id, name)")
                    .execute()
            } catch (e: Exception) {
                return@withContext Result.failure<FileList>(e)
            }
            return@withContext Result.success(response)
        }
    }

}