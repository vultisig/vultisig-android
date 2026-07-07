package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the relayed Cosmos gas-limit (proto `CosmosSpecific.gas_limit`) round-trip in both
 * directions.
 *
 * The relayed per-tx gas limit lands in the signed `AuthInfo.Fee.gas_limit`, which every co-signing
 * device hashes into the SignDoc. If the initiator sets a `gasLimit` but the marshalling drops it,
 * the peer device rebuilds a signing input with the static per-chain limit, the two SignDocs
 * diverge and the threshold signature fails. Keeping the forward (outbound) and reverse (inbound)
 * mappers symmetric is what lets every participant sign the identical bytes — and an absent limit
 * must stay absent so pre-#5112 payloads keep their static-limit behavior.
 */
class KeysignPayloadProtoMapperCosmosGasLimitTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `relayed gasLimit survives the KeysignPayload to proto round-trip`() {
        val payload = cosmosPayload(gasLimit = BigInteger.valueOf(123_456))

        val proto = requireNotNull(outbound(payload))
        // The outbound mapper must carry the relayed limit onto the wire.
        assertEquals(123_456UL, proto.cosmosSpecific?.gasLimit)

        // …and the inbound mapper must restore it identically on the peer device.
        val restored = inbound(proto).blockChainSpecific as BlockChainSpecific.Cosmos
        assertEquals(BigInteger.valueOf(123_456), restored.gasLimit)
    }

    @Test
    fun `absent gasLimit stays absent through the round-trip`() {
        val payload = cosmosPayload(gasLimit = null)

        val proto = requireNotNull(outbound(payload))
        assertNull(proto.cosmosSpecific?.gasLimit)

        val restored = inbound(proto).blockChainSpecific as BlockChainSpecific.Cosmos
        assertNull(restored.gasLimit)
    }

    private fun cosmosPayload(gasLimit: BigInteger?) =
        KeysignPayload(
            coin = ATOM,
            toAddress = "cosmos1to000000000000000000000000000000abcd",
            toAmount = BigInteger.valueOf(1_000),
            blockChainSpecific =
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger.valueOf(7),
                    sequence = BigInteger.valueOf(3),
                    gas = BigInteger.valueOf(7_500),
                    ibcDenomTraces = null,
                    transactionType =
                        vultisig.keysign.v1.TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                    gasLimit = gasLimit,
                ),
            memo = null,
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        val ATOM =
            Coin(
                chain = Chain.GaiaChain,
                ticker = "ATOM",
                logo = "atom",
                address = "cosmos1from00000000000000000000000000000abcd",
                decimal = 6,
                hexPublicKey = "020202020202020202020202020202020202020202020202020202020202020202",
                priceProviderID = "cosmos",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
