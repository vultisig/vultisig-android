package com.vultisig.wallet.presenter.keysign

import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import vultisig.keysign.v1.THORChainSpecific
import vultisig.keysign.v1.TransactionType

@OptIn(ExperimentalSerializationApi::class)
class KeysignMessageTest {

    private val protoBuf = ProtoBuf

    @Test
    fun testToJson() {
        val sessionId = "test-session-id"
        val serviceName = "serviceName"
        val toAddress = "thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2"
        val toAmount = "10000000"

        val coinProto =
            CoinProto(
                chain = "thorChain",
                ticker = "RUNE",
                address = "",
                contractAddress = "",
                decimals = 8,
                priceProviderId = "thorchain",
                isNativeToken = true,
                hexPublicKey = "",
                logo = "rune",
            )

        val thorchainSpecific =
            THORChainSpecific(
                accountNumber = 1024uL,
                sequence = 0uL,
                fee = 2000000uL,
                isDeposit = false,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )

        val payloadProto =
            KeysignPayloadProto(
                coin = coinProto,
                toAddress = toAddress,
                toAmount = toAmount,
                vaultPublicKeyEcdsa = "asdfasdf",
                vaultLocalPartyId = "asdfasdf",
                thorchainSpecific = thorchainSpecific,
            )

        val messageProto =
            KeysignMessageProto(
                sessionId = sessionId,
                serviceName = serviceName,
                keysignPayload = payloadProto,
                encryptionKeyHex = "",
                useVultisigRelay = true,
            )

        val encoded = protoBuf.encodeToByteArray(messageProto)
        val result = protoBuf.decodeFromByteArray<KeysignMessageProto>(encoded)

        assertEquals(sessionId, result.sessionId)
        assertEquals(serviceName, result.serviceName)
        assertNotNull(result.keysignPayload?.thorchainSpecific)

        val encodedPayload = protoBuf.encodeToByteArray(payloadProto)
        val result1 = protoBuf.decodeFromByteArray<KeysignPayloadProto>(encodedPayload)
        assertEquals(toAddress, result1.toAddress)
        assertEquals(toAmount, result1.toAmount)
    }
}
