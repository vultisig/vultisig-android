package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.BitcoinInput
import vultisig.keysign.v1.BitcoinOutput
import vultisig.keysign.v1.SignBitcoin

/**
 * Pins the `signBitcoin` (dApp PSBT) proto round-trip in both directions.
 *
 * `signBitcoin` is the only carrier of the PSBT marker: [BlockChainSpecific.BitcoinPSBT] is a
 * sibling of `UTXO`, not a subtype, so it deliberately maps to no `blockchain_specific` member. The
 * outbound mapper used to drop `signBitcoin`, which left the relayed payload with neither a
 * `sign_bitcoin` block nor a chain-specific one — and the inbound mapper rejects that outright
 * rather than merely signing different bytes.
 */
class KeysignPayloadProtoMapperSignBitcoinTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `signBitcoin survives the KeysignPayload to proto round-trip`() {
        val signBitcoin =
            SignBitcoin(
                version = 2u,
                locktime = 0u,
                inputs =
                    listOf(
                        BitcoinInput(
                            hash =
                                "9f2c1b0e7a5d3c8b6e4f2a1d0c9b8a7f6e5d4c3b2a1908070605040302010000",
                            index = 1u,
                            amount = 250_000L,
                            scriptPubKey = "0014a1b2c3d4e5f60708090a0b0c0d0e0f1011121314",
                            scriptType = "p2wpkh",
                            isOurs = true,
                        )
                    ),
                outputs =
                    listOf(
                        BitcoinOutput(
                            amount = 200_000L,
                            address = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                            scriptPubKey = "0014312e63d4c2b1a0f9e8d7c6b5a4938271605f4e3d",
                            isChange = false,
                        )
                    ),
            )

        val payload =
            KeysignPayload(
                coin = BTC,
                toAddress = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                toAmount = BigInteger("200000"),
                blockChainSpecific = BlockChainSpecific.BitcoinPSBT,
                memo = null,
                vaultPublicKeyECDSA = "pub",
                vaultLocalPartyID = "local",
                libType = null,
                wasmExecuteContractPayload = null,
                signBitcoin = signBitcoin,
            )

        val proto = requireNotNull(outbound(payload))
        // The outbound mapper must carry signBitcoin onto the wire; a PSBT payload has no
        // chain-specific block, so this is the peer's only handle on the transaction.
        assertEquals(signBitcoin, proto.signBitcoin)
        assertNull(proto.utxoSpecific)

        // …and the inbound mapper must restore both the PSBT and its marker on the peer device.
        val restored = inbound(proto)
        assertEquals(signBitcoin, restored.signBitcoin)
        assertEquals(signBitcoin.inputs, restored.signBitcoin?.inputs)
        assertEquals(signBitcoin.outputs, restored.signBitcoin?.outputs)
        assertEquals(BlockChainSpecific.BitcoinPSBT, restored.blockChainSpecific)
    }

    private companion object {
        val BTC =
            Coin(
                chain = Chain.Bitcoin,
                ticker = "BTC",
                logo = "bitcoin",
                address = "bc1q9d4ywgfnd8h43da5tpcxcn6ajv590cg6d3tg6a",
                decimal = 8,
                hexPublicKey = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
                priceProviderID = "bitcoin",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
