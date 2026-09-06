@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Pins [fanOut], the concurrent outbound round the DKLS-family ceremonies use since issue #5813.
 *
 * Awaiting each send is the point of that issue, but awaiting them one after another would have
 * traded one bug for another: a single send can now spend a full 39 s retry budget, so a serial
 * loop over a three-peer committee could burn past the 60 s ceremony stall on retries alone. These
 * tests hold the two properties that make the awaited send affordable — the peers are contacted in
 * parallel, and a failure still reaches the caller instead of being swallowed as before.
 */
class RelayFanOutTest {

    private object PlaintextEncryption : Encryption {
        override fun encrypt(data: ByteArray, password: ByteArray): ByteArray = data

        override fun decrypt(data: ByteArray, password: ByteArray): ByteArray = data
    }

    private fun messenger(api: FakeRelaySessionApi, scope: CoroutineScope): TssMessenger =
        TssMessenger(
            serverAddress = "http://relay.local",
            sessionID = "session-1",
            encryptionHex = "00112233445566778899aabbccddeeff",
            sessionApi = api,
            coroutineScope = scope,
            encryption = PlaintextEncryption,
            isEncryptionGCM = true,
        )

    @Test
    fun `every receiver in the round gets the message`() = runTest {
        val api = FakeRelaySessionApi(onSend = {})

        messenger(api, this).fanOut("deviceA", listOf("deviceB", "deviceC"), "payload")

        assertEquals(2, api.sentMessages.size)
        assertContentEquals(
            listOf(listOf("deviceB"), listOf("deviceC")),
            api.sentMessages.map { it.to }.sortedBy { it.first() },
        )
    }

    /**
     * The load-bearing property. Three slow sends must cost one send's time, not three: serialising
     * them is what would push a round past the ceremony stall.
     */
    @Test
    fun `the peers are contacted in parallel, not one after another`() = runTest {
        val sendDuration = 5_000L
        val api = FakeRelaySessionApi(onSend = { delay(sendDuration) })

        messenger(api, this).fanOut("deviceA", listOf("deviceB", "deviceC", "deviceD"), "payload")

        assertEquals(3, api.sentMessages.size)
        assertEquals(
            sendDuration,
            currentTime,
            "three 5 s sends must cost 5 s in parallel, not 15 s in sequence",
        )
    }

    /**
     * The whole point of #5813: a send that cannot be delivered has to surface. Before the fix it
     * was logged and dropped, and the ceremony waited out the stall for a reply nobody would send.
     */
    @Test
    fun `a failing send propagates to the caller`() = runTest {
        val api = FakeRelaySessionApi(onSend = { error("relay rejected the message") })

        val failure =
            assertFailsWith<IllegalStateException> {
                messenger(api, this).fanOut("deviceA", listOf("deviceB", "deviceC"), "payload")
            }
        assertEquals("relay rejected the message", failure.message)
    }

    /** A round that has already failed should not keep paying for the peers still in flight. */
    @Test
    fun `a failing send cancels its siblings`() = runTest {
        val completed = AtomicInteger(0)
        val api =
            FakeRelaySessionApi(
                onSend = { message ->
                    if (message.to.single() == "deviceB") error("relay rejected the message")
                    delay(30_000)
                    completed.incrementAndGet()
                }
            )

        assertFailsWith<IllegalStateException> {
            messenger(api, this).fanOut("deviceA", listOf("deviceB", "deviceC"), "payload")
        }

        assertEquals(0, completed.get(), "the sibling send should have been cancelled")
        assertTrue(currentTime < 30_000, "the caller must not wait out the cancelled send")
    }

    @Test
    fun `a round with no receivers sends nothing`() = runTest {
        val api = FakeRelaySessionApi(onSend = { error("should not send") })

        messenger(api, this).fanOut("deviceA", emptyList(), "payload")

        assertEquals(0, api.sentMessages.size)
    }

    @Test
    fun `the fan-out survives a scope whose dispatcher is not the test one`() = runTest {
        val api = FakeRelaySessionApi(onSend = {})

        messenger(api, CoroutineScope(Dispatchers.Default))
            .fanOut("deviceA", listOf("deviceB"), "payload")

        assertEquals(1, api.sentMessages.size)
    }
}
