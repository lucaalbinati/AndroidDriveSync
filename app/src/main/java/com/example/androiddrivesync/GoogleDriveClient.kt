package com.example.androiddrivesync

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.webkit.MimeTypeMap
import android.widget.Toast
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
import java.io.FileNotFoundException
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
        private const val DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val UNKNOWN_FILE_MIME_TYPE = "application/octet-stream"

        private fun getFileFieldsDefaultSet(): HashSet<String> {
            return HashSet(setOf("id", "name"))
        }

        private enum class DriveFileStatus {
            NOT_PRESENT, PRESENT_OUTDATED, PRESENT
        }

        private const val BASE_STORAGE_DIR_NAME = "storage/emulated/0/"
        private val BASE_STORAGE_DIR = File(BASE_STORAGE_DIR_NAME)
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
        suspend fun synchroniseIteration(localRelativeFilepath: String) {
            val localFile = File(BASE_STORAGE_DIR, localRelativeFilepath)
            if (!localFile.exists()) {
                throw Exception("Local file with relative path '${localRelativeFilepath}' was not found")
            }

            val driveFile = File("$DRIVE_BACKUP_FOLDER/$localRelativeFilepath")

            if (localFile.isFile) {
                val filename = localFile.name
                val parentFolderId = getOrCreateDriveFolder(driveFile.parent!!)

                when (checkDriveFileStatus(localRelativeFilepath, filename, parentFolderId)) {
                    DriveFileStatus.NOT_PRESENT -> {
                        uploadLocalFileToDrive(localRelativeFilepath, filename, parentFolderId)
                    }
                    DriveFileStatus.PRESENT_OUTDATED -> {
                        deleteDriveFile(filename, parentFolderId)
                        uploadLocalFileToDrive(localRelativeFilepath, filename, parentFolderId)
                    }
                    DriveFileStatus.PRESENT -> {}
                }
            } else {
                //val driveFolderId = getOrCreateDriveFolder(driveFile.path)
                val files = localFile.listFiles()
                if (files != null) {
                    for (file in files) {
                        val newRelativePath = file.relativeTo(BASE_STORAGE_DIR).path
                        synchroniseIteration(newRelativePath)
                    }
                }
            }
        }

        var syncFiles = getSyncFiles()
        if (syncFiles.isEmpty()) {
            //syncFiles = setOf("Signal/signal-2021-04-07-15-40-48.backup", "Signal/")
            //syncFiles = setOf("Signal/signal-2021-04-07-15-40-48.backup", "Signal/")
            syncFiles = setOf("Signal/")
        }

        for (relativeFilepath in syncFiles) {
            synchroniseIteration(relativeFilepath)
        }
    }

    private fun getSyncFiles(): Set<String> {
        val pref = context.getSharedPreferences(SYNC_FILES_SHARED_PREFERENCES, MODE_PRIVATE)
        return Collections.unmodifiableSet(pref.getStringSet("files", Collections.emptySet()))
    }

    private suspend fun getDriveFileId(driveFileFilepath: String, isDir: Boolean? = false): String {
        return if (File(driveFileFilepath).parent == null) {
            getDriveFileId(driveFileFilepath, driveParentFolderId = null, isDir = isDir)
        } else {
            val driveFolderParentId = getDriveFileId(File(driveFileFilepath).parent!!, true)
            getDriveFileId(File(driveFileFilepath).name, driveFolderParentId, isDir)
        }
    }

    private suspend fun getDriveFileId(folderName: String, driveParentFolderId: String? = null, isDir: Boolean? = false): String {
        var query = "name='${folderName}' and trashed=False"
        if (driveParentFolderId != null) {
            query = "$query and ('${driveParentFolderId}' in parents)"
        }
        if (isDir == true) {
            query = "$query and mimeType='${DRIVE_FOLDER_MIME_TYPE}'"
        }

        val files = sendFilesDriveQuery(query, extraFileFields = setOf("parents"))

        if (files.size == 0) {
            throw FileNotFoundException("File '${folderName}' with parentFolderId='${driveParentFolderId}' not found")
        } else if (files.size >= 2) {
            throw FileNotFoundException("Expected 1 file '${folderName}' with parentFolderId='${driveParentFolderId}', but got ${files.size} instead")
        }

        return files[0]["id"] as String
    }

    private suspend fun checkDriveFileStatus(localRelativeFilepath: String, filename: String, driveParentFolderId: String): DriveFileStatus {
        val query = "name='${filename}' and ('${driveParentFolderId}' in parents) and trashed=False"
        val files = sendFilesDriveQuery(query, extraFileFields = setOf("modifiedTime"))

        if (files.size >= 2) {
            throw Exception("There are ${files.size} files with the same name (expected 1 or 0)")
        }

        if (files.size == 0) {
            return DriveFileStatus.NOT_PRESENT
        }

        val driveModifiedDate = (files[0]["modifiedTime"] as DateTime).value
        val localModifiedDate = File(BASE_STORAGE_DIR, localRelativeFilepath).lastModified()

        return if (driveModifiedDate < localModifiedDate) {
            DriveFileStatus.PRESENT_OUTDATED
        } else {
            DriveFileStatus.PRESENT
        }
    }

    private suspend fun deleteDriveFile(filename: String, driveParentFolderId: String) {
        withContext(Dispatchers.IO) {
            service.files().delete(driveParentFolderId).execute()
        }
    }

    private suspend fun uploadLocalFileToDrive(localRelativeFilepath: String, filename: String, driveParentFolderId: String) {
        val fileMetadata = com.google.api.services.drive.model.File()
            .setName(filename)
            .setParents(listOf(driveParentFolderId))

        val mimeType = try {
            getTypeFromFilename(filename)
        } catch (e: MimeTypeNotFoundException) {
            UNKNOWN_FILE_MIME_TYPE
        }

        val fileContent = FileContent(mimeType, File(BASE_STORAGE_DIR, localRelativeFilepath))

        withContext(Dispatchers.IO) {
            service.files().create(fileMetadata, fileContent).execute()
        }
    }

    private fun getTypeFromFilename(filename: String): String {
        val extension = File(filename).extension

        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: throw MimeTypeNotFoundException(context.getString(R.string.mime_type_not_found_exception_message, extension, filename))
    }

    private suspend fun createDriveFolder(driveFolderFilepath: String): String {
        val folderName = File(driveFolderFilepath).name
        val folderParentFilepath = File(driveFolderFilepath).parent

        return if (folderParentFilepath == null || folderParentFilepath.equals("/")) {
            createDriveFolder(folderName, null)
        } else {
            val parentFolderId = getOrCreateDriveFolder(folderParentFilepath)
            createDriveFolder(folderName, parentFolderId)
        }
    }

    private suspend fun createDriveFolder(folderName: String, driveParentFolderId: String? = null): String {
        val folderMetadata = com.google.api.services.drive.model.File()
            .setName(folderName)
            .setMimeType(DRIVE_FOLDER_MIME_TYPE)

        if (driveParentFolderId != null) {
            folderMetadata.parents = listOf(driveParentFolderId)
        }

        val response = withContext(Dispatchers.IO) {
            return@withContext service.files().create(folderMetadata).execute()
        }

        return response["id"] as String
    }

    private suspend fun getOrCreateDriveFolder(driveFolderFilepath: String): String {
        return try {
            getDriveFileId(driveFolderFilepath, true)
        } catch (e: FileNotFoundException) {
            createDriveFolder(driveFolderFilepath)
        }
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