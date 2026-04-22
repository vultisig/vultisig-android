@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.TonConnectSession
import com.vultisig.wallet.data.repositories.TonConnectRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.KeysignMessage
import vultisig.keysign.v1.KeysignPayload
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TonMessage

internal class PersistTonConnectSessionUseCaseImplTest {

    private val repository: TonConnectRepository = mockk()
    private val protoBuf = ProtoBuf

    private val useCase = PersistTonConnectSessionUseCaseImpl(repository, protoBuf)

    private fun message(signTon: SignTon? = null): KeysignMessage =
        KeysignMessage(
            sessionId = "s",
            serviceName = "svc",
            encryptionKeyHex = "",
            keysignPayload = signTon?.let { KeysignPayload(signTon = it) },
            useVultisigRelay = false,
            payloadId = "",
        )

    @Test
    fun `no-op when keysignPayload is null`() = runTest {
        useCase(message(null), vaultId = "v-1")
        coVerify(exactly = 0) { repository.saveSession(any()) }
    }

    @Test
    fun `no-op when signTon is null`() = runTest {
        val msg =
            KeysignMessage(
                sessionId = "s",
                serviceName = "svc",
                encryptionKeyHex = "",
                keysignPayload = KeysignPayload(),
                useVultisigRelay = false,
                payloadId = "",
            )
        useCase(msg, vaultId = "v-1")
        coVerify(exactly = 0) { repository.saveSession(any()) }
    }

    @Test
    fun `persists session with vaultId when signTon is present`() = runTest {
        val signTon =
            SignTon(
                tonMessages =
                    listOf(TonMessage(to = "UQabc", amount = "1", payload = null, stateInit = null))
            )
        val captured = slot<TonConnectSession>()
        coEvery { repository.saveSession(capture(captured)) } just Runs

        useCase(message(signTon), vaultId = "v-1")

        coVerify(exactly = 1) { repository.saveSession(any()) }
        assertEquals("v-1", captured.captured.vaultId)
        assertEquals("", captured.captured.clientId)
    }
}
