package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.OneInchQuote
import vultisig.keysign.v1.OneInchSwapPayload as OneInchSwapPayloadProto
import vultisig.keysign.v1.OneInchTransaction
import vultisig.keysign.v1.THORChainSpecific
import vultisig.keysign.v1.TransactionType

/**
 * Pins the OneInch swap-fee coin-context round-trip (commondata `swap_fee_chain` /
 * `swap_fee_token_id` / `swap_fee_decimals`). These fields tell a join-flow co-signer — which has
 * no live quote — which coin the affiliate fee is denominated in, so it renders the right fiat
 * value instead of misreading a destination-token amount (e.g. KyberSwap USDC, 6 decimals) as
 * 18-decimal native. The backward-compat case (a sender that predates the fields) must decode
 * without throwing and surface them as empty/null so the consumer falls back rather than guessing.
 */
class KeysignPayloadProtoMapperSwapFeeContextTest {

    private val inboundMapper = KeysignPayloadProtoMapperImpl()
    private val outboundMapper = PayloadToProtoMapperImpl()

    @Test
    fun `inbound mapper decodes the swap-fee coin context onto the tx`() {
        val proto =
            basePayload(
                oneinchSwapPayload =
                    oneInchProto(
                        tx =
                            protoTx(
                                swapFee = "1500000", // 1.5 USDC, 6 decimals
                                swapFeeChain = "Ethereum",
                                swapFeeTokenId = USDC_CONTRACT,
                                swapFeeDecimals = 6,
                            )
                    )
            )

        val data =
            assertInstanceOf(SwapPayload.EVM::class.java, inboundMapper.invoke(proto).swapPayload)
                .data
        val tx = data.quote.tx

        assertEquals("1500000", tx.swapFee)
        assertEquals("Ethereum", tx.swapFeeChain)
        assertEquals(USDC_CONTRACT, tx.swapFeeTokenContract)
        assertEquals(6, tx.swapFeeDecimals)
    }

    @Test
    fun `inbound mapper tolerates a sender that predates the coin-context fields`() {
        val proto =
            basePayload(
                oneinchSwapPayload =
                    oneInchProto(
                        tx =
                            protoTx(
                                swapFee = "21000000000000000",
                                swapFeeChain = null,
                                swapFeeTokenId = null,
                                swapFeeDecimals = null,
                            )
                    )
            )

        val tx =
            assertInstanceOf(SwapPayload.EVM::class.java, inboundMapper.invoke(proto).swapPayload)
                .data
                .quote
                .tx

        // Empty / null lets the consumer fall back to the heuristic instead of guessing a coin.
        assertEquals("", tx.swapFeeChain)
        assertEquals("", tx.swapFeeTokenContract)
        assertNull(tx.swapFeeDecimals)
    }

    @Test
    fun `outbound mapper maps empty token context to null proto fields`() {
        // Native fee coin: empty token id must serialize as absent (null), not an empty string, so
        // the field stays a clean proto3 `optional` absence on the wire.
        val outbound = requireNotNull(outboundMapper.invoke(domainWith(domainTx(swapFee = "21000"))))
        val tx = requireNotNull(outbound.oneinchSwapPayload?.quote?.tx)

        assertNull(tx.swapFeeChain)
        assertNull(tx.swapFeeTokenId)
        assertNull(tx.swapFeeDecimals)
    }

    @Test
    fun `proto round-trip preserves the destination-token fee context`() {
        val inboundProto =
            basePayload(
                oneinchSwapPayload =
                    oneInchProto(
                        tx =
                            protoTx(
                                swapFee = "1500000",
                                swapFeeChain = "Ethereum",
                                swapFeeTokenId = USDC_CONTRACT,
                                swapFeeDecimals = 6,
                            )
                    )
            )

        val domain = inboundMapper.invoke(inboundProto)
        val roundTripped =
            requireNotNull(requireNotNull(outboundMapper.invoke(domain)).oneinchSwapPayload?.quote?.tx)

        assertEquals("1500000", roundTripped.swapFee)
        assertEquals("Ethereum", roundTripped.swapFeeChain)
        assertEquals(USDC_CONTRACT, roundTripped.swapFeeTokenId)
        assertEquals(6, roundTripped.swapFeeDecimals)
    }

