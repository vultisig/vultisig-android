package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.ReshareMessage
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.proto.v1.ReshareMessageProto
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import vultisig.keygen.v1.LibType

/**
 * Verifies that the `is_tss_batch` proto field round-trips through the Kotlin mappers.
 *
 * The flag (proto field 10 in `reshare_message.proto`) is what tells every joiner that the
 * initiator opted into the batched reshare path. If the mapper drops the field, joiners fall back
 * to the legacy relay namespaces while the initiator hits `/vault/batch/reshare` — which deadlocks
 * the ceremony because the two sides poll different relay channels.
 */
class ReshareMessageProtoMapperTest {

    private val toProto = ReshareMessageToProtoMapperImpl()
    private val fromProto = ReshareMessageFromProtoMapperImpl()

    private fun sampleProto(isTssBatch: Boolean = false) =
        ReshareMessageProto(
            sessionId = "session-123",
            hexChainCode = "abcd",
            serviceName = "vultisig",
            publicKeyEcdsa = "old-pk",
            oldParties = listOf("a", "b", "server"),
            encryptionKeyHex = "deadbeef",
            useVultisigRelay = true,
            oldResharePrefix = "prefix",
            vaultName = "Test Vault",
            libType = LibType.LIB_TYPE_DKLS,
            isTssBatch = isTssBatch,
        )

    private fun sampleMessage(isTssBatch: Boolean = false) =
        ReshareMessage(
            sessionID = "session-123",
            hexChainCode = "abcd",
            serviceName = "vultisig",
            pubKeyECDSA = "old-pk",
            oldParties = listOf("a", "b", "server"),
            encryptionKeyHex = "deadbeef",
            useVultisigRelay = true,
            oldResharePrefix = "prefix",
            vaultName = "Test Vault",
            libType = SigningLibType.DKLS,
            isTssBatch = isTssBatch,
        )

    @Test
    fun `proto with is_tss_batch true maps to ReshareMessage with isTssBatch true`() {
        val message = fromProto(sampleProto(isTssBatch = true))

        assertTrue(message.isTssBatch)
    }

    @Test
    fun `proto with is_tss_batch false maps to ReshareMessage with isTssBatch false`() {
        val message = fromProto(sampleProto(isTssBatch = false))

        assertFalse(message.isTssBatch)
    }

    @Test
    fun `ReshareMessage with isTssBatch true maps to proto with is_tss_batch true`() {
        val proto = toProto(sampleMessage(isTssBatch = true))

        assertTrue(proto.isTssBatch)
    }

    @Test
    fun `ReshareMessage with isTssBatch false maps to proto with is_tss_batch false`() {
        val proto = toProto(sampleMessage(isTssBatch = false))

        assertFalse(proto.isTssBatch)
    }

    @Test
    fun `round trip preserves isTssBatch when set to true`() {
        val original = sampleMessage(isTssBatch = true)

        val roundTripped = fromProto(toProto(original))

        assertEquals(original, roundTripped)
        assertTrue(roundTripped.isTssBatch)
    }

    @Test
    fun `round trip preserves isTssBatch when set to false`() {
        val original = sampleMessage(isTssBatch = false)

        val roundTripped = fromProto(toProto(original))

        assertEquals(original, roundTripped)
        assertFalse(roundTripped.isTssBatch)
    }

    @Test
    fun `round trip preserves all other fields when isTssBatch is true`() {
        val original = sampleMessage(isTssBatch = true)

        val roundTripped = fromProto(toProto(original))

        assertEquals(original.sessionID, roundTripped.sessionID)
        assertEquals(original.hexChainCode, roundTripped.hexChainCode)
        assertEquals(original.serviceName, roundTripped.serviceName)
        assertEquals(original.pubKeyECDSA, roundTripped.pubKeyECDSA)
        assertEquals(original.oldParties, roundTripped.oldParties)
        assertEquals(original.encryptionKeyHex, roundTripped.encryptionKeyHex)
        assertEquals(original.useVultisigRelay, roundTripped.useVultisigRelay)
        assertEquals(original.oldResharePrefix, roundTripped.oldResharePrefix)
        assertEquals(original.vaultName, roundTripped.vaultName)
        assertEquals(original.libType, roundTripped.libType)
    }

    @Test
    fun `default ReshareMessage is not opted into batch — preserves legacy peer compatibility`() {
        val message =
            ReshareMessage(
                sessionID = "s",
                hexChainCode = "c",
                serviceName = "n",
                pubKeyECDSA = "p",
                oldParties = emptyList(),
                encryptionKeyHex = "k",
                useVultisigRelay = false,
                oldResharePrefix = "",
                vaultName = "v",
                libType = SigningLibType.DKLS,
            )

        assertFalse(message.isTssBatch)
    }
}
