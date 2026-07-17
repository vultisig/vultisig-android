@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.keygen

import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.usecases.Encryption
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldBeEmpty
import io.mockk.coEvery
import io.mockk.coVerify
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
 * library, so it cannot be instantiated or mocked in a JVM unit test (see
 * `CustomMessageSignatureFormatTest` for the same limitation). These tests therefore verify the
 * newly added relay-completion query — the gap being fixed — rather than the value stored on the
 * success path.
 */
class DklsKeysignRelayCompletionFallbackTest {

    private val mediatorURL = "https://api.vultisig.com/router"
    private val sessionID = "test-session"
    private val message = "deadbeef"
    private val expectedMsgHash = message.md5()

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
            val sessionApi = mockk<SessionApi>(relaxed = true)
            // Local session can never start/finish — mirrors a lost relay message.
            coEvery { sessionApi.getSetupMessage(any(), any(), any()) } throws
                IOException("Channel is already closed")
            // Relay has no completed signature yet, so recovery does not kick in.
            coEvery { sessionApi.checkKeysignComplete(any(), any()) } throws
                IOException("not complete")

            shouldThrow<Exception> { keysign(sessionApi).keysignWithRetry() }

            coVerify(atLeast = 1) { sessionApi.checkKeysignComplete(any(), eq(expectedMsgHash)) }
        }

    @Test
    fun `does not falsely succeed when the relay has no completed signature`() = runTest {
        val sessionApi = mockk<SessionApi>(relaxed = true)
        coEvery { sessionApi.getSetupMessage(any(), any(), any()) } throws
            IOException("Channel is already closed")
        coEvery { sessionApi.checkKeysignComplete(any(), any()) } throws IOException("not complete")

        val keysign = keysign(sessionApi)
        shouldThrow<Exception> { keysign.keysignWithRetry() }

        keysign.signatures.shouldBeEmpty()
    }
}
