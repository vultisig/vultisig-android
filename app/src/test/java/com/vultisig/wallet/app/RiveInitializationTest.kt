package com.vultisig.wallet.app

import android.content.Context
import app.rive.runtime.kotlin.core.Rive
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RiveInitializationTest {

    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        resetRiveInitialized()
        mockkObject(Rive)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Rive)
        resetRiveInitialized()
    }

    @Test
    fun `isRiveInitialized is false by default`() {
        assertFalse(isRiveInitialized)
    }

    @Test
    fun `isRiveInitialized is true after successful Rive init`() {
        every { Rive.init(any(), any()) } returns Unit

        initializeRive(context)

        assertTrue(isRiveInitialized)
    }

    @Test
    fun `isRiveInitialized stays false when Rive init throws UnsatisfiedLinkError`() {
        every { Rive.init(any(), any()) } throws UnsatisfiedLinkError("missing native lib")

        initializeRive(context)

        assertFalse(isRiveInitialized)
    }

    @Test
    fun `isRiveInitialized stays false when Rive init throws RuntimeException`() {
        every { Rive.init(any(), any()) } throws RuntimeException("init failed")

        initializeRive(context)

        assertFalse(isRiveInitialized)
    }

    @Test
    fun `isRiveUnsupportedDevice matches Tecno CAMON 30 by manufacturer and model`() {
        assertTrue(
            isRiveUnsupportedDevice(
                manufacturer = "TECNO",
                model = "TECNO CAMON 30",
                socModel = null,
            )
        )
    }

    @Test
    fun `isRiveUnsupportedDevice matches Tecno CAMON 30 case-insensitively`() {
        assertTrue(
            isRiveUnsupportedDevice(
                manufacturer = "tecno",
                model = "Tecno Camon 30 Pro 5G",
                socModel = null,
            )
        )
    }

    @Test
    fun `isRiveUnsupportedDevice matches by MT6789 SoC model`() {
        assertTrue(
            isRiveUnsupportedDevice(
                manufacturer = "Other",
                model = "Other Phone",
                socModel = "MT6789",
            )
        )
    }

    @Test
    fun `isRiveUnsupportedDevice returns false for unaffected devices`() {
        assertFalse(
            isRiveUnsupportedDevice(
                manufacturer = "Google",
                model = "Pixel 8 Pro",
                socModel = "Tensor G3",
            )
        )
    }

    @Test
    fun `isRiveUnsupportedDevice returns false for non-CAMON Tecno models`() {
        assertFalse(
            isRiveUnsupportedDevice(
                manufacturer = "TECNO",
                model = "TECNO SPARK 20",
                socModel = "MT6765",
            )
        )
    }
}
