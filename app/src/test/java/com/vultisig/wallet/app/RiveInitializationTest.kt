package com.vultisig.wallet.app

import android.content.Context
import android.content.SharedPreferences
import app.rive.runtime.kotlin.core.Rive
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RiveInitializationTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private var inFlightValue: Boolean = false

    @BeforeEach
    fun setUp() {
        resetRiveInitialized()
        mockkObject(Rive)
        mockkStatic("com.vultisig.wallet.app.VoltixApplicationKt")
        every { riveGuardPrefs(context) } returns prefs
        // Read from / write to a single in-memory backing variable so the test can observe what
        // the production code commits.
        every { prefs.getBoolean("rive_init_in_flight", false) } answers { inFlightValue }
        every { prefs.edit() } returns editor
        val captured = slot<Boolean>()
        every { editor.putBoolean("rive_init_in_flight", capture(captured)) } answers
            {
                inFlightValue = captured.captured
                editor
            }
        every { editor.commit() } returns true
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        resetRiveInitialized()
        inFlightValue = false
    }

    @Test
    fun `isRiveInitialized is false by default`() {
        isRiveInitialized.shouldBeFalse()
    }

    @Test
    fun `successful init sets initialized true and clears in-flight flag`() {
        every { Rive.init(any(), any()) } returns Unit

        initializeRive(context)

        isRiveInitialized.shouldBeTrue()
        inFlightValue.shouldBeFalse()
    }

    @Test
    fun `catchable UnsatisfiedLinkError clears in-flight flag so Rive is retried next launch`() {
        every { Rive.init(any(), any()) } throws UnsatisfiedLinkError("missing native lib")

        initializeRive(context)

        isRiveInitialized.shouldBeFalse()
        inFlightValue.shouldBeFalse()
    }

    @Test
    fun `catchable RuntimeException clears in-flight flag so Rive is retried next launch`() {
        every { Rive.init(any(), any()) } throws RuntimeException("init failed")

        initializeRive(context)

        isRiveInitialized.shouldBeFalse()
        inFlightValue.shouldBeFalse()
    }

    @Test
    fun `init is skipped when in-flight flag was already set from a previous launch`() {
        inFlightValue = true

        initializeRive(context)

        isRiveInitialized.shouldBeFalse()
        verify(exactly = 0) { Rive.init(any(), any()) }
    }
}
