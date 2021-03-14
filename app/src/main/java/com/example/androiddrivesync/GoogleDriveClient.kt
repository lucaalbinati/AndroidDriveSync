package com.example.androiddrivesync

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveRequest
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

class GoogleDriveClient(private val appName: String, private val authToken: String) {
    private val httpTransport = NetHttpTransport()
    private val jsonFactory = JacksonFactory.getDefaultInstance()
    private val service: Drive = Drive.Builder(httpTransport, jsonFactory, null).setApplicationName(appName).build()

    suspend fun listAllFiles(): Result<FileList> {
        return withContext(Dispatchers.IO) {
            val response = try {
                service.files().list().setOauthToken(authToken)
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