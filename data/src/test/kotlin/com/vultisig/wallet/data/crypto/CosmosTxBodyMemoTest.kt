package com.vultisig.wallet.data.crypto

import com.vultisig.wallet.data.usecases.ProtobufAny
import com.vultisig.wallet.data.usecases.TxBody
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Cosmos

/**
 * A dApp's `signDirect` body is a Cosmos SDK `TxBody`, where the memo is field 2. The helpers used
 * to read it back with wallet-core's `Cosmos.SigningInput`, whose memo is field 5, so the dApp memo
 * was dropped and the payload memo silently signed in its place.
 */
class CosmosTxBodyMemoTest {

    private val protoBuf = ProtoBuf { encodeDefaults = false }

    @Test
    fun `reads the memo a dapp put in its tx body`() {
        assertEquals(DAPP_MEMO, cosmosTxBodyMemo(txBody(memo = DAPP_MEMO)))
    }

    @Test
    fun `wallet-core SigningInput cannot see that memo`() {
        val bodyBytes = txBody(memo = DAPP_MEMO)

        assertEquals("", Cosmos.SigningInput.parseFrom(bodyBytes).memo)
    }

    @Test
    fun `a body without a memo reads as null`() {
        assertNull(cosmosTxBodyMemo(txBody(memo = "")))
    }

    @Test
    fun `a body that cannot be walked reads as null instead of throwing`() {
        assertNull(cosmosTxBodyMemo(byteArrayOf(0x12, 0x7f, 0x01)))
    }

    @Test
    fun `an empty body reads as null`() {
        assertNull(cosmosTxBodyMemo(ByteArray(0)))
    }

    @Test
    fun `a memo behind a later field is still found`() {
        val bodyBytes =
            protoBuf.encodeToByteArray(
                TxBody.serializer(),
                TxBody(messages = listOf(MESSAGE), memo = DAPP_MEMO, timeoutHeight = 42UL),
            )

        assertEquals(DAPP_MEMO, cosmosTxBodyMemo(bodyBytes))
    }

    private fun txBody(memo: String): ByteArray =
        protoBuf.encodeToByteArray(
            TxBody.serializer(),
            TxBody(messages = listOf(MESSAGE), memo = memo),
        )

    private companion object {
        const val DAPP_MEMO = "SWAP:THOR.RUNE:thor1abc"

        val MESSAGE = ProtobufAny(typeUrl = "/types.MsgSend", value = ByteArray(4) { it.toByte() })
    }
}
