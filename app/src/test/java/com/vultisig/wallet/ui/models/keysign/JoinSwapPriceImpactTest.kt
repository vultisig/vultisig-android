@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.models.quotes.Fees
import com.vultisig.wallet.data.api.models.quotes.THORChainSwapQuote
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.THORChainSwapPayload
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.swap.FormatLimitOrderLabelsUseCase
import com.vultisig.wallet.ui.models.swap.PriceImpactLevel
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.TransactionType

/**
 * The co-signer's Price Impact row comes off the payload's `slippage_bps`, never off the quote it
 * re-fetches for the fee rows: pools move between initiating and joining, so a fresh figure would
 * make this the one row the two devices legitimately disagree on. The re-fetched quote below
 * deliberately carries a different `slippage_bps` than the payload to prove which one wins.
 */
internal class JoinSwapPriceImpactTest {

    private val tokenRepository: TokenRepository = mockk()
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk()
    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val getDiscountBps: GetDiscountBpsUseCase = mockk()
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper = mockk()
    private val mapSwapTransactionToHistoryData: SwapTransactionToHistoryDataMapper = mockk()
    private val swapQuoteRepository: SwapQuoteRepository = mockk()

    private fun builder() =
        JoinSwapUiModelBuilder(
            tokenRepository = tokenRepository,
            chainAccountAddressRepository = mockk<ChainAccountAddressRepository>(relaxed = true),
            feeServiceComposite = mockk(relaxed = true),
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            convertTokenValueToFiat = convertTokenValueToFiat,
            fiatValueToStringMapper = fiatValueToStringMapper,
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
            swapQuoteRepository = swapQuoteRepository,
            mapSwapTransactionToHistoryData = mapSwapTransactionToHistoryData,
            formatLimitOrderLabels = FormatLimitOrderLabelsUseCase(),
            getDiscountBps = getDiscountBps,
        )

    @Test
    fun `renders the payload's price impact, not the re-fetched quote's`() = runTest {
        stub(SwapProvider.THORCHAIN, refetchedSlippageBps = 900)

        val tx =
            builder()
                .build(
                    payload(),
                    SwapPayload.ThorChain(thorPayload(slippageBps = 133)),
                    vault,
                    AppCurrency.USD,
                )

        val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
        // 133 bps, negated for display like the initiator's row, in the Average band.
        swap.swapTransactionUiModel.priceImpactPercent shouldBe "-1.33%"
        swap.swapTransactionUiModel.priceImpactLevel shouldBe PriceImpactLevel.AVERAGE
    }

    @Test
    fun `bands the payload's figure the way the initiator does`() = runTest {
        stub(SwapProvider.THORCHAIN, refetchedSlippageBps = null)

        val good =
            builder()
                .build(
                    payload(),
                    SwapPayload.ThorChain(thorPayload(slippageBps = 19)),
                    vault,
                    AppCurrency.USD,
                )
        val high =
            builder()
                .build(
                    payload(),
                    SwapPayload.ThorChain(thorPayload(slippageBps = 450)),
                    vault,
                    AppCurrency.USD,
                )

        (good.transactionTypeUiModel as TransactionTypeUiModel.Swap).swapTransactionUiModel.let {
            it.priceImpactPercent shouldBe "-0.19%"
            it.priceImpactLevel shouldBe PriceImpactLevel.GOOD
        }
        (high.transactionTypeUiModel as TransactionTypeUiModel.Swap).swapTransactionUiModel.let {
            it.priceImpactPercent shouldBe "-4.50%"
            it.priceImpactLevel shouldBe PriceImpactLevel.HIGH
        }
    }

    @Test
    fun `hides the row for a payload without the field, even when the re-fetched quote has one`() =
        runTest {
            stub(SwapProvider.THORCHAIN, refetchedSlippageBps = 19)

            val tx =
                builder()
                    .build(
                        payload(),
                        SwapPayload.ThorChain(thorPayload(slippageBps = null)),
                        vault,
                        AppCurrency.USD,
                    )

            val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
            swap.swapTransactionUiModel.priceImpactPercent shouldBe null
            swap.swapTransactionUiModel.priceImpactLevel shouldBe null
        }

    @Test
    fun `renders a zero-impact payload as zero, distinct from an absent one`() = runTest {
        stub(SwapProvider.THORCHAIN, refetchedSlippageBps = null)

        val tx =
            builder()
                .build(
                    payload(),
                    SwapPayload.ThorChain(thorPayload(slippageBps = 0)),
                    vault,
                    AppCurrency.USD,
                )

        val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
        swap.swapTransactionUiModel.priceImpactPercent shouldBe "+0.00%"
        swap.swapTransactionUiModel.priceImpactLevel shouldBe PriceImpactLevel.GOOD
    }

