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
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.swap.FormatLimitOrderLabelsUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
 * The co-signer re-fetches its own THORChain/MayaChain quote rather than reading the fee off the
 * wire. Asking for it undiscounted quoted the full 50 bps while the initiator had signed a
 * discounted one, so the two devices disagreed on the affiliate fee *and* on the Total Fee built
 * from it — the same #5329 divergence the EVM branch closes, on the two providers it skipped.
 */
internal class JoinSwapNativeDiscountTest {

    private val tokenRepository: TokenRepository = mockk()
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk()
    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val getDiscountBps: GetDiscountBpsUseCase = mockk()
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper = mockk()
    private val mapSwapTransactionToHistoryData: SwapTransactionToHistoryDataMapper = mockk()
    private val swapQuoteRepository: com.vultisig.wallet.data.repositories.SwapQuoteRepository =
        mockk()

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
    fun `quotes the vault's own tier, so both devices sign the same THORChain total`() = runTest {
        val request = stub(SwapProvider.THORCHAIN, vultBps = 20)

        val tx =
            builder().build(payload(), SwapPayload.ThorChain(thorPayload), vault, AppCurrency.USD)

        // Without this the node quotes 50 bps and the co-signer's Total Fee is the undiscounted
        // one, while the initiator signed the discounted quote.
        request.captured.bpsDiscount shouldBe 20
        val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
        // The quoted (net) $0.60 grossed by the $0.40 waived — the initiator's row exactly.
        swap.swapTransactionUiModel.providerFee.fiatValue shouldBe "1.00"
        swap.swapTransactionUiModel.swapFeePercent shouldBe "0.50%"
        swap.swapTransactionUiModel.vultBpsDiscountFiatValue shouldBe "0.40"
        // Total stays built from the NET fee both devices are signing — 0.60 charged +
        // 0.10 outbound + 0.20 gas. Grossing it too would read 1.30.
        swap.swapTransactionUiModel.totalFee shouldBe "0.90"
    }

    @Test
    fun `quotes the vault's own tier on MayaChain too`() = runTest {
        val request = stub(SwapProvider.MAYA, vultBps = 20)

        val tx =
            builder().build(payload(), SwapPayload.MayaChain(thorPayload), vault, AppCurrency.USD)

        request.captured.bpsDiscount shouldBe 20
        val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
        swap.swapTransactionUiModel.providerFee.fiatValue shouldBe "1.00"
        swap.swapTransactionUiModel.vultBpsDiscountFiatValue shouldBe "0.40"
    }

    @Test
    fun `leaves the row on the charged fee for a vault with no tier`() = runTest {
        stub(SwapProvider.THORCHAIN, vultBps = 0)

        val tx =
            builder().build(payload(), SwapPayload.ThorChain(thorPayload), vault, AppCurrency.USD)

        val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
        // Nothing waived, so the charge already is the list rate.
        swap.swapTransactionUiModel.providerFee.fiatValue shouldBe "0.60"
        swap.swapTransactionUiModel.swapFeePercent shouldBe "0.50%"
        swap.swapTransactionUiModel.vultBpsDiscountFiatValue shouldBe null
    }

    private fun stub(
        provider: SwapProvider,
        vultBps: Int,
    ): io.mockk.CapturingSlot<SwapQuoteRequest> {
        val request = slot<SwapQuoteRequest>()
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        every { mapSwapTransactionToHistoryData(any()) } returns mockk(relaxed = true)
        coEvery { getDiscountBps(vault.id, provider) } returns vultBps
        coEvery { tokenRepository.getNativeToken(Chain.ThorChain.id) } returns rune
        coEvery { swapQuoteRepository.getQuote(provider, capture(request)) } returns
            SwapQuoteResult.Native(quote(provider))
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        // The source notional the affiliate rate is charged against.
        coEvery { convertTokenValueToFiat(rune, srcValue, AppCurrency.USD) } returns usd("200")
        // Destination-token fee amounts read as dollars 1:1.
        coEvery { convertTokenValueToFiat(eth, any(), AppCurrency.USD) } answers
            {
                FiatValue(secondArg<TokenValue>().decimal, "USD")
            }
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.setScale(2, java.math.RoundingMode.HALF_UP).toString()
            }
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedTokenValue = "0.02 RUNE",
                formattedFiatValue = "$0.20",
                tokenValue = TokenValue(BigInteger.valueOf(2_000_000L), rune),
                fiatValue = usd("0.20"),
            )
        return request
    }

    private fun quote(provider: SwapProvider): SwapQuote {
        val dst = TokenValue(BigInteger.valueOf(1_000_000_000_000_000_000L), eth)
        // THORChain reports fees in 1e8 units: 0.6 and 0.1 of the destination token.
        val fees =
            Fees(affiliate = "60000000", asset = "0", outbound = "10000000", total = "70000000")
        val data =
            THORChainSwapQuote(
                dustThreshold = null,
                expectedAmountOut = "1",
                expiry = BigInteger.ZERO,
                fees = fees,
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

    private val thorPayload =
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
        )

    private fun usd(amount: String) = FiatValue(BigDecimal(amount), "USD")

    private val vault =
        Vault(id = "vault-1", name = "Main", pubKeyECDSA = "pub", pubKeyEDDSA = "pubed")
}
