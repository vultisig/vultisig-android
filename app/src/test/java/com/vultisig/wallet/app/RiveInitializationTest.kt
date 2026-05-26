package com.vultisig.wallet.app

import android.content.Context
import android.content.SharedPreferences
import app.rive.runtime.kotlin.core.Rive
import com.vultisig.wallet.BuildConfig
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RiveInitializationTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private var storedVersion: Int = NO_IN_FLIGHT_VERSION

    @BeforeEach
    fun setUp() {
        resetRiveInitialized()
        mockkObject(Rive)
        mockkStatic("com.vultisig.wallet.app.VoltixApplicationKt")
        every { riveGuardPrefs(context) } returns prefs
        // Read from / write to a single in-memory backing variable so the test can observe what
        // the production code commits.
        every { prefs.getInt("rive_init_in_flight_version", NO_IN_FLIGHT_VERSION) } answers
            {
                storedVersion
            }
        every { prefs.edit() } returns editor
        val captured = slot<Int>()
        every { editor.putInt("rive_init_in_flight_version", capture(captured)) } answers
            {
                storedVersion = captured.captured
                editor
            }
        every { editor.commit() } returns true
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Rive)
        unmockkStatic("com.vultisig.wallet.app.VoltixApplicationKt")
        resetRiveInitialized()
        storedVersion = NO_IN_FLIGHT_VERSION
    }

    @Test
    fun `isRiveInitialized is false by default`() {
        isRiveInitialized.shouldBeFalse()
    }

    @Test
    fun `successful init sets initialized true and clears in-flight version`() {
        every { Rive.init(any(), any()) } returns Unit

        initializeRive(context)

        isRiveInitialized.shouldBeTrue()
        storedVersion.shouldBe(NO_IN_FLIGHT_VERSION)
    }

    @Test
    fun `in-flight version is committed before Rive_init crosses the JNI boundary`() {
        every { Rive.init(any(), any()) } returns Unit

        initializeRive(context)

        // Guards the SIGABRT-race invariant: the marker must be durably committed before the
        // native call, otherwise an async write could be lost when the native abort kills the
        // process and the next launch would have no record of the crash.
        verifyOrder {
            editor.putInt("rive_init_in_flight_version", BuildConfig.VERSION_CODE)
            editor.commit()
            Rive.init(any(), any())
        }
        verify(atLeast = 1) { editor.commit() }
    }

    @Test
    fun `catchable UnsatisfiedLinkError clears in-flight version so Rive is retried next launch`() {
        every { Rive.init(any(), any()) } throws UnsatisfiedLinkError("missing native lib")

        initializeRive(context)

        isRiveInitialized.shouldBeFalse()
        storedVersion.shouldBe(NO_IN_FLIGHT_VERSION)
    }

    @Test
    fun `catchable RuntimeException clears in-flight version so Rive is retried next launch`() {
        every { Rive.init(any(), any()) } throws RuntimeException("init failed")

        initializeRive(context)

        isRiveInitialized.shouldBeFalse()
        storedVersion.shouldBe(NO_IN_FLIGHT_VERSION)
    }

    @Test
    fun `init is skipped when stored version matches current version from a previous crash`() {
        // We cannot kill the JVM inside a unit test, so we simulate the post-crash state directly:
        // the stored version equals the current versionCode, which is exactly the on-disk shape a
        // SIGABRT during the previous launch would leave behind.
        storedVersion = BuildConfig.VERSION_CODE

        initializeRive(context)

        isRiveInitialized.shouldBeFalse()
        verify(exactly = 0) { Rive.init(any(), any()) }
    }

    @Test
    fun `init proceeds when stored version is from an older app version (upgrade recovery)`() {
        // A prior crashed install left the flag set, but the user has since upgraded — the stored
        // version no longer matches BuildConfig.VERSION_CODE, so Rive should be retried in case
        // the new build ships a fix.
        storedVersion = BuildConfig.VERSION_CODE - 1
        every { Rive.init(any(), any()) } returns Unit

        initializeRive(context)

        isRiveInitialized.shouldBeTrue()
        storedVersion.shouldBe(NO_IN_FLIGHT_VERSION)
        verify(exactly = 1) { Rive.init(any(), any()) }
    }
}
