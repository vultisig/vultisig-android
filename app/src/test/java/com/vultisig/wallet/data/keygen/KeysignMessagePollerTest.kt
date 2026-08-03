package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.mediator.Message
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Regression tests for issue #5488: a co-signing device sat on the signing screen for minutes while
 * the transaction landed on-chain, because the keysign poll loop pushed its 60 s abort deadline
 * forward on every non-empty relay response. A message the device can never clear — one whose relay
 * delete failed, or whose body will not decrypt — is re-served by every poll, so the deadline was
 * extended indefinitely and the error that hands over to relay recovery never fired.
 *
 * The deadline now runs from the start of the attempt, matching iOS and Windows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeysignMessagePollerTest {

    /** Monotonic clock the fake relay advances by one poll interval per poll. */
    private class TestClock {
        var nanos = 0L
            private set

        val elapsedMillis: Long
            get() = nanos / 1_000_000

        fun tick() {
            nanos += POLL_INTERVAL_MS * 1_000_000
        }
    }

    private val waitingForPeers = mutableListOf<List<String>>()
    private var resumedCount = 0

    private fun poller(sessionApi: FakeRelaySessionApi, clock: TestClock) =
        KeysignMessagePoller(
            sessionApi = sessionApi,
            mediatorURL = "http://relay.example",
            sessionID = "session-1",
            localPartyID = LOCAL_PARTY,
            keysignCommittee = listOf(LOCAL_PARTY, PEER),
            onWaitingForPeers = { waitingForPeers += it },
            onPeersResumed = { resumedCount++ },
            nanoTime = { clock.nanos },
        )

    private fun relay(clock: TestClock, onPoll: (Int) -> List<Message>) =
        FakeRelaySessionApi(
            onPoll = { poll ->
                clock.tick()
                // An Error, not an Exception: the poll loop tolerates exceptions by design, so a
                // broken deadline would swallow this guard and hang the suite instead of failing.
                if (clock.elapsedMillis > RUNAWAY_LOOP_MILLIS) {
                    throw AssertionError("poll loop did not terminate")
                }
                onPoll(poll)
            }
        )

    @Test
    fun `aborts at the deadline while the relay keeps re-serving a message that is never applied`() =
        runTest {
            val clock = TestClock()
            val sessionApi = relay(clock) { listOf(peerMessage()) }

            val failure =
                shouldThrow<IllegalStateException> {
                    poller(sessionApi, clock).poll(MESSAGE_ID) { false }
                }

            failure.message shouldContain "keysign timed out after 60s"
            clock.elapsedMillis shouldBe TIMEOUT_MS + POLL_INTERVAL_MS
        }

    @Test
    fun `aborts at the deadline when every batch fails to apply`() = runTest {
        val clock = TestClock()
        val sessionApi = relay(clock) { listOf(peerMessage()) }

        val failure =
            shouldThrow<IllegalStateException> {
                poller(sessionApi, clock).poll(MESSAGE_ID) { error("fail to decrypt message body") }
            }

        failure.message shouldContain "keysign timed out after 60s"
        clock.elapsedMillis shouldBe TIMEOUT_MS + POLL_INTERVAL_MS
    }

    @Test
    fun `backs off between failing polls instead of hot-looping, and still aborts at the deadline`() =
        runTest {
            val clock = TestClock()
            val sessionApi = relay(clock) { error("relay unreachable") }

            val failure =
                shouldThrow<IllegalStateException> {
                    poller(sessionApi, clock).poll(MESSAGE_ID) { true }
                }

            failure.message shouldContain "keysign timed out after 60s"
            currentTime shouldBe TIMEOUT_MS + POLL_INTERVAL_MS
        }

    @Test
    fun `names the peers it has not heard from, or reports that all of them answered`() = runTest {
        val clock = TestClock()
        val silentRelay = relay(clock) { listOf(peerMessage()) }

        val silent =
            shouldThrow<IllegalStateException> {
                poller(silentRelay, clock).poll(MESSAGE_ID) { false }
            }
        silent.message shouldContain "no messages from $PEER"

        val answeringClock = TestClock()
        val answeringRelay = relay(answeringClock) { listOf(peerMessage()) }
        val answering = poller(answeringRelay, answeringClock)

        val allHeard =
            shouldThrow<IllegalStateException> {
                answering.poll(MESSAGE_ID) {
                    answering.recordPeerHeard(PEER)
                    false
                }
            }

        allHeard.message shouldContain "all peers responded but the protocol did not complete"
    }

    @Test
    fun `reports silent peers after ten seconds and reports the resumption when messages return`() =
        runTest {
            val clock = TestClock()
            val sessionApi =
                relay(clock) {
                    if (clock.elapsedMillis < 11_000) emptyList() else listOf(peerMessage())
                }

            poller(sessionApi, clock).poll(MESSAGE_ID) { true } shouldBe true

            waitingForPeers shouldContainExactly listOf(listOf(PEER))
            resumedCount shouldBe 1
        }

    @Test
    fun `stays quiet about silent peers while batches keep arriving`() = runTest {
        val clock = TestClock()
        val sessionApi = relay(clock) { listOf(peerMessage()) }
        val subject = poller(sessionApi, clock)

        shouldThrow<IllegalStateException> {
            subject.poll(MESSAGE_ID) {
                subject.recordPeerHeard(PEER)
                false
            }
        }

        waitingForPeers.shouldBeEmpty()
        resumedCount shouldBe 0
    }

    @Test
    fun `starting the next message clears a silent-peer hint the previous one left raised`() =
        runTest {
            val clock = TestClock()
            val subject = poller(relay(clock) { emptyList() }, clock)

            shouldThrow<IllegalStateException> { subject.poll(MESSAGE_ID) { true } }
            waitingForPeers shouldContainExactly listOf(listOf(PEER))
            resumedCount shouldBe 0

            subject.resetForNewMessage()

            resumedCount shouldBe 1
        }

    @Test
    fun `propagates cancellation instead of retrying the poll`() = runTest {
        val clock = TestClock()
        val sessionApi = relay(clock) { throw CancellationException("keysign cancelled") }

        shouldThrow<CancellationException> { poller(sessionApi, clock).poll(MESSAGE_ID) { true } }
    }

    @Test
    fun `tracks whether any peer answered until the next message resets it`() {
        val clock = TestClock()
        val subject = poller(relay(clock) { emptyList() }, clock)

        subject.hasHeardFromAnyPeer shouldBe false

        subject.recordPeerHeard(PEER)
        subject.hasHeardFromAnyPeer shouldBe true

        subject.resetForNewMessage()
        subject.hasHeardFromAnyPeer shouldBe false
    }

    private fun peerMessage() =
        Message(
            sessionID = "session-1",
            from = PEER,
            to = listOf(LOCAL_PARTY),
            body = "body",
            hash = "hash-1",
            sequenceNo = 0,
        )

    private companion object {
        const val LOCAL_PARTY = "deviceA"
        const val PEER = "deviceB"
        const val MESSAGE_ID = "message-1"
        const val POLL_INTERVAL_MS = 100L
        const val TIMEOUT_MS = 60_000L

        /** Fails a poll loop that never terminates instead of letting the suite hang. */
        const val RUNAWAY_LOOP_MILLIS = 10 * TIMEOUT_MS
    }
}
