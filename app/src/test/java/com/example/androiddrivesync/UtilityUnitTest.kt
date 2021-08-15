package com.example.androiddrivesync

import com.example.androiddrivesync.utils.Utility
import org.junit.Assert
import org.junit.Test

class UtilityUnitTest {

    @Test
    fun getSizeUnitTest() {
        Assert.assertEquals("B", Utility.getSizeUnit(200))
        Assert.assertEquals("KB", Utility.getSizeUnit(1200))
        Assert.assertEquals("KB", Utility.getSizeUnit(24600))
        Assert.assertEquals("MB", Utility.getSizeUnit(3050020))
        Assert.assertEquals("GB", Utility.getSizeUnit(1299900000))
    }

    @Test
    fun convertToSizeUnitTest() {
        Assert.assertEquals(12.0, Utility.convertToSizeUnit(12, "B"), 0.0)
        Assert.assertEquals(0.5, Utility.convertToSizeUnit(512, "KB"), 0.0)
        Assert.assertEquals(1.0, Utility.convertToSizeUnit(1024, "KB"), 0.0)
        Assert.assertEquals(0.95, Utility.convertToSizeUnit(1000000, "MB"), 0.1)
    }
}