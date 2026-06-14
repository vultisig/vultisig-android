@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.TokenValue
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
 * The verify and keysign screens build their display off [SwapTransactionToUiModelMapper], which
 * recomputes the destination fiat from [SwapTransaction.expectedDstTokenValue] (the model carries
 * no fiat). Without the value-preserving clamp (#4878) an illiquid token's inflated market mark
 * would reappear on the very screens the user signs from, even though the swap form clamps it.
 */
internal class SwapTransactionToUiModelMapperFiatClampTest {

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
    fun `clamps an inflated destination fiat to the source fiat`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { tokenRepository.getNativeToken(any()) } returns trx
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        // Accurate source ($5.26) vs an inflated independent dst mark ($13.18).
        coEvery { convertTokenValueToFiat(trx, srcValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal("5.26"), "USD")
        coEvery { convertTokenValueToFiat(xrp, dstValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal("13.18"), "USD")

        val uiModel = mapper().invoke(transaction())

        uiModel.src.fiatValue shouldBe "5.26"
        // Destination clamps down to the source rather than showing the inflated $13.18.
        uiModel.dst.fiatValue shouldBe "5.26"
    }

    @Test
    fun `keeps a destination fiat that is below the source fiat`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        every { mapTokenValueToDecimalUiString(any()) } returns "0"
        coEvery { tokenRepository.getNativeToken(any()) } returns trx
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                firstArg<FiatValue>().value.toPlainString()
            }
        coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
            FiatValue(BigDecimal.ZERO, "USD")
        coEvery { convertTokenValueToFiat(trx, srcValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal("100"), "USD")
        coEvery { convertTokenValueToFiat(xrp, dstValue, AppCurrency.USD) } returns
            FiatValue(BigDecimal("95"), "USD")

        val uiModel = mapper().invoke(transaction())

        uiModel.src.fiatValue shouldBe "100"
        // Real price impact / fees pass through unclamped.
        uiModel.dst.fiatValue shouldBe "95"
    }

    private fun transaction(): RegularSwapTransaction =
        RegularSwapTransaction(
            id = "tx-1",
            vaultId = "vault-1",
            srcToken = trx,
            srcTokenValue = srcValue,
            dstToken = xrp,
            dstAddress = "rDest",
            expectedDstTokenValue = dstValue,
            blockChainSpecific = mockk<BlockChainSpecificAndUtxo>(relaxed = true),
            estimatedFees = srcValue,
            gasFees = srcValue,
            memo = null,
            payload =
                SwapPayload.SwapKit(
                    SwapKitSwapPayloadJson(
                        fromCoin = trx,
                        toCoin = xrp,
                        fromAmount = BigInteger.TEN,
                        toAmountDecimal = BigDecimal.ONE,
                        txType = SwapKitSwapPayloadJson.TX_TYPE_TRON,
                        txPayload = ByteArray(0),
                        targetAddress = "rDest",
                        subProvider = "",
                        swapId = "swap-123",
                    )
                ),
            isApprovalRequired = false,
            gasFeeFiatValue = FiatValue(BigDecimal.ZERO, "USD"),
        )

    private companion object {
        val trx =
            Coin(
                chain = Chain.Tron,
                ticker = "TRX",
                logo = "trx",
                address = "Towner",
                decimal = 6,
                hexPublicKey = "hex",
                priceProviderID = "tron",
                contractAddress = "",
                isNativeToken = true,
            )
        val xrp =
            Coin(
                chain = Chain.Ripple,
                ticker = "XRP",
                logo = "xrp",
                address = "rOwner",
                decimal = 6,
                hexPublicKey = "hex",
                priceProviderID = "ripple",
                contractAddress = "",
                isNativeToken = true,
            )
        val srcValue = TokenValue(value = BigInteger.TEN, token = trx)
        val dstValue = TokenValue(value = BigInteger.ONE, token = xrp)
    }
}
