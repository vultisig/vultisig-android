@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.mappers

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the canonical-id / display-label split for native SwapKit swaps.
 * [SwapTransactionUiModel.provider] is a behavioral key — it is copied onto the tx-history row and
 * matched against `SwapProvider.SWAPKIT.getSwapProviderId()` to gate SwapKit `/track` settlement
 * (#4757). If the mapper ever puts the human label (`SwapKit (NEAR)`) back into `provider`, native
 * SwapKit swaps stop matching the gate and flip to Success on the source-chain deposit. This drives
 * the gate field off the real mapper output rather than a hardcoded `"SwapKit"`.
 */
internal class SwapTransactionToUiModelMapperSwapKitProviderTest {

    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper =
        mockk(relaxed = true)
    private val fiatValueToStringMapper: FiatValueToStringMapper = mockk(relaxed = true)
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
    fun `native SwapKit keeps canonical provider id while label carries the sub-provider`() =
        runTest {
            every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
            coEvery { tokenRepository.getNativeToken(any()) } returns trx
            coEvery { convertTokenValueToFiat(any(), any(), any()) } returns
                FiatValue(BigDecimal.ZERO, "USD")

            val uiModel = mapper().invoke(swapKitTransaction(subProvider = "NEAR"))

            // Behavioral key stays the canonical id the `/track` gate matches on.
            assertEquals(SwapProvider.SWAPKIT.getSwapProviderId(), uiModel.provider)
            assertEquals("SwapKit", uiModel.provider)
            // Display label carries the sub-provider for the UI.
            assertEquals("SwapKit (NEAR)", uiModel.providerLabel)
        }

    private fun swapKitTransaction(subProvider: String): RegularSwapTransaction {
        val srcValue = TokenValue(value = BigInteger.TEN, token = trx)
        return RegularSwapTransaction(
            id = "tx-1",
            vaultId = "vault-1",
            srcToken = trx,
            srcTokenValue = srcValue,
            dstToken = xrp,
            dstAddress = "rDest",
            expectedDstTokenValue = TokenValue(value = BigInteger.ONE, token = xrp),
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
                        subProvider = subProvider,
                        swapId = "swap-123",
                    )
                ),
            isApprovalRequired = false,
            gasFeeFiatValue = FiatValue(BigDecimal.ZERO, "USD"),
        )
    }

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
    }
}
