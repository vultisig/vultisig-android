@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.mediator.Message
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.Encryption
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Regression guard for issue #5314: when a DKLS signing attempt fails (e.g. a relay message lost to
 * a transport error, so the local session never reaches `isFinished`), the device must query the
 * relay for an already-completed signature via `checkKeysignComplete` instead of only retrying and
 * failing.
 *
 * The completed signature is posted under `msgHash` (the md5 of the message), so the fallback must
 * look it up under that same key.
 *
 * Note: `tss.KeysignResponse` is a JNI (gomobile) class whose static initializer loads a native
 * library that isn't available in a JVM unit test. A relaxed mockk of [SessionApi] would try to
 * materialize a default `tss.KeysignResponse` while recording the `checkKeysignComplete` stub and
 * fail with UnsatisfiedLinkError, so this test drives a hand-written [FakeSessionApi] whose
 * `checkKeysignComplete` throws instead — the JNI return type is never instantiated.
 */
class DklsKeysignRelayCompletionFallbackTest {

    private val mediatorURL = "https://api.vultisig.com/router"
    private val sessionID = "test-session"
    private val message = "deadbeef"
    private val expectedMsgHash = message.md5()

    /**
     * Minimal [SessionApi] whose setup-message download always fails (mirroring a lost relay
     * message) and whose relay completion query throws to signal "no completed signature yet".
     * [checkKeysignCompleteCalls] records the message ids the fallback queried. Every other method
     * is unused by [DKLSKeysign.keysignWithRetry]'s failure path and throws if reached.
     */
    private class FakeSessionApi : SessionApi {
        val checkKeysignCompleteCalls = mutableListOf<String>()

        override suspend fun getSetupMessage(
            serverUrl: String,
            sessionId: String,
            messageId: String?,
        ): String = throw IOException("Channel is already closed")

        override suspend fun checkKeysignComplete(
            serverUrl: String,
            messageId: String,
        ): tss.KeysignResponse {
            checkKeysignCompleteCalls += messageId
            throw IOException("not complete")
        }

        override suspend fun checkCommittee(serverUrl: String, sessionId: String): List<String> =
            unsupported()

        override suspend fun startSession(
            serverUrl: String,
            sessionId: String,
            localPartyId: List<String>,
        ) = unsupported()

        override suspend fun startWithCommittee(
            serverUrl: String,
            sessionId: String,
            committee: List<String>,
        ) = unsupported()

        override suspend fun markLocalPartyComplete(
            serverUrl: String,
            sessionId: String,
            localPartyId: List<String>,
        ) = unsupported()

        override suspend fun getCompletedParties(
            serverUrl: String,
            sessionId: String,
        ): List<String> = unsupported()

        override suspend fun getParticipants(serverUrl: String, sessionId: String): List<String> =
            unsupported()

        override suspend fun sendTssMessage(
            serverUrl: String,
            messageId: String?,
            message: Message,
        ) = unsupported()

        override suspend fun getTssMessages(
            serverUrl: String,
            sessionId: String,
            localPartyId: String,
            messageId: String?,
        ): List<Message> = unsupported()

        override suspend fun deleteTssMessage(
            serverUrl: String,
            sessionId: String,
            localPartyId: String,
            msgHash: String,
            messageId: String?,
        ) = unsupported()

        override suspend fun markLocalPartyKeysignComplete(
            serverUrl: String,
            messageId: String,
            sig: tss.KeysignResponse,
        ) = unsupported()

        override suspend fun uploadSetupMessage(
            serverUrl: String,
            sessionId: String,
            message: String,
            messageId: String?,
        ) = unsupported()

        private fun unsupported(): Nothing =
            throw UnsupportedOperationException("not needed for this test")
    }

    private fun keysign(sessionApi: SessionApi): DKLSKeysign {
        val encryption = mockk<Encryption>(relaxed = true)
        val vault =
            Vault(
                id = "test-vault",
                name = "Test Vault",
                localPartyID = "device-A",
                pubKeyECDSA = "pubkey",
                libType = SigningLibType.DKLS,
            )
        return DKLSKeysign(
            keysignCommittee = listOf("device-A", "device-B"),
            mediatorURL = mediatorURL,
            sessionID = sessionID,
            messageToSign = listOf(message),
            vault = vault,
            encryptionKeyHex = "00",
            chainPath = "m/44/60/0/0/0",
            isInitiateDevice = false,
            sessionApi = sessionApi,
            encryption = encryption,
        )
    }

    @Test
    fun `queries the relay for a completed signature under msgHash when a signing attempt fails`() =
        runTest {
            val sessionApi = FakeSessionApi()

            shouldThrow<Exception> { keysign(sessionApi).keysignWithRetry() }

            sessionApi.checkKeysignCompleteCalls.size shouldBeGreaterThanOrEqual 1
            sessionApi.checkKeysignCompleteCalls.forEach { it shouldBe expectedMsgHash }
        }

    @Test
    fun `does not falsely succeed when the relay has no completed signature`() = runTest {
        val sessionApi = FakeSessionApi()

        val keysign = keysign(sessionApi)
        shouldThrow<Exception> { keysign.keysignWithRetry() }

        keysign.signatures.shouldBeEmpty()
    }
}
