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
import com.vultisig.wallet.ui.models.swap.FormatLimitOrderLabelsUseCase
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
 * The destination amount on the verify screen is the quote's *expected* output. Only the memo's
 * `LIM` is enforced, so a minimum may be shown only when the memo carries one (#5711).
 */
internal class SwapTransactionToUiModelMapperMinPayoutTest {

    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper = mockk()
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

    private fun stubCommon() {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        // Render amounts as their plain decimal so the rescaled LIM is asserted exactly, free of
        // the display mapper's locale-dependent grouping.
        every { mapTokenValueToDecimalUiString(any()) } answers
            {
                firstArg<TokenValue>().decimal.stripTrailingZeros().toPlainString()
            }
        coEvery { fiatValueToStringMapper(any(), any()) } returns "$0.00"
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        coEvery { tokenRepository.getNativeToken(any()) } returns rune
    }

    @Test
    fun `a memo carrying a limit surfaces it as the minimum payout`() = runTest {
        stubCommon()

        // 1% tolerance on the #5711 swap: the node bakes the floor into the memo as a 1e8 integer.
        val uiModel =
            mapper().invoke(thorTransaction(memo = "=:THOR.TCY:thor1uet6qz79tu:321308705:va:40"))

        uiModel.minPayout shouldBe "3.21308705"
        // The amount itself stays the expected output — the floor is a separate, smaller number.
        uiModel.dst.value shouldBe "3.24554248"
    }

    @Test
    fun `auto slippage leaves no minimum to show`() = runTest {
        stubCommon()

        // The memo actually signed in #5711: the LIM field is empty, so nothing is enforced and the
        // swap settled 0.37% below the number the screen was calling "min. payout".
        val uiModel = mapper().invoke(thorTransaction(memo = "=:THOR.TCY:thor1uet6qz79tu::va:40"))

        uiModel.minPayout shouldBe null
        uiModel.dst.value shouldBe "3.24554248"
    }

    @Test
    fun `a zero limit is no limit`() = runTest {
        stubCommon()

        val uiModel = mapper().invoke(thorTransaction(memo = "=:THOR.TCY:thor1uet6qz79tu:0:va:40"))

        uiModel.minPayout shouldBe null
    }

    @Test
    fun `an aggregator route never claims a minimum`() = runTest {
        stubCommon()

        // 1inch signs calldata, not a memo. Whatever floor its router enforces isn't visible here,
        // so a memo-shaped string riding the transaction must not be read as one.
        val uiModel = mapper().invoke(oneInchTransaction(memo = "=:ETH.ETH:0xabc:321308705"))

        uiModel.minPayout shouldBe null
    }

    @Test
    fun `a limit order shows no second minimum because its amount is already the floor`() =
        runTest {
            stubCommon()

            val uiModel =
                mapper()
                    .invoke(
                        thorTransaction(
                            memo = "=<:THOR.TCY:thor1uet6qz79tu:321308705/14400/0:va:40",
                            limitOrderTargetPrice = BigDecimal("3.21308705"),
                            limitOrderExpiryHours = 24,
                        )
                    )

            uiModel.isLimitOrder shouldBe true
            uiModel.minPayout shouldBe null
        }

    @Test
    fun `a limit memo naming another asset is not this destination's order`() = runTest {
        stubCommon()

        // A limit order's displayed amount IS the floor its memo enforces, so an order targeting
        // CACAO must not put "min. payout" over a TCY amount.
        val uiModel =
            mapper()
                .invoke(thorTransaction(memo = "=<:MAYA.CACAO:maya1abc:321308705/14400/0:va:40"))

        uiModel.isLimitOrder shouldBe false
        uiModel.minPayout shouldBe null
    }

    @Test
    fun `a memo naming another asset is not this destination's floor`() = runTest {
        stubCommon()

        // The LIM is denominated in the asset the memo names. Labelling a CACAO floor with TCY's
        // ticker would misstate what the signature guarantees (CodeRabbit, #5734).
        val uiModel = mapper().invoke(thorTransaction(memo = "=:MAYA.CACAO:maya1abc:321308705"))

        uiModel.minPayout shouldBe null
    }

    @Test
    fun `an order whose lifetime has no pill is still an order`() = runTest {
        stubCommon()

        // 999 blocks is a valid `=<` interval outside the app's own 12/24/72-hour options, so the
        // Target Price / expiry row cannot render — but the amount is still the enforced floor,
        // and the history card must not call it an expected payout (CodeRabbit, #5734).
        val uiModel =
            mapper()
                .invoke(thorTransaction(memo = "=<:THOR.TCY:thor1uet6qz79tu:321308705/999/0:va:40"))

        uiModel.isLimitOrder shouldBe true
        uiModel.limitTargetPriceLabel shouldBe null
        uiModel.limitExpiryLabel shouldBe null
    }

    private fun thorTransaction(
        memo: String?,
        limitOrderTargetPrice: BigDecimal? = null,
        limitOrderExpiryHours: Int? = null,
    ): RegularSwapTransaction =
        RegularSwapTransaction(
            id = "tx-thor",
            vaultId = "vault-1",
            srcToken = rune,
            srcTokenValue = TokenValue(BigInteger.valueOf(1_00000000), rune),
            dstToken = tcy,
            dstAddress = "thor1Inbound",
            expectedDstTokenValue = expectedDst,
            blockChainSpecific = mockk<BlockChainSpecificAndUtxo>(relaxed = true),
            estimatedFees = TokenValue(BigInteger.valueOf(1_000), tcy),
            gasFees = TokenValue(BigInteger.valueOf(2_000_000), rune),
            memo = memo,
            payload =
                SwapPayload.ThorChain(
                    THORChainSwapPayload(
                        fromAddress = "thor1Owner",
                        fromCoin = rune,
                        toCoin = tcy,
                        vaultAddress = "thor1Vault",
                        routerAddress = null,
                        fromAmount = BigInteger.valueOf(1_00000000),
                        toAmountDecimal = expectedDst.decimal,
                        toAmountLimit = "0",
                        streamingInterval = "1",
                        streamingQuantity = "0",
                        expirationTime = 0uL,
                        isAffiliate = true,
                    )
                ),
            isApprovalRequired = false,
            gasFeeFiatValue = FiatValue(BigDecimal.ZERO, "USD"),
            limitOrderTargetPrice = limitOrderTargetPrice,
            limitOrderExpiryHours = limitOrderExpiryHours,
        )

    private fun oneInchTransaction(memo: String?): RegularSwapTransaction =
        RegularSwapTransaction(
            id = "tx-1inch",
            vaultId = "vault-1",
            srcToken = rune,
            srcTokenValue = TokenValue(BigInteger.valueOf(1_00000000), rune),
            dstToken = tcy,
            dstAddress = "0xRouter",
            expectedDstTokenValue = expectedDst,
            blockChainSpecific = mockk<BlockChainSpecificAndUtxo>(relaxed = true),
            estimatedFees = TokenValue(BigInteger.valueOf(1_000), rune),
            gasFees = TokenValue(BigInteger.valueOf(2_000_000), rune),
            memo = memo,
            payload =
                SwapPayload.EVM(
                    EVMSwapPayloadJson(
                        fromCoin = rune,
                        toCoin = tcy,
                        fromAmount = BigInteger.valueOf(1_00000000),
                        toAmountDecimal = expectedDst.decimal,
                        quote =
                            EVMSwapQuoteJson(
                                dstAmount = "324554248",
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
            gasFeeFiatValue = FiatValue(BigDecimal.ZERO, "USD"),
        )

    private companion object {
        val rune =
            Coin(
                chain = Chain.ThorChain,
                ticker = "RUNE",
                logo = "rune",
                address = "thor1Owner",
                decimal = 8,
                hexPublicKey = "hex",
                priceProviderID = "thorchain",
                contractAddress = "",
                isNativeToken = true,
            )
        val tcy =
            Coin(
                chain = Chain.ThorChain,
                ticker = "TCY",
                logo = "tcy",
                address = "thor1Owner",
                decimal = 8,
                hexPublicKey = "hex",
                priceProviderID = "tcy",
                contractAddress = "tcy",
                isNativeToken = false,
            )

        /** The quote's expected output in #5711: 3.24554248 TCY. */
        val expectedDst = TokenValue(BigInteger.valueOf(324554248), tcy)
    }
}
