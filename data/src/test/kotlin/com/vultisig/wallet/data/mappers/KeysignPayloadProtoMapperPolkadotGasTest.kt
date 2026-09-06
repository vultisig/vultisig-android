package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * Pins the Substrate network fee (proto `PolkadotSpecific.gas`) across the outbound mapper.
 *
 * The initiator computes the fee, holds it in the domain specific and puts it on the wire for the
 * co-signer. Dropping it at the proto boundary leaves the joining device deserializing the proto3
 * default, so its Verify screen states a zero cost for a send that pays a real one — and that
 * screen is the whole safeguard of co-signing. Bittensor rides the same specific, so it shares both
 * the defect and this cover.
 */
class KeysignPayloadProtoMapperPolkadotGasTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `the network fee survives the domain to proto to domain round-trip`() {
        val proto = requireNotNull(outbound(polkadotPayload(DOT)))

        proto.polkadotSpecific?.gas shouldBe GAS

        val restored = inbound(proto).blockChainSpecific as BlockChainSpecific.Polkadot
        restored.gas shouldBe GAS
    }

    // Bittensor builds the very same specific, so a TAO send loses the fee the same way.
    @Test
    fun `a Bittensor send keeps the fee it shares the specific with`() {
        val proto = requireNotNull(outbound(polkadotPayload(TAO)))

        proto.polkadotSpecific?.gas shouldBe GAS

        val restored = inbound(proto).blockChainSpecific as BlockChainSpecific.Polkadot
        restored.gas shouldBe GAS
    }

    // Nothing else pins this specific, and gas was lost precisely because no test noticed a
    // missing field. Compare the whole thing so the next omission fails here.
    @Test
    fun `every field of the specific round-trips unchanged`() {
        val payload = polkadotPayload(DOT)

        val restored = inbound(requireNotNull(outbound(payload))).blockChainSpecific

        restored shouldBe payload.blockChainSpecific
    }

    private fun polkadotPayload(coin: Coin) =
        KeysignPayload(
            coin = coin,
            toAddress = "13SykFhb5ZM4jVLGvVdMzmxpEVSVJ8Q4B4TfNw4kdgqfnhaB",
            toAmount = BigInteger("15000000000"),
            blockChainSpecific =
                BlockChainSpecific.Polkadot(
                    recentBlockHash =
                        "0x4bd8b3fcd2e5a5f4cbb45b0ec8b1b0da43f38b8b8b0e5f37dfd0f16b0a9e1d55",
                    nonce = BigInteger("3"),
                    currentBlockNumber = BigInteger("24186241"),
                    specVersion = 1_003_004u,
                    transactionVersion = 26u,
                    genesisHash =
                        "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
                    gas = GAS,
                ),
            memo = null,
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        // 0.0165 DOT at 10 decimals — the order of magnitude a real send pays.
        const val GAS = 165_000_000UL

        val DOT =
            Coin(
                chain = Chain.Polkadot,
                ticker = "DOT",
                logo = "dot",
                address = "13SykFhb5ZM4jVLGvVdMzmxpEVSVJ8Q4B4TfNw4kdgqfnhaB",
                decimal = 10,
                hexPublicKey = "pub",
                priceProviderID = "polkadot",
                contractAddress = "",
                isNativeToken = true,
            )

        val TAO =
            Coin(
                chain = Chain.Bittensor,
                ticker = "TAO",
                logo = "bittensor",
                address = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY",
                decimal = 9,
                hexPublicKey = "pub",
                priceProviderID = "bittensor",
                contractAddress = "",
                isNativeToken = true,
            )
    }
}
