package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * The extension sends `toAmount = ""` for a Substrate dApp call that has no transfer amount to
 * review (`sendTx/core/amount.ts`), and iOS reads that as zero. `BigInteger("")` threw "Zero length
 * BigInteger" instead, which the join screen surfaced as "Invalid QR code content" before any of
 * the payload was looked at (#5904).
 */
class KeysignPayloadProtoMapperEmptyAmountTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `an empty wire toAmount reads as zero`() {
        val proto = requireNotNull(outbound(polkadotPayload())).copy(toAmount = "")

        inbound(proto).toAmount shouldBe BigInteger.ZERO
    }

    @Test
    fun `a non-numeric wire toAmount is still refused`() {
        val proto = requireNotNull(outbound(polkadotPayload())).copy(toAmount = "abc")

        shouldThrow<NumberFormatException> { inbound(proto) }
    }

    private fun polkadotPayload() =
        KeysignPayload(
            coin =
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
                ),
            toAddress = "",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Polkadot(
                    recentBlockHash = "0x" + "00".repeat(32),
                    nonce = BigInteger.ZERO,
                    currentBlockNumber = BigInteger.ZERO,
                    specVersion = 0u,
                    transactionVersion = 0u,
                    genesisHash =
                        "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3",
                    gas = 250_000_000uL,
                ),
            memo = null,
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )
}