    @Test
    fun `mayachain payload renders its price impact too`() = runTest {
        stub(SwapProvider.MAYA, refetchedSlippageBps = null)

        val tx =
            builder()
                .build(
                    payload(),
                    SwapPayload.MayaChain(thorPayload(slippageBps = 250)),
                    vault,
                    AppCurrency.USD,
                )

        val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
        swap.swapTransactionUiModel.priceImpactPercent shouldBe "-2.50%"
        swap.swapTransactionUiModel.priceImpactLevel shouldBe PriceImpactLevel.AVERAGE
    }

    private fun stub(provider: SwapProvider, refetchedSlippageBps: Int?) {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        every { mapSwapTransactionToHistoryData(any()) } returns mockk(relaxed = true)
        coEvery { getDiscountBps(vault.id, provider) } returns 0
        coEvery { tokenRepository.getNativeToken(Chain.ThorChain.id) } returns rune
        coEvery { swapQuoteRepository.getQuote(provider, any()) } returns
            SwapQuoteResult.Native(quote(provider, refetchedSlippageBps))
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        coEvery { fiatValueToStringMapper(any(), any()) } returns "0.00"
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedTokenValue = "0.02 RUNE",
                formattedFiatValue = "$0.20",
                tokenValue = TokenValue(BigInteger.valueOf(2_000_000L), rune),
                fiatValue = usd("0.20"),
            )
    }

    private fun quote(provider: SwapProvider, slippageBps: Int?): SwapQuote {
        val dst = TokenValue(BigInteger.valueOf(1_000_000_000_000_000_000L), eth)
        val data =
            THORChainSwapQuote(
                dustThreshold = null,
                expectedAmountOut = "1",
                expiry = BigInteger.ZERO,
                fees =
                    Fees(
                        affiliate = "60000000",
                        asset = "0",
                        outbound = "10000000",
                        total = "70000000",
                        slippageBps = slippageBps,
                    ),
                inboundAddress = "inbound",
                inboundConfirmationBlocks = null,
                inboundConfirmationSeconds = null,
                maxStreamingQuantity = 0,
                memo = "=:ETH.ETH:0xdst",
                notes = "",
                outboundDelayBlocks = BigInteger.ZERO,
                outboundDelaySeconds = BigInteger.ZERO,
                recommendedMinAmountIn = "0",
                streamingSwapBlocks = BigInteger.ZERO,
                totalSwapSeconds = 0L,
                warning = "",
                router = null,
                error = null,
            )
        val expiry = Instant.now().plus(5.minutes.toJavaDuration())
        val zero = TokenValue(BigInteger.ZERO, eth)
        return if (provider == SwapProvider.MAYA) {
            SwapQuote.MayaChain(
                expectedDstValue = dst,
                fees = zero,
                expiredAt = expiry,
                recommendedMinTokenValue = zero,
                data = data,
            )
        } else {
            SwapQuote.ThorChain(
                expectedDstValue = dst,
                fees = zero,
                expiredAt = expiry,
                recommendedMinTokenValue = zero,
                data = data,
            )
        }
    }

    private fun payload() =
        KeysignPayload(
            coin = rune,
            toAddress = "thorInbound",
            toAmount = srcValue.value,
            memo = "=:ETH.ETH:0xdst",
            blockChainSpecific =
                BlockChainSpecific.THORChain(
                    accountNumber = BigInteger.ZERO,
                    sequence = BigInteger.ZERO,
                    fee = BigInteger.valueOf(2_000_000L),
                    isDeposit = false,
                    transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private val rune =
        Coin(
            chain = Chain.ThorChain,
            ticker = "RUNE",
            logo = "",
            address = "thorsrc",
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "thorchain",
            contractAddress = "",
            isNativeToken = true,
        )

    private val eth =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xdst",
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private val srcValue = TokenValue(BigInteger.valueOf(100_000_000L), rune)

    private fun thorPayload(slippageBps: Int?) =
        THORChainSwapPayload(
            fromAddress = "thorsrc",
            fromCoin = rune,
            toCoin = eth,
            vaultAddress = "thorInbound",
            routerAddress = null,
            fromAmount = srcValue.value,
            toAmountDecimal = BigDecimal.ONE,
            toAmountLimit = "0",
            streamingInterval = "0",
            streamingQuantity = "0",
            expirationTime = 0UL,
            isAffiliate = true,
            slippageBps = slippageBps,
        )

    private fun usd(amount: String) = FiatValue(BigDecimal(amount), "USD")

    private val vault =
        Vault(id = "vault-1", name = "Main", pubKeyECDSA = "pub", pubKeyEDDSA = "pubed")
}
