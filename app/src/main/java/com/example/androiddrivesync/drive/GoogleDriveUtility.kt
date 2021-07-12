package com.example.androiddrivesync.drive

import android.content.Context
import com.example.androiddrivesync.utility.MimeTypeNotFoundException
import com.example.androiddrivesync.utility.Utility
import com.google.api.client.http.FileContent
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class GoogleDriveUtility {
    companion object {
        const val DRIVE_BACKUP_FOLDER = "Google Pixel 2 XL Backup"

        private const val DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val UNKNOWN_FILE_MIME_TYPE = "application/octet-stream"

        private fun getFileFieldsDefaultSet(): HashSet<String> {
            return HashSet(setOf("id", "name"))
        }

        suspend fun getDriveFileId(service: Drive, driveFileFilepath: String, isDir: Boolean? = false): String {
            val parentDriveFile = File(driveFileFilepath).parentFile

            return if (parentDriveFile == null) {
                try {
                    getDriveFileId(service, driveFileFilepath, driveParentFolderId = null, isDir = isDir)
                } catch (e: FileNotFoundException) {
                    createDriveFolder(service, driveFileFilepath)
                }
            } else {
                val driveFolderParentId = getDriveFileId(service, parentDriveFile.path, true)

                try {
                    getDriveFileId(service, File(driveFileFilepath).name, driveFolderParentId, isDir)
                } catch (e: FileNotFoundException) {
                    createDriveFolder(service, File(driveFileFilepath).name, driveFolderParentId)
                }
            }
        }

        suspend fun getDriveFileId(service: Drive, folderName: String, driveParentFolderId: String? = null, isDir: Boolean? = false): String {
            var query = "name='${folderName}' and trashed=False"
            if (driveParentFolderId != null) {
                query = "$query and ('${driveParentFolderId}' in parents)"
            }
            if (isDir == true) {
                query = "$query and mimeType='$DRIVE_FOLDER_MIME_TYPE'"
            }

            val files = sendFilesDriveQuery(service, query, extraFileFields = setOf("parents"))

            if (files.size == 0) {
                throw FileNotFoundException("File '${folderName}' with parentFolderId='${driveParentFolderId}' not found")
            } else if (files.size >= 2) {
                throw FileNotFoundException("Expected 1 file '${folderName}' with parentFolderId='${driveParentFolderId}', but got ${files.size} instead")
            }

            return files[0]["id"] as String
        }

        private suspend fun createDriveFolder(service: Drive, folderName: String, driveParentFolderId: String? = null): String {
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

        suspend fun createDriveFile(context: Context, service: Drive, localRelativeFilepath: String, filename: String, driveParentFolderId: String) {
            val fileMetadata = com.google.api.services.drive.model.File()
                .setName(filename)
                .setParents(listOf(driveParentFolderId))

            val mimeType = try {
                Utility.getTypeFromFilename(context, filename)
            } catch (e: MimeTypeNotFoundException) {
                UNKNOWN_FILE_MIME_TYPE
            }

            val fileContent = FileContent(mimeType, File(GoogleDriveClient.BASE_STORAGE_DIR, localRelativeFilepath))

            withContext(Dispatchers.IO) {
                service.files().create(fileMetadata, fileContent).execute()
            }
        }

        suspend fun deleteDriveFile(service: Drive, filename: String, parentFolderId: String) {
            val fileId = getDriveFileId(service, filename, parentFolderId)
            return deleteDriveFile(service, fileId)
        }

        suspend fun deleteDriveFile(service: Drive, fileId: String) {
            withContext(Dispatchers.IO) {
                service.files().delete(fileId).execute()
            }
        }

        suspend fun checkDriveFileStatus(service: Drive, localRelativeFilepath: String, filename: String, driveParentFolderId: String): Utility.FileSyncStatus {
            val query = "name='${filename}' and ('${driveParentFolderId}' in parents) and trashed=False"
            val files = sendFilesDriveQuery(service, query, extraFileFields = setOf("modifiedTime"))

            if (files.size >= 2) {
                throw Exception("There are ${files.size} files with the same name (expected 1 or 0)")
            }

            if (files.size == 0) {
                return Utility.FileSyncStatus.NOT_PRESENT
            }

            val driveModifiedDate = (files[0]["modifiedTime"] as DateTime).value
            val localModifiedDate = File(GoogleDriveClient.BASE_STORAGE_DIR, localRelativeFilepath).lastModified()

            return if (driveModifiedDate < localModifiedDate) {
                Utility.FileSyncStatus.OUT_OF_SYNC
            } else {
                Utility.FileSyncStatus.SYNCED
            }
        }

        suspend fun getDriveFilesNotPresentLocally(service: Drive, driveFolderId: String, localFiles: List<File>): List<com.google.api.services.drive.model.File> {
            val query = "'${driveFolderId}' in parents and trashed=False"
            val driveFiles = sendFilesDriveQuery(service, query)

            val localFilesNames = localFiles.map { f ->
                if (f.isFile) {
                    f.name
                } else {
                    var curr = f
                    while (curr.parentFile != null) {
                        curr = curr.parentFile!!
                    }
                    curr.name
                }
            }
            return driveFiles.filter { df -> !localFilesNames.contains(df["name"]) } as ArrayList<com.google.api.services.drive.model.File>
        }

        private suspend fun sendFilesDriveQuery(service: Drive, query: String, extraFileFields: Set<String> = setOf()): ArrayList<com.google.api.services.drive.model.File> {
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
    }
}