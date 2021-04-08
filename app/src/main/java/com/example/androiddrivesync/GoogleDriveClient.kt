package com.example.androiddrivesync

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.example.androiddrivesync.GoogleDriveUtility.Companion.checkDriveFileStatus
import com.example.androiddrivesync.GoogleDriveUtility.Companion.deleteDriveFile
import com.example.androiddrivesync.GoogleDriveUtility.Companion.getDriveFileId
import com.example.androiddrivesync.GoogleDriveUtility.Companion.getOrCreateDriveFolder
import com.example.androiddrivesync.GoogleDriveUtility.Companion.sendFilesDriveQuery
import com.example.androiddrivesync.GoogleDriveUtility.Companion.createDriveFile
import com.example.androiddrivesync.GoogleDriveUtility.Companion.getDriveFilesNotPresentLocally
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import kotlinx.coroutines.*
import java.io.File
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class GoogleDriveClient(private val context: Context, private val authCode: String) {
    companion object {
        private const val DRIVE_SHARED_PREFERENCES = "drive"
        private const val SYNC_FILES_SHARED_PREFERENCES = "syncFiles"
        private const val ACCESS_TOKEN_KEY_NAME = "accessToken"
        private const val EXPIRES_IN_SECONDS_KEY_NAME = "expiresInSeconds"

        private const val DRIVE_BACKUP_FOLDER = "Google Pixel 2 XL Backup"

        enum class DriveFileStatus {
            NOT_PRESENT, PRESENT_OUTDATED, PRESENT
        }

        private const val BASE_STORAGE_DIR_NAME = "storage/emulated/0/"
        val BASE_STORAGE_DIR = File(BASE_STORAGE_DIR_NAME)
    }

    private val httpTransport = NetHttpTransport()
    private val jacksonFactory = JacksonFactory.getDefaultInstance()
    private val service: Drive by lazy {
        suspend fun requestGoogleToken(): GoogleTokenResponse {
            val pref = context.getSharedPreferences(BootUpManager.CREDENTIALS_SHARED_PREFERENCES, MODE_PRIVATE)
            val clientId = pref.getString(BootUpManager.CLIENT_ID_KEY_NAME, null)
            val clientSecret = pref.getString(BootUpManager.CLIENT_SECRET_KEY_NAME, null)

            return withContext(Dispatchers.IO) {
                return@withContext GoogleAuthorizationCodeTokenRequest(httpTransport, jacksonFactory, clientId, clientSecret, authCode, "").execute()
            }
        }

        val pref = context.getSharedPreferences(DRIVE_SHARED_PREFERENCES, MODE_PRIVATE)
        val expiresInSeconds = pref.getLong(EXPIRES_IN_SECONDS_KEY_NAME, 0)

        if (!pref.contains(ACCESS_TOKEN_KEY_NAME) || expiresInSeconds < Calendar.getInstance().time.time) {
            val currTime = System.currentTimeMillis()
            val requestToken = runBlocking {
                return@runBlocking requestGoogleToken()
            }
            val edit = pref.edit()
            edit.putString(ACCESS_TOKEN_KEY_NAME, requestToken.accessToken)
            edit.putLong(EXPIRES_IN_SECONDS_KEY_NAME, currTime + 100 * requestToken.expiresInSeconds)
            edit.apply()
        }

        val accessToken = pref.getString(ACCESS_TOKEN_KEY_NAME, null)
        val credentials = GoogleCredential().setAccessToken(accessToken)

        return@lazy Drive.Builder(httpTransport, jacksonFactory, credentials)
            .setApplicationName(context.resources.getString(R.string.app_name))
            .build()
    }

    suspend fun synchronise() {
        suspend fun synchronise(localRelativeFilepath: String) {
            val localFile = File(BASE_STORAGE_DIR, localRelativeFilepath)
            if (!localFile.exists()) {
                throw Exception("Local file with relative path '${localRelativeFilepath}' was not found")
            }

            val driveFile = File("$DRIVE_BACKUP_FOLDER/$localRelativeFilepath")

            if (localFile.isFile) {
                val filename = localFile.name
                val parentFolderId = getOrCreateDriveFolder(service, driveFile.parent!!)

                when (checkDriveFileStatus(service, localRelativeFilepath, filename, parentFolderId)) {
                    DriveFileStatus.NOT_PRESENT -> {
                        createDriveFile(context, service, localRelativeFilepath, filename, parentFolderId)
                    }
                    DriveFileStatus.PRESENT_OUTDATED -> {
                        deleteDriveFile(service, filename, parentFolderId)
                        createDriveFile(context, service, localRelativeFilepath, filename, parentFolderId)
                    }
                    DriveFileStatus.PRESENT -> {}
                }
            } else {
                // Recursively synchronize child files and folders
                val localFiles = localFile.listFiles()!!
                for (file in localFiles) {
                    val childLocalRelativePath = file.relativeTo(BASE_STORAGE_DIR).path
                    synchronise(childLocalRelativePath)
                }

                // Delete files and folders present on the drive that aren't present locally anymore
                val driveFolderId = getDriveFileId(service, driveFile.path, true)
                val driveFilesNotPresentLocally = getDriveFilesNotPresentLocally(service, driveFolderId, localFiles)
                for (file in driveFilesNotPresentLocally) {
                    deleteDriveFile(service, file["name"] as String, driveFolderId)
                }
            }
        }

        var syncFiles = getSyncFiles()
        if (syncFiles.isEmpty()) {
            syncFiles = setOf("Signal/")
        }

        for (localRelativeFilepath in syncFiles) {
            synchronise(localRelativeFilepath)
        }
    }

    suspend fun listAllFiles(): Result<ArrayList<com.google.api.services.drive.model.File>> {
        val files = try {
            sendFilesDriveQuery(service, "trashed=False")
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(files)
    }

    private fun getSyncFiles(): Set<String> {
        val pref = context.getSharedPreferences(SYNC_FILES_SHARED_PREFERENCES, MODE_PRIVATE)
        return Collections.unmodifiableSet(pref.getStringSet("files", Collections.emptySet()))
    }

}