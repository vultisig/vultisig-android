@file:OptIn(ExperimentalEncodingApi::class)

package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.Encryption
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Regression test for issue #5327: the Schnorr initiator must only create and upload a fresh setup
 * message on the first attempt. On a retry it has to reuse the setup message already on the relay
 * by downloading it — otherwise a peer still mid-protocol on the previous setup message desyncs
 * against a brand-new session. This mirrors the `isInitiateDevice && attempt == 0` guard used by
 * `DklsKeysign` and `MldsaKeysign`.
 *
 * The test pins the upload-vs-download *decision* on a retry: with `attempt = 1` the initiator must
 * call `getSetupMessage` (download) and must NOT call `uploadSetupMessage`. Were the `attempt == 0`
 * guard removed, the initiator would take the upload branch and `getSetupMessage` would never be
 * called — so this test fails. The signing rounds that follow the download touch the goschnorr JNI
 * library, which is unavailable on the host JVM; the ceremony therefore throws once the decision
 * has already been made, which is why the call is wrapped in [shouldThrowAny] and the assertions
 * run afterwards on the recorded relay interactions. The [FakeSessionApi] is hand-written rather
 * than a mockk because mockk eagerly resolves the native `tss.KeysignResponse` return type and
 * crashes with UnsatisfiedLinkError (same constraint as [DklsFamilyRelayRecoveryTest]).
 */
class SchnorrKeysignSetupMessageRetryTest {

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
            keyshares = listOf(KeyShare("pub-eddsa", "share")),
        )

    /** Decryption that returns its ciphertext unchanged, so the test controls the setup bytes. */
    private class IdentityEncryption : Encryption {
        override fun encrypt(data: ByteArray, password: ByteArray): ByteArray = data

        override fun decrypt(data: ByteArray, password: ByteArray): ByteArray? = data
    }

    /**
     * A [SessionApi] that serves a stored setup message on download and records every download and
     * upload so the test can assert which branch the initiator took on a retry.
     */
    private class FakeSessionApi(private val storedSetupMessage: String) : SessionApi {
        val getSetupMessageCalls = mutableListOf<String?>()
        val uploadCalls = mutableListOf<String?>()

        override suspend fun getSetupMessage(
            serverUrl: String,
            sessionId: String,
            messageId: String?,
        ): String {
            getSetupMessageCalls += messageId
            return storedSetupMessage
        }

        override suspend fun uploadSetupMessage(
            serverUrl: String,
            sessionId: String,
            message: String,
            messageId: String?,
        ) {
            uploadCalls += messageId
        }

        override suspend fun checkKeysignComplete(
            serverUrl: String,
            messageId: String,
        ): tss.KeysignResponse = throw RuntimeException("not complete yet")

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

        private fun unexpected(name: String): Nothing =
            error("Unexpected SessionApi call in test: $name")
    }

    @Test
    fun `Schnorr initiator downloads the existing setup message on retry instead of re-uploading`() =
        runTest {
            // The stored setup message is decrypted by IdentityEncryption, so any base64 payload
            // works.
            val sessionApi =
                FakeSessionApi(storedSetupMessage = Base64.encode(byteArrayOf(1, 2, 3)))
            val keysign =
                SchnorrKeysign(
                    keysignCommittee = listOf("deviceA", "deviceB"),
                    mediatorURL = "http://relay.example",
                    sessionID = "session-1",
                    messageToSign = listOf(message),
                    vault = vault(),
                    encryptionKeyHex = "00".repeat(32),
                    isInitiateDevice = true,
                    sessionApi = sessionApi,
                    encryption = IdentityEncryption(),
                )

            // attempt = 1 is a retry: the ceremony fails later in the goschnorr JNI rounds, which
            // are unavailable on the host JVM — but only after the download-vs-upload decision.
            shouldThrowAny {
                keysign.keysignOneMessageWithRetry(attempt = 1, messageToSign = message)
            }

            sessionApi.getSetupMessageCalls shouldContain expectedMsgHash
            sessionApi.uploadCalls.shouldBeEmpty()
        }
}
