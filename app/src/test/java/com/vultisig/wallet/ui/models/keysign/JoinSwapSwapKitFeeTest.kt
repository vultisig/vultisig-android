@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.swap.FormatLimitOrderLabelsUseCase
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * A SwapKit transfer-route co-signer reads the provider fee off the payload's `swap_fee` group and
 * renders it without a second quote. Only a sender that predates the group falls back to the
 * inbound re-fetch; a stated fee this device cannot render shows no row and never throws.
 */
internal class JoinSwapSwapKitFeeTest {

    private val tokenRepository: TokenRepository = mockk()
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk()
    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val getDiscountBps: GetDiscountBpsUseCase = mockk()
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper = mockk()
    private val mapSwapTransactionToHistoryData: SwapTransactionToHistoryDataMapper = mockk()
    private val swapQuoteRepository: SwapQuoteRepository = mockk()
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk()

    private fun builder() =
        JoinSwapUiModelBuilder(
            tokenRepository = tokenRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
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
    fun `renders the fee stated on the wire without re-quoting`() = runTest {
        stub()

        // 13 TRX affiliate + service, as a NEAR route states it in the source asset.
        val tx = join(payload(swapFee = "13000000", swapFeeChain = "Tron", swapFeeDecimals = 6))

        tx.swapFeeHidden shouldBe false
        tx.providerFee.token shouldBe trx
        tx.providerFee.value shouldBe "13000000"
        tx.providerFee.fiatValue shouldBe "1.30"
        // 1.30 swap fee + 0.11 gas.
        tx.totalFee shouldBe "1.41"
        coVerify(exactly = 0) { swapQuoteRepository.getSwapKitInboundFee(any()) }
    }

    @Test
    fun `prices a Chainflip fee in Ethereum USDC, a coin that is neither leg`() = runTest {
        stub()
        val usdc = Coins.Ethereum.USDC

        val tx =
            join(
                payload(
                    swapFee = "650000",
                    swapFeeChain = "Ethereum",
                    swapFeeTokenId = usdc.contractAddress.lowercase(),
                    swapFeeDecimals = 6,
                    subProvider = "CHAINFLIP",
                )
            )

        tx.swapFeeHidden shouldBe false
        tx.providerFee.token.contractAddress shouldBe usdc.contractAddress
        tx.providerFee.token.chain shouldBe Chain.Ethereum
        tx.providerFee.fiatValue shouldBe "0.65"
        tx.totalFee shouldBe "0.76"
        coVerify(exactly = 0) { swapQuoteRepository.getSwapKitInboundFee(any()) }
    }

    @Test
    fun `keeps the inbound re-fetch for a sender that predates the field`() = runTest {
        stub()
        coEvery { swapQuoteRepository.getSwapKitInboundFee(any()) } returns
            TokenValue(BigInteger.valueOf(1_100_000L), trx)

        val tx = join(payload(swapFee = ""))

        tx.swapFeeHidden shouldBe false
        tx.providerFee.value shouldBe "1100000"
        tx.providerFee.fiatValue shouldBe "0.11"
        coVerify(exactly = 1) { swapQuoteRepository.getSwapKitInboundFee(any()) }
    }

    @Test
    fun `legacy re-fetch failure still degrades to a zero fee`() = runTest {
        stub()
        coEvery { swapQuoteRepository.getSwapKitInboundFee(any()) } throws
            SwapKitError.Decoding("boom")

        val tx = join(payload(swapFee = ""))

        tx.providerFee.value shouldBe "0"
        tx.totalFee shouldBe "0.11"
    }

    @Test
    fun `an unknown fee chain renders no row and does not re-fetch or throw`() = runTest {
        stub()

        val tx = join(payload(swapFee = "13000000", swapFeeChain = "Narnia", swapFeeDecimals = 6))

        tx.swapFeeHidden shouldBe true
        // Gas alone: a fee this device cannot price is not silently added to the total either.
        tx.totalFee shouldBe "0.11"
        coVerify(exactly = 0) { swapQuoteRepository.getSwapKitInboundFee(any()) }
    }

    @Test
    fun `a non-integer amount renders no row and does not throw`() = runTest {
        stub()

        val tx = join(payload(swapFee = "13.5", swapFeeChain = "Tron", swapFeeDecimals = 6))

        tx.swapFeeHidden shouldBe true
        coVerify(exactly = 0) { swapQuoteRepository.getSwapKitInboundFee(any()) }
    }

    @Test
    fun `a stated fee in a coin this device cannot resolve renders no row`() = runTest {
        stub()

        val tx =
            join(
                payload(
                    swapFee = "1000000",
                    swapFeeChain = "Solana",
                    swapFeeTokenId = "NotARealMint",
                    swapFeeDecimals = 6,
                )
            )

        tx.swapFeeHidden shouldBe true
        coVerify(exactly = 0) { swapQuoteRepository.getSwapKitInboundFee(any()) }
    }

    @Test
    fun `missing decimals render no row rather than guessing a scale`() = runTest {
        stub()

        val tx = join(payload(swapFee = "13000000", swapFeeChain = "Tron", swapFeeDecimals = null))

        tx.swapFeeHidden shouldBe true
    }

    @Test
    fun `out-of-range decimals render no row instead of throwing on render`() = runTest {
        stub()

        // A negative scale throws out of `10 ^ decimals`; an absurd one burns CPU on every render.
        val negative =
            join(payload(swapFee = "13000000", swapFeeChain = "Tron", swapFeeDecimals = -1))
        val absurd =
            join(payload(swapFee = "13000000", swapFeeChain = "Tron", swapFeeDecimals = 255))

        negative.swapFeeHidden shouldBe true
        absurd.swapFeeHidden shouldBe true
        coVerify(exactly = 0) { swapQuoteRepository.getSwapKitInboundFee(any()) }
    }

    private suspend fun join(swapKit: SwapKitSwapPayloadJson): SwapTransactionUiModel {
        val result =
            builder().build(keysignPayload(), SwapPayload.SwapKit(swapKit), vault, AppCurrency.USD)
        return (result.transactionTypeUiModel as TransactionTypeUiModel.Swap).swapTransactionUiModel
    }

    private fun stub() {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        every { mapSwapTransactionToHistoryData(any()) } returns mockk(relaxed = true)
        coEvery { tokenRepository.getNativeToken(Chain.Tron.id) } returns trx
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any()) } returns
            ("Tsrc" to "")
        // TRX at $0.10, everything else (ETH dst, USDC) at $1 per whole unit.
        coEvery { convertTokenValueToFiat(any(), any(), any()) } answers
            {
                val rate =
                    if (firstArg<Coin>().chain == Chain.Tron) BigDecimal("0.10") else BigDecimal.ONE
                FiatValue(secondArg<TokenValue>().decimal.multiply(rate), "USD")
            }
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.setScale(2, RoundingMode.HALF_UP).toPlainString()
            }
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedTokenValue = "1.1 TRX",
                formattedFiatValue = "$0.11",
                tokenValue = TokenValue(BigInteger.valueOf(1_100_000L), trx),
                fiatValue = FiatValue(BigDecimal("0.11"), "USD"),
            )
    }

    private fun payload(
        swapFee: String,
        swapFeeChain: String? = null,
        swapFeeTokenId: String? = null,
        swapFeeDecimals: Int? = null,
        subProvider: String = "NEAR",
    ) =
        SwapKitSwapPayloadJson(
            fromCoin = trx,
            toCoin = eth,
            fromAmount = srcValue.value,
            toAmountDecimal = BigDecimal("0.27"),
            txType = SwapKitSwapPayloadJson.TX_TYPE_TRON,
            txPayload = byteArrayOf(1),
            targetAddress = "Tdeposit",
            subProvider = subProvider,
            swapFee = swapFee,
            swapFeeChain = swapFeeChain,
            swapFeeTokenId = swapFeeTokenId,
            swapFeeDecimals = swapFeeDecimals,
        )

    private fun keysignPayload() =
        KeysignPayload(
            coin = trx,
            toAddress = "Tdeposit",
            toAmount = srcValue.value,
            memo = null,
            blockChainSpecific =
                BlockChainSpecific.Tron(
                    timestamp = 0uL,
                    expiration = 0uL,
                    blockHeaderTimestamp = 0uL,
                    blockHeaderNumber = 0uL,
                    blockHeaderVersion = 0uL,
                    blockHeaderTxTrieRoot = "",
                    blockHeaderParentHash = "",
                    blockHeaderWitnessAddress = "",
                    gasFeeEstimation = 1_100_000uL,
                ),
            vaultPublicKeyECDSA = "pub",
            vaultLocalPartyID = "party",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    private val trx =
        Coin(
            chain = Chain.Tron,
            ticker = "TRX",
            logo = "",
            address = "Tsrc",
            decimal = 6,
            hexPublicKey = "pub",
            priceProviderID = "tron",
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

    private val srcValue = TokenValue(BigInteger.valueOf(2_000_000_000L), trx)

    private val vault =
        Vault(id = "vault-1", name = "Main", pubKeyECDSA = "pub", pubKeyEDDSA = "pubed")
}
