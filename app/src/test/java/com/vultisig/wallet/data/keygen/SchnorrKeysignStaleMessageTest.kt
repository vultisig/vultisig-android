package com.vultisig.wallet.data.keygen

import com.silencelaboratories.goschnorr.Handle
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.Encryption
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Regression test for issue #5488: a message the relay keeps serving after this device already
 * applied it used to be skipped without re-issuing its delete, so a single delete failure left the
 * message in the inbox for the rest of the session. Re-issuing the (best-effort) delete clears it
 * at the source instead of waiting for the relay to expire it — but only once per attempt, because
 * the relay client backs off between its own delete retries and the poll loop waits on that.
 *
 * Only the dedup branch is exercised — it returns before the goschnorr JNI rounds, which are
 * unavailable on the host JVM, so the session handle is never dereferenced.
 */
class SchnorrKeysignStaleMessageTest {

    @Test
    fun `an already-applied message is deleted from the relay again, once, not on every poll`() =
        runTest {
            val sessionApi = FakeRelaySessionApi()
            val keysign =
                SchnorrKeysign(
                    keysignCommittee = listOf(LOCAL_PARTY, PEER),
                    mediatorURL = "http://relay.example",
                    sessionID = SESSION_ID,
                    messageToSign = listOf("abc123deadbeef"),
                    vault = vault(),
                    encryptionKeyHex = "00".repeat(32),
                    isInitiateDevice = false,
                    sessionApi = sessionApi,
                    encryption = mockk<Encryption>(relaxed = true),
                )
            val staleMessage =
                Message(
                    sessionID = SESSION_ID,
                    from = PEER,
                    to = listOf(LOCAL_PARTY),
                    body = "body",
                    hash = "hash-1",
                    sequenceNo = 0,
                )
            keysign.cache["$SESSION_ID-$LOCAL_PARTY-$MESSAGE_ID-${staleMessage.hash}"] = Any()

            val handle = mockk<Handle>(relaxed = true)
            repeat(3) {
                keysign.processInboundMessage(
                    handle = handle,
                    msgs = listOf(staleMessage),
                    messageID = MESSAGE_ID,
                ) shouldBe false
            }

            sessionApi.deletedHashes shouldContainExactly listOf(staleMessage.hash)
        }

    private fun vault() =
        Vault(
            id = "vault-id",
            name = "test-vault",
            pubKeyECDSA = "pub-ecdsa",
            pubKeyEDDSA = "pub-eddsa",
            pubKeyMLDSA = "pub-mldsa",
            localPartyID = LOCAL_PARTY,
            keyshares = listOf(KeyShare("pub-eddsa", "share")),
        )

    private companion object {
        const val SESSION_ID = "session-1"
        const val MESSAGE_ID = "message-1"
        const val LOCAL_PARTY = "deviceA"
        const val PEER = "deviceB"
    }
}
