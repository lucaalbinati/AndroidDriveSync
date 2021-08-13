package com.example.androiddrivesync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.androiddrivesync.boot.SignInActivity
import com.example.androiddrivesync.synchronizeservice.SynchronizeWorker
import com.google.android.gms.tasks.Tasks
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SynchronizeWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val googleSignInClient = SignInActivity.getGoogleSignInClient(context, false)
        val task = googleSignInClient.silentSignIn()
        Tasks.await(task)
    }

    @Test
    fun test() {
        val workManager = WorkManager.getInstance(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)

        val requestId = SynchronizeWorker.setupPeriodicWorkRequest(context)
        testDriver?.setPeriodDelayMet(requestId)

        val workInfoFuture = workManager.getWorkInfoById(requestId)
        while (!(workInfoFuture.isDone || workInfoFuture.isCancelled)) {}

        val workInfo = workInfoFuture.get()
        assert(workInfo.state == WorkInfo.State.SUCCEEDED)
    }

}