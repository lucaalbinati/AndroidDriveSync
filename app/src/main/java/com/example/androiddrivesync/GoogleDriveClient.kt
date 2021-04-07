package com.example.androiddrivesync

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.webkit.MimeTypeMap
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import kotlinx.coroutines.*
import java.io.File
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class GoogleDriveClient(private val context: Context, private val authCode: String) {
    companion object {
        private const val DRIVE_SHARED_PREFERENCES = "drive"
        private const val SYNC_FILES_SHARED_PREFERENCES = "syncFiles"
        private const val ACCESS_TOKEN_KEY_NAME = "accessToken"
        private const val EXPIRES_IN_SECONDS_KEY_NAME = "expiresInSeconds"

        private const val DRIVE_BACKUP_FOLDER = "Google Pixel 2 XL Backup"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

        private fun getFileFieldsDefaultSet(): HashSet<String> {
            return HashSet(setOf("id", "name"))
        }

        private enum class DriveFileStatus {
            NOT_PRESENT, PRESENT_OUTDATED, PRESENT
        }
    }

    private val httpTransport = NetHttpTransport()
    private val jacksonFactory = JacksonFactory.getDefaultInstance()
    private val service = getDriveService()

    private fun getDriveService(): Drive {
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

        return Drive.Builder(httpTransport, jacksonFactory, credentials)
            .setApplicationName(context.resources.getString(R.string.app_name))
            .build()
    }

    private suspend fun requestGoogleToken(): GoogleTokenResponse {
        val pref = context.getSharedPreferences(BootUpManager.CREDENTIALS_SHARED_PREFERENCES, MODE_PRIVATE)
        val clientId = pref.getString(BootUpManager.CLIENT_ID_KEY_NAME, null)
        val clientSecret = pref.getString(BootUpManager.CLIENT_SECRET_KEY_NAME, null)

        return withContext(Dispatchers.IO) {
            return@withContext GoogleAuthorizationCodeTokenRequest(httpTransport, jacksonFactory, clientId, clientSecret, authCode, "").execute()
        }
    }

    suspend fun synchronise() {
//        suspend fun synchroniseIteration(filename: String, localDir: String, driveParentFolderId: String) {
//            if (!fileExistsLocally(localDir, filename)) {
//                throw Exception("File with path '${File(localDir, filename).path}' was not found locally")
//            }
//
//            when (checkDriveFileStatus(filename, localDir, driveParentFolderId)) {
//                DriveFileStatus.PRESENT_OUTDATED -> {
//                    deleteDriveFile(filename, driveParentFolderId)
//                    //uploadLocalFileToDrive(filename, localDir, driveParentFolderId)
//                }
//                DriveFileStatus.NOT_PRESENT -> {
//                    //uploadLocalFileToDrive(filename, localDir, driveParentFolderId)
//                }
//                DriveFileStatus.PRESENT -> {
//                    // nothing
//                }
//            }
//        }

        suspend fun synchroniseIteration2(relativeFilepath: String) {
            if (!fileExistsLocally(relativeFilepath)) {
                throw Exception("File with relative path '${relativeFilepath}' was not found locally")
            }

            val files = File(getBaseDir(), relativeFilepath).listFiles()
            for (file in files) {
                print(file.exists())
            }
        }

        var syncFiles = getSyncFiles()
        if (syncFiles.isEmpty()) {
            syncFiles = setOf("Signal/", "Documents/AndroidDriveSync/credentials.json")
        }

        //val driveParentFolderId = getBackupDriveFolderId()
        for (relativeFilepath in syncFiles) {
            synchroniseIteration2(relativeFilepath)
        }
    }

    private fun getBaseDir(): File {
        return File(context.getExternalFilesDir(null)!!.absolutePath)
    }

    private fun getSyncFiles(): Set<String> {
        val pref = context.getSharedPreferences(SYNC_FILES_SHARED_PREFERENCES, MODE_PRIVATE)
        return Collections.unmodifiableSet(pref.getStringSet("files", Collections.emptySet()))
    }

    private fun fileExistsLocally(relativeFilepath: String): Boolean {
        return File(getBaseDir(), relativeFilepath).exists()
    }

    private fun getRelativeParentPath(filepath: String): String {
        val baseDir = getBaseDir()
        val file = File(filepath)
        return file.relativeTo(baseDir).path
    }

    private fun isDir(filepath: String): Boolean {
        return File(filepath).isDirectory
    }

    private suspend fun getBackupDriveFolderId(): String {
        return try {
            getDriveFolderId(DRIVE_BACKUP_FOLDER)
        } catch (e: FolderNotFound) {
            return createFolderOnDrive(DRIVE_BACKUP_FOLDER)
        }
    }

    private suspend fun getDriveFolderId(driveFilepath: String): String {
        val query = "name='${driveFilepath}' and mimeType='${FOLDER_MIME_TYPE}' and trashed=False"
        val files = sendFilesDriveQuery(query)

        if (files.size != 1) {
            throw FolderNotFound("Expected 1 folder '${driveFilepath}' but got ${files.size}")
        }

        return files[0]["id"] as String
    }

    private suspend fun checkDriveFileStatus(filename: String, localDir: String, driveParentFolderId: String): DriveFileStatus {
        val query = "name='${filename}' and ('${driveParentFolderId}' in parents) and trashed=False"
        val files = sendFilesDriveQuery(query, extraFileFields = setOf("modifiedTime"))

        if (files.size >= 2) {
            throw Exception("There are ${files.size} files with the same name (should only be 1 or 0)")
        }

        if (files.size == 0) {
            return DriveFileStatus.NOT_PRESENT
        }

        // TODO make sure they are of the same type
        val driveModifiedDate = (files[0]["modifiedTime"] as DateTime).value
        val localModifiedDate = getLocalFileModifiedDate(filename, localDir)

        return if (driveModifiedDate < localModifiedDate) {
            DriveFileStatus.PRESENT_OUTDATED
        } else {
            DriveFileStatus.PRESENT
        }
    }

    private fun getLocalFileModifiedDate(filename: String, dir: String): Long {
        return File(dir, filename).lastModified()
    }

    private suspend fun deleteDriveFile(filename: String, driveParentFolderId: String) {
        withContext(Dispatchers.IO) {
            service.files().delete(driveParentFolderId).execute()
        }
    }

    private suspend fun uploadLocalFileToDrive(filename: String, localDir: String, driveParentFolderId: String) {
        val fileMetadata = com.google.api.services.drive.model.File()
            .setName(filename)
            .setParents(listOf(driveParentFolderId))

        val fileContent = FileContent(getTypeFromFilename(filename), File(localDir, filename))

        withContext(Dispatchers.IO) {
            service.files().create(fileMetadata, fileContent).execute()
        }
    }

    private fun getTypeFromFilename(filename: String): String {
        val extension = File(filename).extension

        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: throw Exception("MimeType not found for extension '${extension}', from filename '${filename}'")
    }

    private suspend fun createFolderOnDrive(name: String, driveParentFolderId: String? = null): String {
        val folderMetadata = com.google.api.services.drive.model.File()
            .setName(name)
            .setMimeType(FOLDER_MIME_TYPE)
            .setParents(listOf(driveParentFolderId)) // TODO what if 'driveParentFolderId' is null?

        val response = withContext(Dispatchers.IO) {
            // TODO check if 'setFields' is needed
            return@withContext service.files().create(folderMetadata).setFields("id").execute()
        }

        return response["id"] as String
    }

    private suspend fun sendFilesDriveQuery(query: String, extraFileFields: Set<String> = setOf()): ArrayList<com.google.api.services.drive.model.File> {
        val fileFieldsSet = getFileFieldsDefaultSet()
        fileFieldsSet.addAll(extraFileFields)
        val fields = "nextPageToken, files(${(fileFieldsSet as Set<String>).joinToString(", ")})"

        val response = withContext(Dispatchers.IO) {
            return@withContext service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setPageSize(10)
                .setFields(fields)
                .execute()
        }

        return response["files"] as ArrayList<com.google.api.services.drive.model.File>
    }

    suspend fun listAllFiles(): Result<ArrayList<com.google.api.services.drive.model.File>> {
        val files = try {
            sendFilesDriveQuery("trashed=False")
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(files)
    }

}