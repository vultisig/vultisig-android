@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.TonKeysignSession
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import com.vultisig.wallet.data.repositories.TonConnectRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TonMessage

/** Unit tests for [PersistTonConnectSessionUseCaseImpl] signTon detection and persistence. */
internal class TonConnectDeepLinkParsingTest {

    private val protoBuf = ProtoBuf
    private val repository: TonConnectRepository = mockk()
    private val useCase = PersistTonConnectSessionUseCaseImpl(repository, protoBuf)

    @Test
    fun `use case is no-op when proto contains no signTon`() = runTest {
        val proto =
            KeysignMessageProto(
                sessionId = "sess-1",
                serviceName = "svc",
                encryptionKeyHex = "aabbcc",
                keysignPayload = null,
                useVultisigRelay = false,
                payloadId = "",
            )
        useCase(proto, "vault-42")
        coVerify(exactly = 0) { repository.saveSession(any()) }
    }

    @Test
    fun `use case persists session when proto contains signTon`() = runTest {
        val signTon = SignTon(tonMessages = listOf(TonMessage(to = "EQabc", amount = "1000000000")))
        val proto =
            KeysignMessageProto(
                sessionId = "sess-1",
                serviceName = "svc",
                encryptionKeyHex = "aabbcc",
                keysignPayload = KeysignPayloadProto(signTon = signTon),
                useVultisigRelay = false,
                payloadId = "",
            )

        val captured = slot<TonKeysignSession>()
        coEvery { repository.saveSession(capture(captured)) } just Runs

        useCase(proto, "vault-42")

        coVerify(exactly = 1) { repository.saveSession(any()) }
        assertEquals("vault-42", captured.captured.vaultId)
        assertTrue(captured.captured.signTonProtoBase64.isNotEmpty())
    }
}
