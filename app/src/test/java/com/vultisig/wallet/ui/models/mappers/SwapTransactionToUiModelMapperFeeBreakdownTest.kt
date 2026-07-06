@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * THORChain / MayaChain swaps carry a fee breakdown (affiliate + outbound + liquidity). The verify
 * / overview screen must show the affiliate-only "Swap Fee" and a separate "Outbound Fee" — the
 * same decomposition the swap form does — rather than folding the outbound fee into the "Swap Fee"
 * label (#5061).
 */
internal class SwapTransactionToUiModelMapperFeeBreakdownTest {

    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper =
        mockk(relaxed = true)
    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk()
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk()
    private val appCurrencyRepository: AppCurrencyRepository = mockk()
    private val tokenRepository: TokenRepository = mockk()

    private fun mapper() =
        SwapTransactionToUiModelMapperImpl(
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
            fiatValueToStringMapper = fiatValueToStringMapper,
            convertTokenValueToFiat = convertTokenValueToFiat,
            appCurrencyRepository = appCurrencyRepository,
            tokenRepository = tokenRepository,
        )

    @Test
    fun `splits affiliate and outbound into Swap Fee and Outbound Fee rows`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        // Source / destination valuation (destination below source → no clamp interference).
        coEvery { convertTokenValueToFiat(eth, srcValue, AppCurrency.USD) } returns usd("100")
        coEvery { convertTokenValueToFiat(usdt, dstValue, AppCurrency.USD) } returns usd("99")
        // Fee breakdown: affiliate $0.00, outbound $1.18, opaque total $1.40 (incl. liquidity).
        coEvery { convertTokenValueToFiat(usdt, totalFees, AppCurrency.USD) } returns usd("1.40")
        coEvery { convertTokenValueToFiat(usdt, affiliateFee, AppCurrency.USD) } returns usd("0.00")
        coEvery { convertTokenValueToFiat(usdt, outboundFee, AppCurrency.USD) } returns usd("1.18")

        val uiModel = mapper().invoke(transaction())

        // "Swap Fee" shows the affiliate portion, not the inflated total.
        uiModel.providerFee.fiatValue shouldBe "0.00"
        // Outbound fee surfaces as its own row.
        uiModel.outboundFee shouldBe "1.18"
        // Total reconciles to gas + affiliate + outbound (liquidity dropped), not gas + total.
        uiModel.totalFee shouldBe "1.20"
    }

    @Test
    fun `keeps the opaque total when there is no fee breakdown`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { convertTokenValueToFiat(eth, srcValue, AppCurrency.USD) } returns usd("100")
        coEvery { convertTokenValueToFiat(usdt, dstValue, AppCurrency.USD) } returns usd("99")
        coEvery { convertTokenValueToFiat(usdt, totalFees, AppCurrency.USD) } returns usd("1.40")

        val uiModel = mapper().invoke(transaction(swapFee = null, outboundFee = null))

        uiModel.providerFee.fiatValue shouldBe "1.40"
        uiModel.outboundFee shouldBe null
        uiModel.totalFee shouldBe "1.42"
    }

    /**
     * Proof for #5121: on a SwapKit EVM route the near-zero `fees[].inbound` placeholder surfaces
     * only in the "Estimated Fees" (providerFee) row — NOT the Network Fee — and the total is the
     * oracle gas bond, not an under-reported value. This pins WHERE the `0.0000…13 ETH` the
     * reporter saw actually renders: the swap-fee row, mis-read as the Network Fee. The Network Fee
     * row is fed by `gasFees`/`gasFeeFiatValue` (the oracle bond), and Total = swap-fee($0.00) +
     * gas.
     */
    @Test
    fun `swapkit evm places the inbound placeholder in the swap-fee row, not the network fee (#5121)`() =
        runTest {
            every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
            every { mapTokenValueToDecimalUiString(any()) } returns "0"
            coEvery { fiatValueToStringMapper(any(), any()) } answers
                {
                    firstArg<FiatValue>().value.toPlainString()
                }
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
            // SwapKit maps the fee onto the source-chain native coin.
            coEvery { tokenRepository.getNativeToken(eth.chain.id) } returns eth
            // The inbound placeholder (130 wei ETH) converts to ~$0.00 — the near-zero value.
            coEvery { convertTokenValueToFiat(eth, inboundPlaceholder, AppCurrency.USD) } returns
                usd("0.00")

            val uiModel = mapper().invoke(swapKitEvmTransaction())

            // The near-zero placeholder shows in the "Estimated Fees" / Swap Fee row…
            uiModel.providerFee.fiatValue shouldBe "0.00"
            // …while the Network Fee row is the oracle gas bond ($3.50 here), never the
            // placeholder…
            uiModel.networkFee.fiatValue shouldBe "3.50"
            // …and the Total is that gas bond, i.e. NOT under-reported (0.00 swap fee + 3.50 gas).
            uiModel.totalFee shouldBe "3.50"
        }

    private fun swapKitEvmTransaction(): RegularSwapTransaction =
        RegularSwapTransaction(
            id = "tx-swapkit",
            vaultId = "vault-1",
            srcToken = eth,
            srcTokenValue = srcValue,
            dstToken = usdt,
            dstAddress = "0xRouter",
            expectedDstTokenValue = dstValue,
            blockChainSpecific = mockk<BlockChainSpecificAndUtxo>(relaxed = true),
            // The SwapKit inbound native-gas placeholder rides estimatedFees; swapFee stays null.
            estimatedFees = inboundPlaceholder,
            swapFee = null,
            outboundFee = null,
            // Network Fee = the oracle bond the tx is signed with.
            gasFees = TokenValue(BigInteger.valueOf(2_000_000_000_000_000L), eth),
            memo = null,
            payload =
                SwapPayload.EVM(
                    EVMSwapPayloadJson(
                        fromCoin = eth,
                        toCoin = usdt,
                        fromAmount = srcValue.value,
                        toAmountDecimal = BigDecimal.ONE,
                        quote =
                            EVMSwapQuoteJson(
                                dstAmount = "400",
                                tx =
                                    OneInchSwapTxJson(
                                        from = "0xsrc",
                                        to = "0xRouter",
                                        gas = 100_000L,
                                        data = "0xdata",
                                        value = "0",
                                        gasPrice = "76833041",
                                        swapFee = "130",
                                        swapFeeTokenContract = "",
                                    ),
                            ),
                        provider = SwapProvider.SWAPKIT.getSwapProviderId(),
                        subProvider = "FLASHNET",
                    )
                ),
            isApprovalRequired = false,
            gasFeeFiatValue = usd("3.50"),
        )

    private fun transaction(
        swapFee: TokenValue? = affiliateFee,
        outboundFee: TokenValue? = SwapTransactionToUiModelMapperFeeBreakdownTest.outboundFee,
    ): RegularSwapTransaction =
        RegularSwapTransaction(
            id = "tx-1",
            vaultId = "vault-1",
            srcToken = eth,
            srcTokenValue = srcValue,
            dstToken = usdt,
            dstAddress = "0xDest",
            expectedDstTokenValue = dstValue,
            blockChainSpecific = mockk<BlockChainSpecificAndUtxo>(relaxed = true),
            estimatedFees = totalFees,
            swapFee = swapFee,
            outboundFee = outboundFee,
            gasFees = srcValue,
            memo = null,
            payload =
                SwapPayload.ThorChain(
                    THORChainSwapPayload(
                        fromAddress = "0xOwner",
                        fromCoin = eth,
                        toCoin = usdt,
                        vaultAddress = "0xVault",
                        routerAddress = null,
                        fromAmount = BigInteger.TEN,
                        toAmountDecimal = BigDecimal.ONE,
                        toAmountLimit = "0",
                        streamingInterval = "1",
                        streamingQuantity = "0",
                        expirationTime = 0uL,
                        isAffiliate = true,
                    )
                ),
            isApprovalRequired = false,
            gasFeeFiatValue = usd("0.02"),
        )

    private companion object {
        fun usd(value: String) = FiatValue(BigDecimal(value), "USD")

        val eth =
            Coin(
                chain = Chain.Ethereum,
                ticker = "ETH",
                logo = "eth",
                address = "0xOwner",
                decimal = 18,
                hexPublicKey = "hex",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )
        val usdt =
            Coin(
                chain = Chain.Ethereum,
                ticker = "USDT",
                logo = "usdt",
                address = "0xOwner",
                decimal = 6,
                hexPublicKey = "hex",
                priceProviderID = "tether",
                contractAddress = "0xUsdt",
                isNativeToken = false,
            )
        val srcValue = TokenValue(value = BigInteger.valueOf(10), token = eth)
        val dstValue = TokenValue(value = BigInteger.valueOf(20), token = usdt)
        val totalFees = TokenValue(value = BigInteger.valueOf(30), token = usdt)
        val affiliateFee = TokenValue(value = BigInteger.valueOf(40), token = usdt)
        val outboundFee = TokenValue(value = BigInteger.valueOf(50), token = usdt)

        // SwapKit's FLASHNET-style near-zero inbound placeholder: 130 wei of native ETH (#5121).
        val inboundPlaceholder = TokenValue(value = BigInteger.valueOf(130), token = eth)
    }
}
