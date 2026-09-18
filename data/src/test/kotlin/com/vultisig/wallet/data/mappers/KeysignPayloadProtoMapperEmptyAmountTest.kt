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
 * review (`sendTx/core/amount.ts`). `BigInteger("")` threw "Zero length BigInteger" instead, which
 * the join screen surfaced as "Invalid QR code content" before any of the payload was looked at
 * (#5904). That route is the only one allowed to omit the amount: the mapper is shared by every
 * chain, and a native send whose amount went missing on the wire must keep failing fast rather than
 * reach Verify as a zero the initiator never stated.
 */
class KeysignPayloadProtoMapperEmptyAmountTest {

    private val outbound = PayloadToProtoMapperImpl()
    private val inbound = KeysignPayloadProtoMapperImpl()

    @Test
    fun `an empty wire toAmount reads as zero on a Substrate dApp call`() {
        val proto =
            requireNotNull(outbound(polkadotPayload(memo = PLAYGROUND_MEMO))).copy(toAmount = "")

        inbound(proto).toAmount shouldBe BigInteger.ZERO
    }

    @Test
    fun `an empty wire toAmount on a native Substrate send is still refused`() {
        val proto = requireNotNull(outbound(polkadotPayload(memo = null))).copy(toAmount = "")

        shouldThrow<NumberFormatException> { inbound(proto) }
    }

    @Test
    fun `an empty wire toAmount on another chain is still refused`() {
        val proto = requireNotNull(outbound(ethereumPayload())).copy(toAmount = "")

        shouldThrow<NumberFormatException> { inbound(proto) }
    }

    @Test
    fun `a non-numeric wire toAmount is still refused on a Substrate dApp call`() {
        val proto =
            requireNotNull(outbound(polkadotPayload(memo = PLAYGROUND_MEMO))).copy(toAmount = "abc")

        shouldThrow<NumberFormatException> { inbound(proto) }
    }

    private fun polkadotPayload(memo: String?) =
        keysignPayload(
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
            blockChainSpecific =
                BlockChainSpecific.Polkadot(
                    recentBlockHash = "0x" + "00".repeat(32),
                    nonce = BigInteger.ZERO,
                    currentBlockNumber = BigInteger.ZERO,
                    specVersion = 0u,
                    transactionVersion = 0u,
                    genesisHash = GENESIS,
                    gas = 250_000_000uL,
                ),
            memo = memo,
        )

    private fun ethereumPayload() =
        keysignPayload(
            coin =
                Coin(
                    chain = Chain.Ethereum,
                    ticker = "ETH",
                    logo = "eth",
                    address = "0x0000000000000000000000000000000000000001",
                    decimal = 18,
                    hexPublicKey = "pub",
                    priceProviderID = "ethereum",
                    contractAddress = "",
                    isNativeToken = true,
                ),
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger.ONE,
                    priorityFeeWei = BigInteger.ONE,
                    nonce = BigInteger.ZERO,
                    gasLimit = BigInteger.valueOf(21_000),
                ),
            memo = null,
        )

    private fun keysignPayload(coin: Coin, blockChainSpecific: BlockChainSpecific, memo: String?) =
        KeysignPayload(
            coin = coin,
            toAddress = "",
            toAmount = BigInteger.ZERO,
            blockChainSpecific = blockChainSpecific,
            memo = memo,
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "local",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private companion object {
        const val GENESIS = "0x91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"

        // The memo the extension sends for the Vultisig playground's all-zero raw payload (#5904).
        val PLAYGROUND_MEMO =
            """{"address":"","blockHash":"0x${"00".repeat(32)}","blockNumber":"0x00000000",""" +
                """"era":"0x0000","genesisHash":"$GENESIS","method":"0x0000","nonce":"0x00000000",""" +
                """"specVersion":"0x00000000","tip":"0x${"00".repeat(16)}",""" +
                """"transactionVersion":"0x00000000","signedExtensions":[],"version":4}"""
    }
}
