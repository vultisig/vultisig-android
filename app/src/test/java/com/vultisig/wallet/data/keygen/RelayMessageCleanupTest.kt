package com.vultisig.wallet.data.keygen

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Regression tests for issue #5488: the relay delete of an applied message used to be able to throw
 * out of the round that applied it, skipping the outbound drain and the completion check that
 * follow. A device that had already finished signing therefore discarded its own completion flag
 * and could never produce its signature — which is how the reporting user's transaction broadcast
 * fine while their second device stayed on the signing screen.
 */
class RelayMessageCleanupTest {

    @Test
    fun `a failed relay delete does not propagate to the round that applied the message`() =
        runTest {
            val sessionApi = FakeRelaySessionApi(onDelete = { error("relay rejected the delete") })

            sessionApi.deleteTssMessageQuietly(
                serverUrl = "http://relay.example",
                sessionId = "session-1",
                localPartyId = "deviceA",
                msgHash = "hash-1",
                messageId = "message-1",
            )

            sessionApi.deletedHashes shouldContainExactly listOf("hash-1")
        }

    @Test
    fun `cancellation still propagates`() = runTest {
        val sessionApi =
            FakeRelaySessionApi(onDelete = { throw CancellationException("keysign cancelled") })

        shouldThrow<CancellationException> {
            sessionApi.deleteTssMessageQuietly(
                serverUrl = "http://relay.example",
                sessionId = "session-1",
                localPartyId = "deviceA",
                msgHash = "hash-1",
                messageId = "message-1",
            )
        }
    }
}
