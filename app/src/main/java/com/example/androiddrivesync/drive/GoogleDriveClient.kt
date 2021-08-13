package com.example.androiddrivesync.drive

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import com.example.androiddrivesync.R
import com.example.androiddrivesync.drive.GoogleDriveUtility.Companion.DRIVE_BACKUP_FOLDER
import com.example.androiddrivesync.drive.GoogleDriveUtility.Companion.checkDriveFileStatus
import com.example.androiddrivesync.drive.GoogleDriveUtility.Companion.createDriveFile
import com.example.androiddrivesync.drive.GoogleDriveUtility.Companion.deleteDriveFile
import com.example.androiddrivesync.drive.GoogleDriveUtility.Companion.getDriveFileId
import com.example.androiddrivesync.drive.GoogleDriveUtility.Companion.getDriveFilesNotPresentLocally
import com.example.androiddrivesync.utility.CredentialsSharedPreferences
import com.example.androiddrivesync.utility.Utility
import com.google.api.client.googleapis.auth.oauth2.*
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class GoogleDriveClient(private val context: Context, authCode: String) {
    companion object {
        private const val TAG = "GoogleDriveClient"
        const val DRIVE_SHARED_PREFERENCES = "drive"
        private const val SERVER_AUTHENTICATION_CODE_KEY = "serverAuthenticationCode"
        private const val ID_TOKEN_KEY = "idToken"
        private const val ACCESS_TOKEN_KEY = "accessToken"
        const val REFRESH_TOKEN_KEY = "refreshToken"
        private const val BASE_STORAGE_DIR_NAME = "storage/emulated/0/"
        val BASE_STORAGE_DIR = File(BASE_STORAGE_DIR_NAME)

        fun setupGoogleDriveClient(context: Context): GoogleDriveClient {
            val pref = context.getSharedPreferences(DRIVE_SHARED_PREFERENCES, MODE_PRIVATE)
            val serverAuthCode: String = pref.getString(SERVER_AUTHENTICATION_CODE_KEY, null)
                ?: throw Exception("Server authentication code is null")
            return GoogleDriveClient(context, serverAuthCode)
        }

        fun saveServerAuthenticationCode(context: Context, serverAuthCode: String) {
            val pref = context.getSharedPreferences(DRIVE_SHARED_PREFERENCES, MODE_PRIVATE)
            val editor = pref.edit()
            editor.putString(SERVER_AUTHENTICATION_CODE_KEY, serverAuthCode)
            editor.apply()
            Log.i(TAG, "saved server authentication code '$serverAuthCode' to '${DRIVE_SHARED_PREFERENCES}' shared preferences file")
        }
    }

    private val httpTransport = NetHttpTransport()
    private val jsonFactory = JacksonFactory.getDefaultInstance()
    private val service: Drive = initService(authCode)

    private fun initService(authorizationCode: String): Drive {
        suspend fun requestAccessToken(refreshToken: String? = null): GoogleTokenResponse {
            val pref = context.getSharedPreferences(CredentialsSharedPreferences.CREDENTIALS_SHARED_PREFERENCES, MODE_PRIVATE)
            val clientId = pref.getString(CredentialsSharedPreferences.CLIENT_ID_KEY_NAME, null)
            val clientSecret = pref.getString(CredentialsSharedPreferences.CLIENT_SECRET_KEY_NAME, null)

            return withContext(Dispatchers.IO) {
                val tokenResponse = if (refreshToken == null) {
                    GoogleAuthorizationCodeTokenRequest(httpTransport, jsonFactory, clientId, clientSecret, authorizationCode, "").execute()
                } else {
                    GoogleRefreshTokenRequest(httpTransport, jsonFactory, refreshToken, clientId, clientSecret).execute()
                }
                Log.i(TAG, "received token response $tokenResponse")
                Log.i(TAG, "received '$ACCESS_TOKEN_KEY': ${tokenResponse.accessToken}")
                Log.i(TAG, "received '$REFRESH_TOKEN_KEY': ${tokenResponse.refreshToken}")
                if (tokenResponse.refreshToken == null) {
                    throw Exception("Expected to receive a '$REFRESH_TOKEN_KEY' but got none.")
                }
                val edit = context.getSharedPreferences(DRIVE_SHARED_PREFERENCES, MODE_PRIVATE).edit()
                edit.putString(ID_TOKEN_KEY, tokenResponse.idToken)
                edit.putString(ACCESS_TOKEN_KEY, tokenResponse.accessToken)
                edit.putString(REFRESH_TOKEN_KEY, tokenResponse.refreshToken)
                edit.apply()
                Log.i(TAG, "saved '$ID_TOKEN_KEY', '$ACCESS_TOKEN_KEY' and '$REFRESH_TOKEN_KEY' to '$DRIVE_SHARED_PREFERENCES' shared preferences file")
                return@withContext tokenResponse
            }
        }

        val pref = context.getSharedPreferences(DRIVE_SHARED_PREFERENCES, MODE_PRIVATE)

        if (!pref.contains(ID_TOKEN_KEY)) {
            Log.i(TAG, "requesting access token for the first time")
            runBlocking {
                requestAccessToken()
            }
        } else {
            val idToken = pref.getString(ID_TOKEN_KEY, null)
            val googleIdToken = GoogleIdToken.parse(jsonFactory, idToken)

            if (googleIdToken.verifyExpirationTime(Calendar.getInstance().timeInMillis, 0)
                && googleIdToken.verifyIssuer(listOf("accounts.google.com", "https://accounts.google.com"))) {
                Log.i(TAG, "access token is present and valid")
            } else {
                Log.i(TAG, "requesting access token again (because the token is invalid) using the refresh token")
                runBlocking {
                    val refreshToken = pref.getString(REFRESH_TOKEN_KEY, null)
                        ?: throw Exception("Refresh token is missing")
                    requestAccessToken(refreshToken)
                }
            }
        }

        val accessToken = pref.getString(ACCESS_TOKEN_KEY, null)
        Log.i(TAG, "retrieved access token '$accessToken'")
        val credentials = GoogleCredential().setAccessToken(accessToken)

        return Drive.Builder(httpTransport, jsonFactory, credentials)
            .setApplicationName(context.resources.getString(R.string.app_name))
            .build()
    }

    suspend fun synchronise(filesSyncActions: List<Utility.FileSyncAction>, callback: (String?, Utility.FileSyncStatus) -> Unit) {
        for (action in filesSyncActions) {
            try {
                when (action.syncStatus) {
                    Utility.FileSyncStatus.NOT_PRESENT -> {
                        createDriveFile(context, service, action.localRelativeFilepath!!, action.filename, action.parentFolderId!!)
                        callback(action.localRelativeFilepath, Utility.FileSyncStatus.SYNCED)
                    }
                    Utility.FileSyncStatus.OUT_OF_SYNC -> {
                        deleteDriveFile(service, action.filename, action.parentFolderId!!)
                        createDriveFile(context, service, action.localRelativeFilepath!!, action.filename, action.parentFolderId)
                        callback(action.localRelativeFilepath, Utility.FileSyncStatus.SYNCED)
                    }
                    Utility.FileSyncStatus.TO_BE_DELETED -> {
                        deleteDriveFile(service, action.filename, action.parentFolderId!!)
                    }
                    Utility.FileSyncStatus.TO_BE_DELETED_FROM_BASE_DIR -> {
                        deleteDriveFile(service, action.parentFolderId!!)
                    }
                    Utility.FileSyncStatus.SYNCED -> {}
                    Utility.FileSyncStatus.UNKNOWN -> throw Exception("File '${action.filename}' has status '${action.syncStatus}'")
                }
            } catch (e: Exception) {
                throw Exception("Unknown exception occurred during synchronization", e)
            }
        }
    }

    suspend fun getFileSyncActions(filesToSynchronize: List<String>): List<Utility.FileSyncAction> {
        val fileSyncActions = ArrayList<Utility.FileSyncAction>()

        for (localRelativeFilepath in filesToSynchronize) {
            fileSyncActions.addAll(getFileSyncAction(localRelativeFilepath))
        }

        // Delete files and folders present on the drive on the top level folder ('DRIVE_BACKUP_FOLDER') that aren't present locally
        val topFolderId = getDriveFileId(service, DRIVE_BACKUP_FOLDER, isDir = true)
        val topLevelDriveFilesNotPresentLocally = getDriveFilesNotPresentLocally(service, topFolderId, filesToSynchronize.map { f -> File(f) })
        topLevelDriveFilesNotPresentLocally.forEach { f ->
            fileSyncActions.add(Utility.FileSyncAction(Utility.FileSyncStatus.TO_BE_DELETED_FROM_BASE_DIR, f.name, f["id"] as String))
        }

        return fileSyncActions
    }

    private suspend fun getFileSyncAction(localRelativeFilepath: String): List<Utility.FileSyncAction> {
        val localFile = File(BASE_STORAGE_DIR, localRelativeFilepath)
        if (!localFile.exists()) {
            throw Exception("Local file with relative path '${localRelativeFilepath}' was not found")
        }

        val driveFile = File("${DRIVE_BACKUP_FOLDER}/$localRelativeFilepath")

        val fileSyncActions = ArrayList<Utility.FileSyncAction>()

        if (localFile.isFile) {
            val filename = localFile.name
            val parentFolderId = getDriveFileId(service, driveFile.parent!!, isDir = true)
            val syncStatus = checkDriveFileStatus(service, localRelativeFilepath, filename, parentFolderId)
            fileSyncActions.add(Utility.FileSyncAction(syncStatus, filename, localRelativeFilepath, parentFolderId))
        } else {
            // Recursively synchronize child files and folders
            val localFiles = localFile.listFiles()!!.toList()
            for (file in localFiles) {
                val childLocalRelativePath = file.relativeTo(BASE_STORAGE_DIR).path
                fileSyncActions.addAll(getFileSyncAction(childLocalRelativePath))
            }

            // Delete files and folders present on the drive that aren't present locally
            val driveFolderId = getDriveFileId(service, driveFile.path, true)
            val driveFilesNotPresentLocally = getDriveFilesNotPresentLocally(service, driveFolderId, localFiles)
            for (file in driveFilesNotPresentLocally) {
                fileSyncActions.add(Utility.FileSyncAction(Utility.FileSyncStatus.TO_BE_DELETED, file.name, driveFolderId))
            }
        }

        return fileSyncActions
    }

    fun getFilesToUploadSize(fileSyncActions: List<Utility.FileSyncAction>): Long {
        var totalLength: Long = 0

        for (fileSyncAction in fileSyncActions) {
            if (fileSyncAction.syncStatus == Utility.FileSyncStatus.OUT_OF_SYNC || fileSyncAction.syncStatus == Utility.FileSyncStatus.NOT_PRESENT) {
                totalLength += File(BASE_STORAGE_DIR, fileSyncAction.localRelativeFilepath!!).length()
            }
        }

        return totalLength
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
            val localFilesStatus = localFiles.map { f -> checkFileOrFolderDriveStatus(f.relativeTo(
                BASE_STORAGE_DIR
            ).path) }

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