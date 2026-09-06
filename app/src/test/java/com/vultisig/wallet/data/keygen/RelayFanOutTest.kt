@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicInteger
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

        api.sentMessages.size shouldBe 2
        api.sentMessages.map { it.to }.sortedBy { it.first() } shouldContainExactly
            listOf(listOf("deviceB"), listOf("deviceC"))
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

        api.sentMessages.size shouldBe 3
        withClue("three 5 s sends must cost 5 s in parallel, not 15 s in sequence") {
            currentTime shouldBe sendDuration
        }
    }

    /**
     * The whole point of #5813: a send that cannot be delivered has to surface. Before the fix it
     * was logged and dropped, and the ceremony waited out the stall for a reply nobody would send.
     */
    @Test
    fun `a failing send propagates to the caller`() = runTest {
        val api = FakeRelaySessionApi(onSend = { error("relay rejected the message") })

        val failure =
            shouldThrow<IllegalStateException> {
                messenger(api, this).fanOut("deviceA", listOf("deviceB", "deviceC"), "payload")
            }
        failure.message shouldBe "relay rejected the message"
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

        shouldThrow<IllegalStateException> {
            messenger(api, this).fanOut("deviceA", listOf("deviceB", "deviceC"), "payload")
        }

        withClue("the sibling send should have been cancelled") { completed.get() shouldBe 0 }
        withClue("the caller must not wait out the cancelled send") {
            currentTime shouldBeLessThan 30_000L
        }
    }

    @Test
    fun `a round with no receivers sends nothing`() = runTest {
        val api = FakeRelaySessionApi(onSend = { error("should not send") })

        messenger(api, this).fanOut("deviceA", emptyList(), "payload")

        api.sentMessages.size shouldBe 0
    }

    @Test
    fun `the fan-out survives a scope whose dispatcher is not the test one`() = runTest {
        val api = FakeRelaySessionApi(onSend = {})

        messenger(api, CoroutineScope(Dispatchers.Default))
            .fanOut("deviceA", listOf("deviceB"), "payload")

        api.sentMessages.size shouldBe 1
    }
}