    // ---- helpers ----

    private fun domainWith(tx: OneInchSwapTxJson) =
        inboundMapper
            .invoke(basePayload())
            .copy(
                swapPayload =
                    SwapPayload.EVM(
                        EVMSwapPayloadJson(
                            fromCoin = ethDomainCoin(),
                            toCoin = usdcDomainCoin(),
                            fromAmount = BigInteger("1000000000000000000"),
                            toAmountDecimal = BigDecimal("1.5"),
                            quote = EVMSwapQuoteJson(dstAmount = "1500000", tx = tx),
                            provider = "kyber",
                        )
                    )
            )

    private fun domainTx(
        swapFee: String,
        swapFeeChain: String = "",
        swapFeeTokenContract: String = "",
        swapFeeDecimals: Int? = null,
    ) =
        OneInchSwapTxJson(
            from = "0xuser",
            to = "0xrouter",
            gas = 250000,
            data = "0xdead",
            value = "0",
            gasPrice = "1000000000",
            swapFee = swapFee,
            swapFeeChain = swapFeeChain,
            swapFeeTokenContract = swapFeeTokenContract,
            swapFeeDecimals = swapFeeDecimals,
        )

    private fun oneInchProto(tx: OneInchTransaction) =
        OneInchSwapPayloadProto(
            fromCoin = ETH_COIN,
            toCoin = USDC_COIN,
            fromAmount = "1000000000000000000",
            toAmountDecimal = "1.5",
            quote = OneInchQuote(dstAmount = "1500000", tx = tx),
            provider = "kyber",
        )

    private fun protoTx(
        swapFee: String,
        swapFeeChain: String?,
        swapFeeTokenId: String?,
        swapFeeDecimals: Int?,
    ) =
        OneInchTransaction(
            from = "0xuser",
            to = "0xrouter",
            data = "0xdead",
            value = "0",
            gasPrice = "1000000000",
            gas = 250000,
            swapFee = swapFee,
            swapFeeChain = swapFeeChain,
            swapFeeTokenId = swapFeeTokenId,
            swapFeeDecimals = swapFeeDecimals,
        )

    private fun ethDomainCoin() =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xuser",
            decimal = 18,
            hexPublicKey = "pubkey",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun usdcDomainCoin() =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "0xuser",
            decimal = 6,
            hexPublicKey = "pubkey",
            priceProviderID = "usd-coin",
            contractAddress = USDC_CONTRACT,
            isNativeToken = false,
        )

    private fun basePayload(oneinchSwapPayload: OneInchSwapPayloadProto? = null) =
        KeysignPayloadProto(
            coin = ETH_COIN,
            toAddress = "0xdest",
            toAmount = "1000",
            vaultPublicKeyEcdsa = "pubkey",
            vaultLocalPartyId = "party-1",
            thorchainSpecific =
                THORChainSpecific(
                    accountNumber = 1uL,
                    sequence = 0uL,
                    fee = 2_000_000uL,
                    isDeposit = false,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            oneinchSwapPayload = oneinchSwapPayload,
        )

    companion object {
        private const val USDC_CONTRACT = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"

        private val ETH_COIN =
            CoinProto(
                chain = "Ethereum",
                ticker = "ETH",
                address = "0xuser",
                contractAddress = "",
                decimals = 18,
                priceProviderId = "ethereum",
                isNativeToken = true,
                hexPublicKey = "pubkey",
                logo = "",
            )
        private val USDC_COIN =
            CoinProto(
                chain = "Ethereum",
                ticker = "USDC",
                address = "0xuser",
                contractAddress = USDC_CONTRACT,
                decimals = 6,
                priceProviderId = "usd-coin",
                isNativeToken = false,
                hexPublicKey = "pubkey",
                logo = "",
            )
    }
}
