package com.example.androiddrivesync

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.example.androiddrivesync.GoogleDriveUtility.Companion.DRIVE_BACKUP_FOLDER
import com.example.androiddrivesync.GoogleDriveUtility.Companion.checkDriveFileStatus
import com.example.androiddrivesync.GoogleDriveUtility.Companion.createDriveFile
import com.example.androiddrivesync.GoogleDriveUtility.Companion.deleteDriveFile
import com.example.androiddrivesync.GoogleDriveUtility.Companion.getDriveFileId
import com.example.androiddrivesync.GoogleDriveUtility.Companion.getDriveFilesNotPresentLocally
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class GoogleDriveClient(private val context: Context, private val authCode: String) {
    companion object {
        private const val DRIVE_SHARED_PREFERENCES = "drive"
        private const val ACCESS_TOKEN_KEY_NAME = "accessToken"
        private const val EXPIRES_IN_SECONDS_KEY_NAME = "expiresInSeconds"

        private const val BASE_STORAGE_DIR_NAME = "storage/emulated/0/"
        val BASE_STORAGE_DIR = File(BASE_STORAGE_DIR_NAME)
    }

    private val httpTransport = NetHttpTransport()
    private val jacksonFactory = JacksonFactory.getDefaultInstance()
    private val service: Drive by lazy {
        suspend fun requestGoogleToken(): GoogleTokenResponse {
            val pref = context.getSharedPreferences(CredentialsSharedPreferences.CREDENTIALS_SHARED_PREFERENCES, MODE_PRIVATE)
            val clientId = pref.getString(CredentialsSharedPreferences.CLIENT_ID_KEY_NAME, null)
            val clientSecret = pref.getString(CredentialsSharedPreferences.CLIENT_SECRET_KEY_NAME, null)

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

    suspend fun synchronise(filesToSynchronize: List<String>, callback: (String, Utility.FileSyncStatus) -> Unit) {
        for (localRelativeFilepath in filesToSynchronize) {
            try {
                synchronise(localRelativeFilepath)
                callback(localRelativeFilepath, Utility.FileSyncStatus.SYNCED)
            } catch (e: Exception) {
                throw Exception("Unknown exception occurred during synchronization", e)
            }
        }

        // Delete files and folders present on the drive on the top level folder ('DRIVE_BACKUP_FOLDER') that aren't present locally
        val topFolderId = getDriveFileId(service, DRIVE_BACKUP_FOLDER, isDir = true)
        val topLevelDriveFilesNotPresentLocally = getDriveFilesNotPresentLocally(service, topFolderId, filesToSynchronize.map { f -> File(f) })
        topLevelDriveFilesNotPresentLocally.forEach { f ->
            deleteDriveFile(service, f["id"] as String)
        }
    }

    private suspend fun synchronise(localRelativeFilepath: String) {
        val localFile = File(BASE_STORAGE_DIR, localRelativeFilepath)
        if (!localFile.exists()) {
            throw Exception("Local file with relative path '${localRelativeFilepath}' was not found")
        }

        val driveFile = File("${DRIVE_BACKUP_FOLDER}/$localRelativeFilepath")

        if (localFile.isFile) {
            val filename = localFile.name
            val parentFolderId = getDriveFileId(service, driveFile.parent!!, isDir = true)

            when (val fileStatus = checkDriveFileStatus(service, localRelativeFilepath, filename, parentFolderId)) {
                Utility.FileSyncStatus.NOT_PRESENT -> {
                    createDriveFile(context, service, localRelativeFilepath, filename, parentFolderId)
                }
                Utility.FileSyncStatus.OUT_OF_SYNC -> {
                    deleteDriveFile(service, filename, parentFolderId)
                    createDriveFile(context, service, localRelativeFilepath, filename, parentFolderId)
                }
                Utility.FileSyncStatus.SYNCED -> {}
                Utility.FileSyncStatus.UNKNOWN -> throw Exception("File '$filename' has status '$fileStatus'")
            }
        } else {
            // Recursively synchronize child files and folders
            val localFiles = localFile.listFiles()!!.toList()
            for (file in localFiles) {
                val childLocalRelativePath = file.relativeTo(BASE_STORAGE_DIR).path
                synchronise(childLocalRelativePath)
            }

            // Delete files and folders present on the drive that aren't present locally
            val driveFolderId = getDriveFileId(service, driveFile.path, true)
            val driveFilesNotPresentLocally = getDriveFilesNotPresentLocally(service, driveFolderId, localFiles)
            for (file in driveFilesNotPresentLocally) {
                deleteDriveFile(service, file["name"] as String, driveFolderId)
            }
        }
    }

    suspend fun checkDriveStatus(filesToSynchronize: List<String>, callback: (String, Utility.FileSyncStatus) -> Unit): Map<String, Utility.FileSyncStatus> {
        val statusMap = HashMap<String, Utility.FileSyncStatus>()

        for (localRelativeFilepath in filesToSynchronize) {
            val status = checkFileOrFolderDriveStatus(localRelativeFilepath)
            statusMap[localRelativeFilepath] = status
            callback(localRelativeFilepath, status)
        }

        return statusMap
    }

    private suspend fun checkFileOrFolderDriveStatus(localRelativeFilepath: String): Utility.FileSyncStatus {
        suspend fun checkFileDriveStatus(localRelativeFilepath: String): Utility.FileSyncStatus {
            val filename = File(BASE_STORAGE_DIR, localRelativeFilepath).name
            val driveFile = File("${DRIVE_BACKUP_FOLDER}/$localRelativeFilepath")
            val parentFolderId = getDriveFileId(service, driveFile.parent!!, isDir = true)
            return checkDriveFileStatus(service, localRelativeFilepath, filename, parentFolderId)
        }

        val localFile = File(BASE_STORAGE_DIR, localRelativeFilepath)
        if (!localFile.exists()) {
            throw Exception("Local file with relative path '${localRelativeFilepath}' was not found")
        }

        if (localFile.isFile) {
            return checkFileDriveStatus(localRelativeFilepath)
        } else {
            val localFiles = localFile.listFiles()!!
            val localFilesStatus = localFiles.map { f -> checkFileOrFolderDriveStatus(f.relativeTo(BASE_STORAGE_DIR).path) }

            return if (localFilesStatus.isEmpty()) {
                Utility.FileSyncStatus.UNKNOWN
            } else {
                localFilesStatus.reduce { a, b ->
                    if (a == Utility.FileSyncStatus.UNKNOWN || b == Utility.FileSyncStatus.UNKNOWN) {
                        return Utility.FileSyncStatus.UNKNOWN
                    }

                    if (a == Utility.FileSyncStatus.OUT_OF_SYNC || b == Utility.FileSyncStatus.OUT_OF_SYNC) {
                        return Utility.FileSyncStatus.OUT_OF_SYNC
                    }

                    if (a == Utility.FileSyncStatus.NOT_PRESENT || b == Utility.FileSyncStatus.NOT_PRESENT) {
                        return Utility.FileSyncStatus.OUT_OF_SYNC
                    }

                    return Utility.FileSyncStatus.SYNCED
                }
            }
        }
    }

}