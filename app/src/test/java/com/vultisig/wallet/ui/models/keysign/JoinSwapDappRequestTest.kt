@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.DAppMetadata
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.swap.FormatLimitOrderLabelsUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * A co-signed swap that a dApp authored (the keysign carries `dappMetadata`) is recorded as such,
 * so History never offers to try it again through the form; a swap the form authored is not
 * (#5918). The verdict is taken from the payload, the one place the origin is stated.
 */
internal class JoinSwapDappRequestTest {

    private val tokenRepository: TokenRepository = mockk()
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase = mockk()
    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper = mockk()
    private val mapSwapTransactionToHistoryData: SwapTransactionToHistoryDataMapper = mockk()
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk()

    private val builder =
        JoinSwapUiModelBuilder(
            tokenRepository = tokenRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            feeServiceComposite = mockk(relaxed = true),
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            convertTokenValueToFiat = convertTokenValueToFiat,
            fiatValueToStringMapper = fiatValueToStringMapper,
            mapTokenValueToDecimalUiString = mapTokenValueToDecimalUiString,
            swapQuoteRepository = mockk(),
            mapSwapTransactionToHistoryData = mapSwapTransactionToHistoryData,
            formatLimitOrderLabels = FormatLimitOrderLabelsUseCase(),
            getDiscountBps = mockk(),
        )

    @Test
    fun `a dApp keysign records its origin on the history row`() = runTest {
        stub()

        val row = join(dappMetadata = DAppMetadata(name = "Jupiter", url = "", iconUrl = ""))

        row.isDappRequest shouldBe true
    }

    @Test
    fun `a swap the form authored is not marked as a dApp request`() = runTest {
        stub()

        join(dappMetadata = null).isDappRequest shouldBe false
    }

    private suspend fun join(dappMetadata: DAppMetadata?): SwapTransactionHistoryData {
        val result =
            builder.build(
                payload = keysignPayload(dappMetadata),
                swapPayload = SwapPayload.SwapKit(swapKitPayload()),
                vault = vault,
                currency = AppCurrency.USD,
            )
        return result.transactionHistoryData.shouldBeInstanceOf<SwapTransactionHistoryData>()
    }

    private fun stub() {
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        // The mapper never sets the origin itself — the builder stamps it from the payload.
        every { mapSwapTransactionToHistoryData(any()) } returns historyRow
        coEvery { tokenRepository.getNativeToken(Chain.Tron.id) } returns trx
        coEvery { chainAccountAddressRepository.getAddress(any<Coin>(), any()) } returns
            ("Tsrc" to "")
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ONE, "USD")
        coEvery { fiatValueToStringMapper(any(), any()) } returns "1.00"
        coEvery { gasFeeToEstimatedFee(any()) } returns
            EstimatedGasFee(
                formattedTokenValue = "1.1 TRX",
                formattedFiatValue = "$0.11",
                tokenValue = TokenValue(BigInteger.valueOf(1_100_000L), trx),
                fiatValue = FiatValue(BigDecimal("0.11"), "USD"),
            )
    }

    private fun swapKitPayload() =
        SwapKitSwapPayloadJson(
            fromCoin = trx,
            toCoin = eth,
            fromAmount = srcValue.value,
            toAmountDecimal = BigDecimal("0.27"),
            txType = SwapKitSwapPayloadJson.TX_TYPE_TRON,
            txPayload = byteArrayOf(1),
            targetAddress = "Tdeposit",
            subProvider = "NEAR",
            swapFee = "13000000",
            swapFeeChain = "Tron",
            swapFeeTokenId = null,
            swapFeeDecimals = 6,
        )

    private fun keysignPayload(dappMetadata: DAppMetadata?) =
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
            dappMetadata = dappMetadata,
        )

    private val historyRow =
        SwapTransactionHistoryData(
            fromToken = "TRX",
            fromAmount = "2,000",
            fromChain = Chain.Tron.id,
            fromTokenLogo = "",
            toToken = "ETH",
            toAmount = "0.27",
            toChain = Chain.Ethereum.id,
            toTokenLogo = "",
            provider = "SwapKit",
            fiatValue = "$1.00",
            fromAmountDecimal = "2000",
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
