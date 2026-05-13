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
}
