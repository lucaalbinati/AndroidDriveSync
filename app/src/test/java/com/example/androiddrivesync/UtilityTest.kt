package com.example.androiddrivesync

import com.example.androiddrivesync.utils.MimeTypeNotFoundException
import com.example.androiddrivesync.utils.Utility
import org.junit.Assert
import org.junit.Test

class UtilityTest {

    @Test
    fun getMimeTypeThatExists() {
        val mimeType = Utility.getTypeFromFilename("myFile.txt")
        Assert.assertEquals(mimeType, "text/plain")
    }

    @Test
    fun getMimeTypeThatDoesNotExist() {
        Assert.assertThrows(MimeTypeNotFoundException::class.java) {
            Utility.getTypeFromFilename("myFile.backup")
        }
    }

}