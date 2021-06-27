package com.example.androiddrivesync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.androiddrivesync.utility.MimeTypeNotFoundException
import com.example.androiddrivesync.utility.Utility
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UtilityTest {

    lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun getMimeTypeThatExists() {
        val mimeType = Utility.getTypeFromFilename(context, "myFile.txt")
        Assert.assertEquals(mimeType, "text/plain")
    }

    @Test
    fun getMimeTypeThatDoesNotExist() {
        Assert.assertThrows(MimeTypeNotFoundException::class.java) {
            Utility.getTypeFromFilename(context, "myFile.backup")
        }
    }

}