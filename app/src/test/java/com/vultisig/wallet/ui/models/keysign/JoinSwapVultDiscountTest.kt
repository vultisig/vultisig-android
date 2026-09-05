@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
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
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The co-signer's verify screen shows the same Swap Fee as the initiator's. The wire carries only
 * the net fee (#5329), so the tier that discounted it is re-derived here from the vault's own VULT
 * balance — both devices are the same vault — rather than left out, which had the two screens
 * disagree by the discount on the transaction they were both about to sign (#5803).
 */
internal class JoinSwapVultDiscountTest {

    private val tokenRepository: TokenRepository = mockk()
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk()
    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val getDiscountBps: GetDiscountBpsUseCase = mockk()
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper = mockk()
    private val mapSwapTransactionToHistoryData: SwapTransactionToHistoryDataMapper = mockk()

    private fun builder() =
        JoinSwapUiModelBuilder(
            tokenRepository = tokenRepository,
            chainAccountAddressRepository = mockk<ChainAccountAddressRepository>(relaxed = true),
            feeServiceComposite = mockk(relaxed = true),
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            convertTokenValueToFiat = convertTokenValueToFiat,
            fiatValueToStringMapper = fiatValueToStringMapper,
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
            swapQuoteRepository = mockk(relaxed = true),
            mapSwapTransactionToHistoryData = mapSwapTransactionToHistoryData,
            formatLimitOrderLabels = FormatLimitOrderLabelsUseCase(),
            getDiscountBps = getDiscountBps,
        )

    @Test
    fun `grosses the co-signer's fee to the list rate off the vault's own tier`() = runTest {
        // Kyber stamped the net $0.60 a Gold vault was charged. 20 bps of the $200 source is $0.40,
        // so the row reads the $1.00 the swap would have cost undiscounted — the same figure the
        // initiator shows — and the discount row subtracts back to the $0.60 in Total Fees.
        stubFees(vultBps = 20)

        val tx = builder().build(payload(), swapPayload(), vault, AppCurrency.USD)

        val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
        swap.swapTransactionUiModel.providerFee.fiatValue shouldBe "1.00"
        swap.swapTransactionUiModel.swapFeePercent shouldBe "0.50%"
        swap.swapTransactionUiModel.vultBpsDiscount shouldBe 20
        swap.swapTransactionUiModel.vultBpsDiscountFiatValue shouldBe "0.40"
    }

    @Test
    fun `leaves the charged fee alone for a vault with no tier`() = runTest {
        stubFees(vultBps = 0)

        val tx = builder().build(payload(), swapPayload(), vault, AppCurrency.USD)

        val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
        // Nothing was waived, so the charge already is the list rate.
        swap.swapTransactionUiModel.providerFee.fiatValue shouldBe "0.60"
        swap.swapTransactionUiModel.swapFeePercent shouldBe "0.50%"
        swap.swapTransactionUiModel.vultBpsDiscountFiatValue shouldBe null
    }

    @Test
    fun `charges the tier before grossing a LI-FI fee it derives itself`() = runTest {
        // No swap-fee stamp on the wire, so the fee is recomputed from the destination amount.
        // `integratorFeeAmount` defaults to the UNDISCOUNTED rate, and grossing that would bill
        // the discount twice: 50 bps of the $200 dst is $1.00, plus $0.40 again = $1.40.
        stubFees(vultBps = 20, provider = SwapProvider.LIFI)

        val tx =
            builder()
                .build(payload(), swapPayload(unstamped(SwapProvider.LIFI)), vault, AppCurrency.USD)

        val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
        // 30 bps charged ($0.60) grossed by the $0.40 waived: the list rate, once.
        swap.swapTransactionUiModel.providerFee.fiatValue shouldBe "1.00"
        swap.swapTransactionUiModel.swapFeePercent shouldBe "0.50%"
        swap.swapTransactionUiModel.vultBpsDiscountFiatValue shouldBe "0.40"
    }

    @Test
    fun `neither grosses nor itemizes a 1inch fee, which is baked into the quoted rate`() =
        runTest {
            // 1inch itemizes no affiliate fee, so the join screen falls back to a gas placeholder
            // for
            // the amount and renders "included in quoted rate" over it. There is nothing to gross,
            // and
            // a discount row under it would subtract from a fee it was never added to.
            stubFees(vultBps = 20, provider = SwapProvider.ONEINCH)

            val tx =
                builder()
                    .build(
                        payload(),
                        swapPayload(unstamped(SwapProvider.ONEINCH)),
                        vault,
                        AppCurrency.USD,
                    )

            val swap = tx.transactionTypeUiModel as TransactionTypeUiModel.Swap
            swap.swapTransactionUiModel.swapFeeIncludedInRate shouldBe true
            swap.swapTransactionUiModel.swapFeePercent shouldBe null
            swap.swapTransactionUiModel.vultBpsDiscount shouldBe null
            swap.swapTransactionUiModel.vultBpsDiscountFiatValue shouldBe null
        }

    /** A quote from a sender that stamps no swap-fee coin context (iOS, Windows, older Android). */
    private fun unstamped(provider: SwapProvider) =
        OneInchSwapTxJson(
                from = "0xsrc",
                to = "0xRouter",
                gas = 100_000L,
                data = "0xdata",
                value = "0",
                gasPrice = "1000000000",
                swapFee = "",
                swapFeeTokenContract = "",
                swapFeeChain = "",
                swapFeeDecimals = null,
            )
            .let { it to provider }

    private fun stubFees(vultBps: Int, provider: SwapProvider = SwapProvider.KYBER) {
        stubFees(vultBps)
        coEvery { getDiscountBps(vault.id, provider) } returns vultBps
        // Declared after the base stubs so it wins: any destination-token amount reads as dollars
        // at 6 decimals, so a fee the builder derives itself is priced by the same rule as one
        // stamped on the wire — and a fee grossed twice shows up as twice the dollars.
        coEvery { convertTokenValueToFiat(usdc, any(), AppCurrency.USD) } answers
            {
                FiatValue(
                    BigDecimal(secondArg<TokenValue>().value).divide(BigDecimal(1_000_000)),
                    "USD",
                )
            }
    }

    private fun stubFees(vultBps: Int) {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        every { mapSwapTransactionToHistoryData(any()) } returns mockk(relaxed = true)
        coEvery { getDiscountBps(vault.id, SwapProvider.KYBER) } returns vultBps
        coEvery { tokenRepository.getNativeToken(Chain.Ethereum.id) } returns eth
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns usd("0")
        // The source notional every affiliate rate is charged against.
        coEvery { convertTokenValueToFiat(eth, srcValue, AppCurrency.USD) } returns usd("200")
        // The net fee the initiator stamped on the wire.
        coEvery { convertTokenValueToFiat(usdc, wireFee, AppCurrency.USD) } returns usd("0.60")
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.setScale(2, java.math.RoundingMode.HALF_UP).toString()
            }
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedTokenValue = "0.0001 ETH",
                formattedFiatValue = "$0.30",
                tokenValue = TokenValue(BigInteger.valueOf(100_000L), eth),
                fiatValue = usd("0.30"),
            )
    }

    private fun payload() =
        KeysignPayload(
            coin = eth,
            toAddress = "0xRouter",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger.valueOf(1_000_000_000L),
                    priorityFeeWei = BigInteger.ONE,
                    nonce = BigInteger.ZERO,
                    gasLimit = BigInteger.valueOf(100_000L),
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private fun swapPayload(override: Pair<OneInchSwapTxJson, SwapProvider>? = null) =
        override?.let { (tx, provider) ->
            SwapPayload.EVM(
                EVMSwapPayloadJson(
                    fromCoin = eth,
                    toCoin = usdc,
                    fromAmount = srcValue.value,
                    toAmountDecimal = BigDecimal("200"),
                    quote = EVMSwapQuoteJson(dstAmount = "200000000", tx = tx),
                    provider = provider.getSwapProviderId(),
                )
            )
        } ?: defaultSwapPayload()

    private fun defaultSwapPayload() =
        SwapPayload.EVM(
            EVMSwapPayloadJson(
                fromCoin = eth,
                toCoin = usdc,
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
                                gasPrice = "1000000000",
                                // Kyber charges its affiliate fee in the destination token, with
                                // the coin context #5329 stamps so the co-signer values it right.
                                swapFee = "600000",
                                swapFeeTokenContract = "0xusdc",
                                swapFeeChain = Chain.Ethereum.id,
                                swapFeeDecimals = 6,
                            ),
                    ),
                provider = SwapProvider.KYBER.getSwapProviderId(),
            )
        )

    private fun usd(amount: String) = FiatValue(BigDecimal(amount), "USD")

    private val eth =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "",
            address = "0xsrc",
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private val usdc =
        Coin(
            chain = Chain.Ethereum,
            ticker = "USDC",
            logo = "",
            address = "0xdst",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "usd-coin",
            contractAddress = "0xusdc",
            isNativeToken = false,
        )

    private val srcValue = TokenValue(BigInteger.valueOf(100_000_000_000_000_000L), eth)
    private val wireFee = TokenValue(BigInteger.valueOf(600_000L), usdc)

    private val vault =
        Vault(id = "vault-1", name = "Main", pubKeyECDSA = "pub", pubKeyEDDSA = "pubed")
}
