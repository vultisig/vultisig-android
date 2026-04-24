package com.vultisig.wallet.app

import android.content.Context
import app.rive.runtime.kotlin.core.Rive
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
        unmockkAll()
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
    fun `initializeRive skips Rive init when Mali GPU is detected`() {
        mockkStatic("com.vultisig.wallet.app.VoltixApplicationKt")
        every { isMaliGpu() } returns true

        initializeRive(context)

        assertFalse(isRiveInitialized)
    }

    @Test
    fun `initializeRive proceeds normally when Mali GPU is not detected`() {
        mockkStatic("com.vultisig.wallet.app.VoltixApplicationKt")
        every { isMaliGpu() } returns false
        every { Rive.init(any(), any()) } returns Unit

        initializeRive(context)

        assertTrue(isRiveInitialized)
    }
}
