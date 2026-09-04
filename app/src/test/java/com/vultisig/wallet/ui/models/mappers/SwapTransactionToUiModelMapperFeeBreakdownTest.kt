@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
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
import com.vultisig.wallet.ui.models.swap.FormatLimitOrderLabelsUseCase
import com.vultisig.wallet.ui.models.swap.PriceImpactLevel
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
            formatLimitOrderLabels = FormatLimitOrderLabelsUseCase(),
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
    fun `Swap Fee row shows the list rate, with the tier discount added back`() = runTest {
        // A Gold vault is quoted at 30 bps, so the provider charges $0.59 on a $200 swap. The
        // verify screen itemizes the 20 bps VULT discount on its own row underneath, so showing
        // the charged fee here read as two separate cuts. The row shows what the swap costs
        // undiscounted; subtracting the discount row lands back on the fee Total Fee is built
        // from.
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { convertTokenValueToFiat(eth, srcValue, AppCurrency.USD) } returns usd("200")
        coEvery { convertTokenValueToFiat(usdt, dstValue, AppCurrency.USD) } returns usd("199")
        coEvery { convertTokenValueToFiat(usdt, totalFees, AppCurrency.USD) } returns usd("1.40")
        coEvery { convertTokenValueToFiat(usdt, affiliateFee, AppCurrency.USD) } returns usd("0.59")
        coEvery { convertTokenValueToFiat(usdt, outboundFee, AppCurrency.USD) } returns usd("1.18")

        val uiModel = mapper().invoke(transaction(vultBpsDiscount = 20))

        // $0.59 charged + 20 bps of the $200 source = the undiscounted $0.99.
        uiModel.providerFee.fiatValue shouldBe "0.990"
        // Total Fee stays net: gas $0.02 + affiliate $0.59 + outbound $1.18.
        uiModel.totalFee shouldBe "1.79"
    }

    @Test
    fun `revalues the discount row with the fee, so a price move cannot unbalance the panel`() =
        runTest {
            // The source doubled between the quote and this screen. The fee rows are re-valued at
            // the current price, so the discount row has to be too — pairing a current-price fee
            // with the $0.40 discount recorded at quote time would leave the rows unable to
            // subtract back to the fee Total Fee is built from.
            every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
            every { mapTokenValueToDecimalUiString(any()) } returns "0"
            coEvery { fiatValueToStringMapper(any(), any()) } answers
                {
                    firstArg<FiatValue>().value.toPlainString()
                }
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
            coEvery { convertTokenValueToFiat(eth, srcValue, AppCurrency.USD) } returns usd("400")
            coEvery { convertTokenValueToFiat(usdt, dstValue, AppCurrency.USD) } returns usd("399")
            coEvery { convertTokenValueToFiat(usdt, totalFees, AppCurrency.USD) } returns
                usd("2.80")
            coEvery { convertTokenValueToFiat(usdt, affiliateFee, AppCurrency.USD) } returns
                usd("1.18")
            coEvery { convertTokenValueToFiat(usdt, outboundFee, AppCurrency.USD) } returns
                usd("2.36")

            val uiModel =
                mapper()
                    .invoke(transaction(vultBpsDiscount = 20, vultBpsDiscountFiatValue = "$0.40"))

            // 20 bps of the $400 source, not the recorded $0.40.
            uiModel.vultBpsDiscountFiatValue shouldBe "0.800"
            // $1.18 charged + that same $0.80, so the row subtracts back to $1.18.
            uiModel.providerFee.fiatValue shouldBe "1.980"
            // Total Fee is the net: gas $0.02 + affiliate $1.18 + outbound $2.36.
            uiModel.totalFee shouldBe "3.56"
        }

    @Test
    fun `Swap Fee row keeps the charged fee when no discount applies`() = runTest {
        // Nothing was waived — a co-signer, or a vault with no tier — so there is no row below to
        // reconcile against and the provider's own figure stands.
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { convertTokenValueToFiat(eth, srcValue, AppCurrency.USD) } returns usd("200")
        coEvery { convertTokenValueToFiat(usdt, dstValue, AppCurrency.USD) } returns usd("199")
        coEvery { convertTokenValueToFiat(usdt, totalFees, AppCurrency.USD) } returns usd("1.40")
        coEvery { convertTokenValueToFiat(usdt, affiliateFee, AppCurrency.USD) } returns usd("0.99")
        coEvery { convertTokenValueToFiat(usdt, outboundFee, AppCurrency.USD) } returns usd("1.18")

        val uiModel = mapper().invoke(transaction())

        uiModel.providerFee.fiatValue shouldBe "0.99"
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

    @Test
    fun `1inch included-in-rate fee adds nothing to the total and carries the display context`() =
        runTest {
            every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
            every { mapTokenValueToDecimalUiString(any()) } returns "0"
            coEvery { fiatValueToStringMapper(any(), any()) } answers
                {
                    firstArg<FiatValue>().value.toPlainString()
                }
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
            coEvery { tokenRepository.getNativeToken(eth.chain.id) } returns eth
            // 1inch stages gas on estimatedFees; without the guard it would count a second time.
            coEvery { convertTokenValueToFiat(eth, oneInchGas, AppCurrency.USD) } returns
                usd("2.00")

            val uiModel = mapper().invoke(oneInchTransaction())

            // Total is the Network Fee alone ($3.50), never gas ($2.00) + gas ($3.50) = $5.50.
            uiModel.totalFee shouldBe "3.50"
            uiModel.swapFeeIncludedInRate shouldBe true
            uiModel.swapFeePercent shouldBe "0.50%"
            uiModel.vultBpsDiscount shouldBe 0
        }

    @Test
    fun `swapkit utxo hides the swap fee and keeps the total at the network fee`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { tokenRepository.getNativeToken(btc.chain.id) } returns btc
        // The SwapKit inbound fee (== the BTC deposit cost) converts to $0.53.
        coEvery { convertTokenValueToFiat(btc, btcInboundFee, AppCurrency.USD) } returns usd("0.53")

        val uiModel = mapper().invoke(swapKitUtxoTransaction())

        // The deposit cost is shown once as the Network Fee ($0.51); the Swap Fee row is hidden and
        // the total is the network fee alone — never $0.51 + $0.53 = $1.04 (#5358, #5321).
        uiModel.swapFeeHidden shouldBe true
        uiModel.totalFee shouldBe "0.51"
    }

    /**
     * #5335: on a thin route the liquidity cost the user actually gave up dwarfs the fee total, but
     * it is baked into the received amount and so contributes nothing to Total Fee. It must reach
     * the screens as its own Price Impact row — without moving into the total, which stays exactly
     * what it was.
     */
    @Test
    fun `surfaces the quote's price impact without changing the fee total`() = runTest {
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
        coEvery { convertTokenValueToFiat(usdt, affiliateFee, AppCurrency.USD) } returns usd("0.00")
        coEvery { convertTokenValueToFiat(usdt, outboundFee, AppCurrency.USD) } returns usd("1.18")

        // 1.58% impact — the node reports it positive, the row shows what the user loses.
        val uiModel = mapper().invoke(transaction(priceImpact = BigDecimal("0.0158")))

        uiModel.priceImpactPercent shouldBe "-1.58%"
        uiModel.priceImpactLevel shouldBe PriceImpactLevel.AVERAGE
        // Unchanged from `splits affiliate and outbound…`: impact never enters the total.
        uiModel.totalFee shouldBe "1.20"
    }

    @Test
    fun `hides the price impact row when the provider reports none`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { tokenRepository.getNativeToken(eth.chain.id) } returns eth
        coEvery { convertTokenValueToFiat(eth, oneInchGas, AppCurrency.USD) } returns usd("2.00")

        val uiModel = mapper().invoke(oneInchTransaction())

        uiModel.priceImpactPercent shouldBe null
        uiModel.priceImpactLevel shouldBe null
    }

    private fun swapKitUtxoTransaction(): RegularSwapTransaction =
        RegularSwapTransaction(
            id = "tx-swapkit-btc",
            vaultId = "vault-1",
            srcToken = btc,
            srcTokenValue = TokenValue(BigInteger.valueOf(16_471), btc),
            dstToken = usdt,
            dstAddress = "bc1qDeposit",
            expectedDstTokenValue = dstValue,
            blockChainSpecific = mockk<BlockChainSpecificAndUtxo>(relaxed = true),
            // SwapKit stages the deposit cost as the inbound fee on estimatedFees; swapFee is null.
            estimatedFees = btcInboundFee,
            swapFee = null,
            outboundFee = null,
            // Network Fee = the BTC deposit miner fee.
            gasFees = TokenValue(BigInteger.valueOf(765), btc),
            memo = null,
            payload =
                SwapPayload.SwapKit(
                    SwapKitSwapPayloadJson(
                        fromCoin = btc,
                        toCoin = usdt,
                        fromAmount = BigInteger.valueOf(16_471),
                        toAmountDecimal = BigDecimal.ONE,
                        txType = "PSBT",
                        txPayload = ByteArray(0),
                        targetAddress = "bc1qDeposit",
                        subProvider = "GARDEN",
                    )
                ),
            isApprovalRequired = false,
            gasFeeFiatValue = usd("0.51"),
        )

    @Test
    fun `never itemizes a 1inch discount, whose fee is baked into the quoted rate`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { tokenRepository.getNativeToken(eth.chain.id) } returns eth
        // A priceable source, so the row is dropped for being un-itemizable rather than unpriced.
        coEvery { convertTokenValueToFiat(eth, srcValue, AppCurrency.USD) } returns usd("200")

        val uiModel = mapper().invoke(oneInchTransaction(vultBpsDiscount = 20))

        // The row reads "included in quoted rate" with no amount, so there is nothing for a
        // "-$0.40" beneath it to subtract from — and 0.50% is not the rate a Gold vault paid.
        uiModel.swapFeeIncludedInRate shouldBe true
        uiModel.swapFeePercent shouldBe null
        uiModel.vultBpsDiscountFiatValue shouldBe null
    }

    private fun oneInchTransaction(vultBpsDiscount: Int = 0): RegularSwapTransaction =
        RegularSwapTransaction(
            id = "tx-1inch",
            vaultId = "vault-1",
            srcToken = eth,
            srcTokenValue = srcValue,
            dstToken = usdt,
            dstAddress = "0xRouter",
            expectedDstTokenValue = dstValue,
            blockChainSpecific = mockk<BlockChainSpecificAndUtxo>(relaxed = true),
            // 1inch stages gas (gasPrice × gas) on estimatedFees; swapFee stays null.
            estimatedFees = oneInchGas,
            swapFee = null,
            outboundFee = null,
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
                                        swapFee = "",
                                        swapFeeTokenContract = "",
                                    ),
                            ),
                        provider = SwapProvider.ONEINCH.getSwapProviderId(),
                    )
                ),
            isApprovalRequired = false,
            gasFeeFiatValue = usd("3.50"),
            swapFeeIncludedInRate = true,
            swapFeePercent = "0.50%",
            vultBpsDiscount = vultBpsDiscount,
        )

    private fun swapKitEvmTransaction(vultBpsDiscount: Int? = null): RegularSwapTransaction =
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
            swapFeePercent = "0.50%",
            vultBpsDiscount = vultBpsDiscount,
        )

    @Test
    fun `never grosses a SwapKit fee, which is an inbound cost no discount came off`() = runTest {
        // SwapKit reports the source-chain inbound (deposit) cost as its fee and bakes the
        // affiliate fee into the quoted destination amount. Adding a Gold tier's 20 bps onto that
        // would invent a discount on a network cost — and the row below it would subtract from a
        // fee it was never added to.
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { tokenRepository.getNativeToken(eth.chain.id) } returns eth
        coEvery { convertTokenValueToFiat(eth, srcValue, AppCurrency.USD) } returns usd("400")
        coEvery { convertTokenValueToFiat(eth, inboundPlaceholder, AppCurrency.USD) } returns
            usd("0.60")

        val uiModel = mapper().invoke(swapKitEvmTransaction(vultBpsDiscount = 20))

        uiModel.providerFee.fiatValue shouldBe "0.60"
        uiModel.swapFeePercent shouldBe null
        uiModel.vultBpsDiscountFiatValue shouldBe null
    }

    @Test
    fun `drops the rate and the discount row when the source has lost its price`() = runTest {
        // Without a source price the discount can't be valued, so the fee can't be grossed by it.
        // Titling the charged fee "0.50%" would then restate the double cut #5803 removes, and the
        // row would read "-$0.00" against a fee it never came off.
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { convertTokenValueToFiat(usdt, affiliateFee, AppCurrency.USD) } returns usd("1.18")
        coEvery { convertTokenValueToFiat(usdt, outboundFee, AppCurrency.USD) } returns usd("2.36")

        val uiModel =
            mapper().invoke(transaction(vultBpsDiscount = 20, vultBpsDiscountFiatValue = "$0.40"))

        uiModel.providerFee.fiatValue shouldBe "1.18"
        uiModel.swapFeePercent shouldBe null
        uiModel.vultBpsDiscountFiatValue shouldBe null
    }

    private fun transaction(
        swapFee: TokenValue? = affiliateFee,
        outboundFee: TokenValue? = SwapTransactionToUiModelMapperFeeBreakdownTest.outboundFee,
        priceImpact: BigDecimal? = null,
        vultBpsDiscount: Int? = null,
        vultBpsDiscountFiatValue: String? = null,
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
            priceImpact = priceImpact,
            vultBpsDiscount = vultBpsDiscount,
            vultBpsDiscountFiatValue = vultBpsDiscountFiatValue,
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

        // 1inch stages its gas estimate (gasPrice × gas) on estimatedFees, denominated in the
        // source-native coin. Used to prove it is not double-counted in the total (#5358).
        val oneInchGas = TokenValue(value = BigInteger.valueOf(1_000), token = eth)

        val btc =
            Coin(
                chain = Chain.Bitcoin,
                ticker = "BTC",
                logo = "btc",
                address = "bc1qOwner",
                decimal = 8,
                hexPublicKey = "hex",
                priceProviderID = "bitcoin",
                contractAddress = "",
                isNativeToken = true,
            )

        // SwapKit stages the BTC deposit cost as its inbound fee on estimatedFees (#5358, #5321).
        val btcInboundFee = TokenValue(value = BigInteger.valueOf(795), token = btc)
    }
}
