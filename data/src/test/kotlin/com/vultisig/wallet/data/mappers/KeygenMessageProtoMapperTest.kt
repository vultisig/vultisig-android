package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.KeygenMessage
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import vultisig.keygen.v1.LibType

/**
 * Verifies that the `is_tss_batch` proto field round-trips through the Kotlin mappers.
 *
 * The flag (proto field 9 in `keygen_message.proto`) is what tells a joiner that the initiator
 * opted into the batched keygen / key-import path. If the mapper drops the field, a joiner falls
 * back to the legacy relay namespaces while an iOS/Windows initiator drives the batched `p-ecdsa` /
 * `p-eddsa` channels — which deadlocks the ceremony because the two sides poll different relay
 * channels. This is the `KeygenMessage` counterpart of `ReshareMessageProtoMapper` Test; reshare
 * carried the field for a while before keygen/key-import did, which is exactly why
 * extension-initiated key-import failed only on the Android joiner.
 */
class KeygenMessageProtoMapperTest {

    private val toProto = KeygenMessageToProtoMapperImpl()
    private val fromProto = KeygenMessageFromProtoMapperImpl()

    private fun sampleProto(isTssBatch: Boolean = false) =
        KeygenMessageProto(
            sessionId = "session-123",
            hexChainCode = "abcd",
            serviceName = "vultisig",
            encryptionKeyHex = "deadbeef",
            useVultisigRelay = true,
            vaultName = "Test Vault",
            libType = LibType.LIB_TYPE_DKLS,
            chains = listOf("bitcoin", "ethereum"),
            isTssBatch = isTssBatch,
        )

    private fun sampleMessage(isTssBatch: Boolean = false) =
        KeygenMessage(
            vaultName = "Test Vault",
            sessionID = "session-123",
            hexChainCode = "abcd",
            serviceName = "vultisig",
            encryptionKeyHex = "deadbeef",
            useVultisigRelay = true,
            libType = SigningLibType.DKLS,
            chains = listOf("bitcoin", "ethereum"),
            isTssBatch = isTssBatch,
        )

    @Test
    fun `proto with is_tss_batch true maps to KeygenMessage with isTssBatch true`() {
        assertTrue(fromProto(sampleProto(isTssBatch = true)).isTssBatch)
    }

    @Test
    fun `proto with is_tss_batch false maps to KeygenMessage with isTssBatch false`() {
        assertFalse(fromProto(sampleProto(isTssBatch = false)).isTssBatch)
    }

    @Test
    fun `KeygenMessage with isTssBatch true maps to proto with is_tss_batch true`() {
        assertTrue(toProto(sampleMessage(isTssBatch = true)).isTssBatch)
    }

    @Test
    fun `round trip preserves isTssBatch when set to true`() {
        val original = sampleMessage(isTssBatch = true)

        val roundTripped = fromProto(toProto(original))

        assertEquals(original, roundTripped)
        assertTrue(roundTripped.isTssBatch)
    }

    @Test
    fun `default KeygenMessage is not opted into batch — preserves legacy peer compatibility`() {
        val message =
            KeygenMessage(
                vaultName = "v",
                sessionID = "s",
                hexChainCode = "c",
                serviceName = "n",
                encryptionKeyHex = "k",
                useVultisigRelay = false,
                libType = SigningLibType.DKLS,
            )

        assertFalse(message.isTssBatch)
    }
}
