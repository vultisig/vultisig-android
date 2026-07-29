package com.vultisig.wallet.data.services

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PushRegistrationWorkerTest {

    private lateinit var pushNotificationManager: PushNotificationManager
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        pushNotificationManager = mockk()
        context = mockk(relaxed = true)
    }

    private fun buildWorker(runAttemptCount: Int = 0): PushRegistrationWorker =
        TestListenableWorkerBuilder<PushRegistrationWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        PushRegistrationWorker(
                            appContext,
                            workerParameters,
                            pushNotificationManager,
                        )
                }
            )
            .build()

    @Test
    fun `re-registers every opted-in vault against the current token`() = runTest {
        coEvery { pushNotificationManager.hasOptedInVaults() } returns true
        coEvery { pushNotificationManager.currentToken() } returns "fcm-token"
        coEvery { pushNotificationManager.reRegisterOptedInVaults("fcm-token") } returns true

        buildWorker().doWork() shouldBe ListenableWorker.Result.success()

        coVerify(exactly = 1) { pushNotificationManager.reRegisterOptedInVaults("fcm-token") }
    }

    /**
     * The whole point of running in WorkManager: a registration that fails must come back, or the
     * server keeps a dead token while the local toggle still reads ON and pushes stop for good.
     */
    @Test
    fun `retries when a vault fails to re-register`() = runTest {
        coEvery { pushNotificationManager.hasOptedInVaults() } returns true
        coEvery { pushNotificationManager.currentToken() } returns "fcm-token"
        coEvery { pushNotificationManager.reRegisterOptedInVaults(any()) } returns false

        buildWorker().doWork() shouldBe ListenableWorker.Result.retry()
    }

    @Test
    fun `retries when no token can be obtained`() = runTest {
        coEvery { pushNotificationManager.hasOptedInVaults() } returns true
        coEvery { pushNotificationManager.currentToken() } returns null

        buildWorker().doWork() shouldBe ListenableWorker.Result.retry()

        coVerify(exactly = 0) { pushNotificationManager.reRegisterOptedInVaults(any()) }
    }

    @Test
    fun `gives up once the attempt cap is reached`() = runTest {
        coEvery { pushNotificationManager.hasOptedInVaults() } returns true
        coEvery { pushNotificationManager.currentToken() } returns "fcm-token"
        coEvery { pushNotificationManager.reRegisterOptedInVaults(any()) } returns false

        val worker = buildWorker(runAttemptCount = PushRegistrationWorker.MAX_ATTEMPTS)

        worker.doWork() shouldBe ListenableWorker.Result.failure()
    }

    /**
     * The startup reconcile runs on every launch, including for users who never enabled pushes.
     * Minting a token for them would register a device nobody asked to register.
     */
    @Test
    fun `does nothing when no vault has opted in`() = runTest {
        coEvery { pushNotificationManager.hasOptedInVaults() } returns false

        buildWorker().doWork() shouldBe ListenableWorker.Result.success()

        coVerify(exactly = 0) { pushNotificationManager.currentToken() }
        coVerify(exactly = 0) { pushNotificationManager.reRegisterOptedInVaults(any()) }
    }
}
