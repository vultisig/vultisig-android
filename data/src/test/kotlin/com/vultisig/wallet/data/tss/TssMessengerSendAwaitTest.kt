@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.tss

import com.vultisig.wallet.data.api.RelaySendBudget
import com.vultisig.wallet.data.api.RelaySendDeadlineExceededException
import com.vultisig.wallet.data.api.RelaySendFailedException
import com.vultisig.wallet.data.api.SessionApiImpl
import com.vultisig.wallet.data.api.utils.HttpException
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.testutils.MockHttpClient
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Covers [TssMessenger.sendAwait], the awaited relay send added for issue #5813.
 *
 * The bug it replaces: `send` fired the POST into a scope nobody joined and only logged the
 * failure, so a lost relay message left the ceremony waiting 60 s for a reply that could never
 * arrive, then restarting locally while the other devices were still on the old session.
 * `sendAwait` waits for the relay to accept the message and throws when the budget is spent, which
 * is what lets the keygen retry wrapper see the failure at all.
 *
 * `sendAwait recovers when the relay rejects the first three posts` is the issue's acceptance case:
 * fail one device's first outbound POST three times, then let it through.
 */
class TssMessengerSendAwaitTest {

    private val serverAddress = "http://relay.local"
    private val sessionId = "session-1"
    private val relayUrl = "$serverAddress/message/$sessionId"
    // 32 hex chars: the AES-CBC path decodes the key as hex, so it has to be well formed.
    private val encryptionHex = "00112233445566778899aabbccddeeff"
    private val json = Json { ignoreUnknownKeys = true }

    /** Passes the body straight through so a test can read the sent payload without decrypting. */
    private object PlaintextEncryption : Encryption {
        override fun encrypt(data: ByteArray, password: ByteArray): ByteArray = data

        override fun decrypt(data: ByteArray, password: ByteArray): ByteArray = data
    }

    private fun messenger(client: HttpClient, scope: CoroutineScope): TssMessenger =
        TssMessenger(
            serverAddress = serverAddress,
            sessionID = sessionId,
            encryptionHex = encryptionHex,
            sessionApi = SessionApiImpl(json = json, httpClient = client),
            coroutineScope = scope,
            encryption = PlaintextEncryption,
            isEncryptionGCM = true,
        )

    private fun alwaysFailing(onCall: (Int) -> Unit = {}): HttpClient =
        MockHttpClient.failingThenResponding(
            failures = Int.MAX_VALUE,
            failWith = { IOException("relay unreachable") },
            onCall = onCall,
        )

    private fun sequenceNoOf(requestBody: String): Int =
        json
            .parseToJsonElement(requestBody)
            .jsonObject
            .getValue("sequence_no")
            .jsonPrimitive
            .content
            .toInt()

    /**
     * The issue's acceptance case. Three rejected posts then a good one must leave the ceremony
     * with a delivered message and no error, rather than a swallowed failure and a 60 s stall.
     */
    @Test
    fun `sendAwait recovers when the relay rejects the first three posts`() = runTest {
        val calls = AtomicInteger(0)
        val client =
            MockHttpClient.failingThenResponding(
                failures = 3,
                failWith = { IOException("relay unreachable") },
                onCall = { calls.incrementAndGet() },
            )

        messenger(client, this).sendAwait("deviceA", "deviceB", "round-one-payload")

        assertEquals(4, calls.get(), "should have used all four attempts")
        // 1 s + 2 s + 4 s of backoff between the four attempts.
        assertEquals(7_000L, currentTime)
    }

    /**
     * Every retry has to carry the byte-identical message. The relay stores by
     * `session-recipient-hash` and receivers dedupe by the same key, so a message rebuilt per
     * attempt — a fresh sequence number, or a fresh AES-GCM nonce — would be applied twice.
     */
    @Test
    fun `sendAwait resends the identical message on every attempt`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val client =
            MockHttpClient.capturingRequestSequence(
                capture,
                HttpStatusCode.InternalServerError to "boom",
                HttpStatusCode.InternalServerError to "boom",
                HttpStatusCode.OK to "",
            )

        messenger(client, this).sendAwait("deviceA", "deviceB", "round-one-payload")

