package com.vultisig.wallet.presenter.keysign

import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import vultisig.keysign.v1.CosmosSpecific
import vultisig.keysign.v1.EthereumSpecific
import vultisig.keysign.v1.SolanaSpecific
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
        val decodedPayload = result.keysignPayload
        assertNotNull(decodedPayload)
        assertEquals(thorchainSpecific, decodedPayload?.thorchainSpecific)
        assertEquals(toAddress, decodedPayload?.toAddress)
        assertEquals(toAmount, decodedPayload?.toAmount)

        val encodedPayload = protoBuf.encodeToByteArray(payloadProto)
        val result1 = protoBuf.decodeFromByteArray<KeysignPayloadProto>(encodedPayload)
        assertEquals(toAddress, result1.toAddress)
        assertEquals(toAmount, result1.toAmount)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keysignPayloadVariants")
    fun testKeysignMessageNestedPayloadRoundTrip(
        @Suppress("UNUSED_PARAMETER") name: String,
        payloadProto: KeysignPayloadProto,
    ) {
        val sessionId = "test-session-id"
        val serviceName = "serviceName"
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
        val decodedPayload = result.keysignPayload
        assertNotNull(decodedPayload)
        assertEquals(payloadProto, decodedPayload)
    }

    companion object {
        private val testCoin =
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

        @JvmStatic
        fun keysignPayloadVariants(): List<Arguments> =
            listOf(
                Arguments.of(
                    "thorchain",
                    KeysignPayloadProto(
                        coin = testCoin,
                        toAddress = "thor1x6f63myfwktevd6mkspdeus9rea5a72w6ynax2",
                        toAmount = "10000000",
                        vaultPublicKeyEcdsa = "asdfasdf",
                        vaultLocalPartyId = "asdfasdf",
                        thorchainSpecific =
                            THORChainSpecific(
                                accountNumber = 1024uL,
                                sequence = 0uL,
                                fee = 2000000uL,
                                isDeposit = false,
                                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                            ),
                    ),
                ),
                Arguments.of(
                    "ethereum",
                    KeysignPayloadProto(
                        coin = testCoin,
                        toAddress = "0x1234567890abcdef1234567890abcdef12345678",
                        toAmount = "1000000000000000000",
                        vaultPublicKeyEcdsa = "asdfasdf",
                        vaultLocalPartyId = "asdfasdf",
                        ethereumSpecific =
                            EthereumSpecific(
                                maxFeePerGasWei = "20000000000",
                                priorityFee = "1000000000",
                                nonce = 42L,
                                gasLimit = "21000",
                            ),
                    ),
                ),
                Arguments.of(
                    "solana",
                    KeysignPayloadProto(
                        coin = testCoin,
                        toAddress = "So11111111111111111111111111111111111111112",
                        toAmount = "5000000",
                        vaultPublicKeyEcdsa = "asdfasdf",
                        vaultLocalPartyId = "asdfasdf",
                        solanaSpecific =
                            SolanaSpecific(
                                recentBlockHash = "9bFDnEfGV3ELhePbTj5BoXgq2ZbGFvDVqxJJ7rHhwHn1",
                                priorityFee = "100",
                                toTokenAssociatedAddress = null,
                                fromTokenAssociatedAddress = null,
                                programId = false,
                                computeLimit = "200000",
                            ),
                    ),
                ),
                Arguments.of(
                    "cosmos",
                    KeysignPayloadProto(
                        coin = testCoin,
                        toAddress = "cosmos1z98eg2ztdp2glyla62629nrlvczg8s7f0tm3dx",
                        toAmount = "1000000",
                        vaultPublicKeyEcdsa = "asdfasdf",
                        vaultLocalPartyId = "asdfasdf",
                        cosmosSpecific =
                            CosmosSpecific(
                                accountNumber = 2048uL,
                                sequence = 1uL,
                                gas = 200000uL,
                                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                                ibcDenomTraces = null,
                            ),
                    ),
                ),
            )
    }
}
