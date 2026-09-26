@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import android.content.Context
import android.graphics.Bitmap
import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.MakeQrCodeBitmapShareFormat
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.VsClipboardService
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins that Copy Link on the share sheet copies exactly the string drawn in the pairing QR, so a
 * device that opens the link joins the same session a scan would.
 */
internal class KeysignShareViewModelCopyLinkTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val snackbarFlow: SnackbarFlow = mockk(relaxed = true)
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat = mockk(relaxed = true)
    private val generateQrBitmap: GenerateQrBitmap = mockk {
        every { this@mockk(any(), any(), any(), any()) } returns mockk<Bitmap>(relaxed = true)
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(VsClipboardService)
        every { VsClipboardService.copy(any(), any()) } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(VsClipboardService)
        Dispatchers.resetMain()
    }

    @Test
    fun `copies the link the QR encodes and confirms it`() = runTest {
        val vm = viewModel()
        vm.loadQrPainter(INTERNET_LINK).join()

        vm.copyQrLink(context)

        verify(exactly = 1) { VsClipboardService.copy(context, INTERNET_LINK) }
        coVerify(exactly = 1) {
            snackbarFlow.showMessage(
                UiText.StringResource(R.string.keysign_share_qr_link_copied),
                any(),
            )
        }
    }

    @Test
    fun `copies the rebuilt link after a switch to local mode`() = runTest {
        val vm = viewModel()
        vm.loadQrPainter(INTERNET_LINK).join()
        vm.loadQrPainter(LOCAL_LINK).join()

        vm.copyQrLink(context)

        verify(exactly = 1) { VsClipboardService.copy(context, LOCAL_LINK) }
        verify(exactly = 0) { VsClipboardService.copy(context, INTERNET_LINK) }
    }

    @Test
    fun `copies nothing before the QR exists`() = runTest {
        val vm = viewModel()

        vm.copyQrLink(context)

        verify(exactly = 0) { VsClipboardService.copy(any(), any()) }
        coVerify(exactly = 0) { snackbarFlow.showMessage(any<UiText>(), any()) }
    }

    @Test
    fun `a slower earlier render never publishes over the latest link`() = runTest {
        val internetBitmap = mockk<Bitmap>(relaxed = true)
        val localBitmap = mockk<Bitmap>(relaxed = true)
        val internetRenderStarted = CountDownLatch(1)
        val releaseInternetRender = CountDownLatch(1)
        every { generateQrBitmap(INTERNET_LINK, any(), any(), any()) } answers
            {
                internetRenderStarted.countDown()
                releaseInternetRender.await()
                internetBitmap
            }
        every { generateQrBitmap(LOCAL_LINK, any(), any(), any()) } returns localBitmap
        val vm = viewModel()

        val internetLoad = vm.loadQrPainter(INTERNET_LINK)
        internetRenderStarted.await()
        vm.loadQrPainter(LOCAL_LINK).join()
        releaseInternetRender.countDown()
        internetLoad.join()

        assertEquals(LOCAL_LINK, vm.qrLink.value)
        assertNotNull(vm.qrBitmapPainter.value)
        vm.saveShareQrBitmap(context, 0, mockk(relaxed = true), mockk(relaxed = true))
        verify(exactly = 1) { makeQrCodeBitmapShareFormat(any(), localBitmap, any(), any(), any()) }
        verify(exactly = 0) {
            makeQrCodeBitmapShareFormat(any(), internetBitmap, any(), any(), any())
        }
    }

    @Test
    fun `a new load hides the previous link until its QR is rendered`() = runTest {
        val releaseLocalRender = CountDownLatch(1)
        every { generateQrBitmap(LOCAL_LINK, any(), any(), any()) } answers
            {
                releaseLocalRender.await()
                mockk<Bitmap>(relaxed = true)
            }
        val vm = viewModel()
        vm.loadQrPainter(INTERNET_LINK).join()

        val localLoad = vm.loadQrPainter(LOCAL_LINK)

        assertNull(vm.qrLink.value)
        assertNull(vm.qrBitmapPainter.value)
        vm.copyQrLink(context)
        verify(exactly = 0) { VsClipboardService.copy(any(), any()) }

        releaseLocalRender.countDown()
        localLoad.join()
        assertEquals(LOCAL_LINK, vm.qrLink.value)
    }

    private fun viewModel() =
        KeysignShareViewModel(
            mapTokenValueToStringWithUnit = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            transactionRepository = mockk(relaxed = true),
            swapTransactionRepository = mockk(relaxed = true),
            depositTransaction = mockk(relaxed = true),
            customMessagePayloadRepo = mockk(relaxed = true),
            makeQrCodeBitmapShareFormat = makeQrCodeBitmapShareFormat,
            generateQrBitmap = generateQrBitmap,
            snackbarFlow = snackbarFlow,
        )

    private companion object {
        const val INTERNET_LINK =
            "https://vultisig.com?type=SignTransaction&resharePrefix=abc&vault=02ab&jsonData=internet"
        const val LOCAL_LINK =
            "https://vultisig.com?type=SignTransaction&resharePrefix=abc&vault=02ab&jsonData=local"
    }
}
