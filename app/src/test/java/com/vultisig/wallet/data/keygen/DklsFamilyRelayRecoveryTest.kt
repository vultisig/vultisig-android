package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.Vault
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Regression tests for issue #5314: when a DKLS-family signing attempt fails or times out (e.g. a
 * relay message is lost to a transport error), the device must consult the relay for an
 * already-completed signature via `KeysignVerify.checkKeysignComplete` before giving up — mirroring
 * the legacy GG20 fallback in `KeysignViewModel.signMessageWithRetry`. Without this fallback a
 * device whose local session never reaches `isFinished` fails with "signatures empty" even though
 * the peer already posted the full signature to the relay.
 *
 * These tests pin the *wiring*: on failure the relay IS queried, and with the correct `msgHash` key
 * (matching each class's `markLocalPartyKeysignComplete(msgHash, …)` write) — not the raw
 * `messageToSign`. They deliberately do not assert the recovered signature is stored, because
 * `tss.KeysignResponse` is a JNI/native type whose static initializer loads the native `gojni`
 * library and therefore cannot be constructed — or even mocked — on the host JVM. This is the same
 * constraint that makes `SessionApiBodyReadTest` skip `checkKeysignComplete`, and the reason the
 * [FakeSessionApi] below is hand-written rather than a mockk (mockk eagerly resolves the
 * `tss.KeysignResponse` return type and crashes with UnsatisfiedLinkError).
 */
class DklsFamilyRelayRecoveryTest {

    private val message = "abc123deadbeef"
    private val expectedMsgHash = message.md5()

    private fun vault() =
        Vault(
            id = "vault-id",
            name = "test-vault",
            pubKeyECDSA = "pub-ecdsa",
            pubKeyEDDSA = "pub-eddsa",
            pubKeyMLDSA = "pub-mldsa",
            localPartyID = "deviceA",
            keyshares = listOf(KeyShare("pub-ecdsa", "share")),
        )

    /**
     * A [SessionApi] whose setup-message fetch always fails (simulating the failed/lost round that
     * drops the device into its catch block) and that records every `checkKeysignComplete` lookup.
     * The lookup itself throws ("relay has no signature yet"), so `KeysignVerify` returns null and
     * the ceremony ultimately rethrows — without ever constructing a native `tss.KeysignResponse`.
     */
    private class FakeSessionApi : SessionApi {
        val completionLookups = mutableListOf<String>()

        override suspend fun getSetupMessage(
            serverUrl: String,
            sessionId: String,
            messageId: String?,
        ): String = throw RuntimeException("transport error")

        override suspend fun checkKeysignComplete(
            serverUrl: String,
            messageId: String,
        ): tss.KeysignResponse {
            completionLookups += messageId
            throw RuntimeException("not complete yet")
        }

        override suspend fun checkCommittee(serverUrl: String, sessionId: String): List<String> =
            unexpected("checkCommittee")

        override suspend fun startSession(
            serverUrl: String,
            sessionId: String,
            localPartyId: List<String>,
        ) = unexpected("startSession")

        override suspend fun startWithCommittee(
            serverUrl: String,
            sessionId: String,
            committee: List<String>,
        ) = unexpected("startWithCommittee")

        override suspend fun markLocalPartyComplete(
            serverUrl: String,
            sessionId: String,
            localPartyId: List<String>,
        ) = unexpected("markLocalPartyComplete")

        override suspend fun getCompletedParties(
            serverUrl: String,
            sessionId: String,
        ): List<String> = unexpected("getCompletedParties")

        override suspend fun getParticipants(serverUrl: String, sessionId: String): List<String> =
            unexpected("getParticipants")

        override suspend fun sendTssMessage(
            serverUrl: String,
            messageId: String?,
            message: Message,
        ) = unexpected("sendTssMessage")

        override suspend fun getTssMessages(
            serverUrl: String,
            sessionId: String,
            localPartyId: String,
            messageId: String?,
        ): List<Message> = unexpected("getTssMessages")

        override suspend fun deleteTssMessage(
            serverUrl: String,
            sessionId: String,
            localPartyId: String,
            msgHash: String,
            messageId: String?,
        ) = unexpected("deleteTssMessage")

        override suspend fun markLocalPartyKeysignComplete(
            serverUrl: String,
            messageId: String,
            sig: tss.KeysignResponse,
        ) = unexpected("markLocalPartyKeysignComplete")

        override suspend fun uploadSetupMessage(
            serverUrl: String,
            sessionId: String,
            message: String,
            messageId: String?,
        ) = unexpected("uploadSetupMessage")

        private fun unexpected(name: String): Nothing =
            error("Unexpected SessionApi call in test: $name")
    }

    @Test
    fun `DKLS queries the relay with msgHash on signing failure`() = runTest {
        val sessionApi = FakeSessionApi()
        val keysign =
            DKLSKeysign(
                keysignCommittee = listOf("deviceA", "deviceB"),
                mediatorURL = "http://relay.example",
                sessionID = "session-1",
                messageToSign = listOf(message),
                vault = vault(),
                encryptionKeyHex = "00".repeat(32),
                chainPath = "m/44'/60'/0'/0/0",
                isInitiateDevice = false,
                sessionApi = sessionApi,
                encryption = mockk(relaxed = true),
            )

        shouldThrow<RuntimeException> { keysign.keysignWithRetry() }

        sessionApi.completionLookups shouldContain expectedMsgHash
    }

    @Test
    fun `Schnorr queries the relay with msgHash on signing failure`() = runTest {
        val sessionApi = FakeSessionApi()
        val keysign =
            SchnorrKeysign(
                keysignCommittee = listOf("deviceA", "deviceB"),
                mediatorURL = "http://relay.example",
                sessionID = "session-1",
                messageToSign = listOf(message),
                vault = vault(),
                encryptionKeyHex = "00".repeat(32),
                isInitiateDevice = false,
                sessionApi = sessionApi,
                encryption = mockk(relaxed = true),
            )

        shouldThrow<RuntimeException> { keysign.keysignWithRetry() }

        sessionApi.completionLookups shouldContain expectedMsgHash
    }

    @Test
    fun `MLDSA queries the relay with msgHash on signing failure`() = runTest {
        val sessionApi = FakeSessionApi()
        val keysign =
            MldsaKeysign(
                keysignCommittee = listOf("deviceA", "deviceB"),
                mediatorURL = "http://relay.example",
                sessionID = "session-1",
                messageToSign = listOf(message),
                vault = vault(),
                encryptionKeyHex = "00".repeat(32),
                isInitiateDevice = false,
                sessionApi = sessionApi,
                encryption = mockk(relaxed = true),
            )

        shouldThrow<RuntimeException> { keysign.keysignWithRetry() }

        sessionApi.completionLookups shouldContain expectedMsgHash
    }
}