        assertEquals(3, capture.bodies.size)
        assertEquals(1, capture.bodies.toSet().size, "every attempt must send the same bytes")
    }

    @Test
    fun `sendAwait numbers consecutive messages in order`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val messenger =
            messenger(MockHttpClient.capturingRequest(HttpStatusCode.OK, "", capture), this)

        messenger.sendAwait("deviceA", "deviceB", "first")
        messenger.sendAwait("deviceA", "deviceB", "second")

        assertContentEquals(listOf(1, 2), capture.bodies.map(::sequenceNoOf))
    }

    /**
     * The DKLS ceremonies now fan one round out to every peer at once, so the sequence counter is
     * written concurrently. A lost increment would give two messages bound for the same peer the
     * same number, and the receiver sorts by it before applying.
     */
    @Test
    fun `concurrent sends never reuse a sequence number`() = runTest {
        val capture = MockHttpClient.RequestCapture()
        val messenger =
            messenger(MockHttpClient.capturingRequest(HttpStatusCode.OK, "", capture), this)

        (1..16)
            .map { peer -> async { messenger.sendAwait("deviceA", "device$peer", "payload") } }
            .awaitAll()

        assertContentEquals((1..16).toList(), capture.bodies.map(::sequenceNoOf).sorted())
    }

    /**
     * The type is load-bearing, not decoration: the ceremony poll loops wrap the relay read and the
     * outbound round in one broad catch, and tell a lost send from a failed read by this class
     * alone. A bare transport exception here is swallowed by the loop waiting on the reply.
     */
    @Test
    fun `sendAwait throws a marked send failure once the budget is spent`() = runTest {
        val calls = AtomicInteger(0)
        val messenger = messenger(alwaysFailing { calls.incrementAndGet() }, this)

        val failure =
            assertFailsWith<RelaySendFailedException> {
                messenger.sendAwait("deviceA", "deviceB", "round-one-payload")
            }

        assertEquals("failed to send the message to deviceB", failure.message)
        assertIs<NetworkException>(failure.cause, "the transport fault must stay on the cause")
        assertEquals(RelaySendBudget.DEFAULT_MAX_ATTEMPTS, calls.get())
    }

    @Test
    fun `sendAwait retries a relay 5xx`() = runTest {
        val client =
            MockHttpClient.respondingWithSequence(
                HttpStatusCode.InternalServerError to "boom",
                HttpStatusCode.OK to "",
            )

        messenger(client, this).sendAwait("deviceA", "deviceB", "round-one-payload")

        assertEquals(1_000L, currentTime, "one backoff only")
    }

    /** A rejected message is deterministic: resending it three more times cannot help. */
    @Test
    fun `sendAwait gives up immediately on a 4xx`() = runTest {
        val calls = AtomicInteger(0)
        val client =
            MockHttpClient.failingThenResponding(
                failures = 0,
                failWith = { IOException("unused") },
                status = HttpStatusCode.BadRequest,
                body = "rejected",
                onCall = { calls.incrementAndGet() },
            )

        val failure =
            assertFailsWith<RelaySendFailedException> {
                messenger(client, this).sendAwait("deviceA", "deviceB", "round-one-payload")
            }

        assertIs<HttpException>(failure.cause, "the rejection status must stay on the cause")
        assertEquals(1, calls.get())
        assertEquals(0L, currentTime, "a 4xx must not spend any backoff")
    }

    /**
     * The keysign poll deadline is absolute (`KeysignMessagePoller`, issue #5488). A send started
     * from inside an applied message must not outlive it — past the deadline there is nothing left
     * to send into, and the attempt has already failed.
     */
    @Test
    fun `sendAwait refuses to start once the ceremony deadline has passed`() = runTest {
        val calls = AtomicInteger(0)
        val client =
            MockHttpClient.failingThenResponding(
                failures = 0,
                failWith = { IOException("unused") },
                onCall = { calls.incrementAndGet() },
            )

        val failure =
            assertFailsWith<RelaySendDeadlineExceededException> {
                messenger(client, this)
                    .sendAwait(
                        from = "deviceA",
                        to = "deviceB",
                        body = "round-one-payload",
                        deadlineNanos = System.nanoTime() - 1,
                    )
            }

        assertIs<RelaySendFailedException>(failure, "the poll loops key on the marked type")
        assertEquals(0, calls.get(), "no request should be started past the deadline")
    }

    /**
     * A hung POST used to inherit the client's own timeouts and could sit there while the ceremony
     * clock ran out. The per-request budget ends it so the attempt is retried instead.
     *
     * Real milliseconds, not virtual: Ktor's `HttpTimeout` runs its killer in the client's own
     * scope rather than the test scheduler's, so the timeout is kept small instead of mocked away.
     */
    @Test
    fun `a hung request is capped instead of waiting out the client default`() = runTest {
        val calls = AtomicInteger(0)
        val api =
            SessionApiImpl(json = json, httpClient = MockHttpClient.hanging(calls::incrementAndGet))
        val message = Message(sessionId, "deviceA", listOf("deviceB"), "body", "hash", 1)

        assertFailsWith<NetworkException> {
            api.sendTssMessage(
                serverUrl = relayUrl,
                messageId = null,
                message = message,
                budget =
                    RelaySendBudget(
                        maxAttempts = 2,
                        requestTimeout = 50.milliseconds,
                        initialBackoff = 1.milliseconds,
                    ),
            )
        }
        assertEquals(2, calls.get(), "the hung request must time out and be retried")
    }

    /**
     * The legacy GG20 path is a gomobile `tss.Messenger` implementation called synchronously from
     * native code, so [TssMessenger.send] keeps its signature and its swallow-and-log behaviour.
     * Changing it is out of scope for #5813 and would break the interface.
     */
    @Test
    fun `the legacy send still swallows a failing relay`() = runTest {
        val calls = AtomicInteger(0)
        // A 4xx so the shared relay retry gives up at once and the legacy loop's own three
        // attempts are the only ones, with no backoff to wait out on a real dispatcher.
        val client =
            MockHttpClient.failingThenResponding(
                failures = 0,
                failWith = { IOException("unused") },
                status = HttpStatusCode.BadRequest,
                body = "rejected",
                onCall = { calls.incrementAndGet() },
            )
        val sendScope = CoroutineScope(Dispatchers.Default)

        try {
            // Must neither throw nor block the caller.
            messenger(client, sendScope).send("deviceA", "deviceB", "round-one-payload")
            sendScope.coroutineContext.job.children.toList().joinAll()

            assertEquals(3, calls.get(), "the legacy loop tries three times and gives up quietly")
        } finally {
            sendScope.cancel()
        }
    }
}
