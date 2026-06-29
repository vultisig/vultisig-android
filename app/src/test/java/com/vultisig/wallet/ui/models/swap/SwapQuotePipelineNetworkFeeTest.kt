@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test

/**
 * Covers the EVM-aggregator network-fee re-basing wired into [SwapQuotePipeline.resolveNetworkFee]
 * (#5056).
 */
internal class SwapQuotePipelineNetworkFeeTest {

    private val swapGasCalculator: SwapGasCalculator = mockk(relaxed = true)
    private val swapValidator: SwapValidator = mockk(relaxed = true)

    private val pipeline =
        SwapQuotePipeline(
            swapQuoteRepository = mockk(relaxed = true),
            appCurrencyRepository = mockk(relaxed = true),
            referralRepository = mockk(relaxed = true),
            getDiscountBpsUseCase = mockk(relaxed = true),
            convertTokenAndValueToTokenValue = mockk(relaxed = true),
            swapQuoteManager = mockk(relaxed = true),
            swapDiscountChecker = mockk(relaxed = true),
            swapGasCalculator = swapGasCalculator,
            swapValidator = swapValidator,
            fiatValueToString = mockk(relaxed = true),
        )

    @Test
    fun `re-bases the EVM aggregator network fee onto the route gas`() = runTest {
        val ethCoin = coin(Chain.Ethereum)
        val src = sendSrc(ethCoin)
        val rebased = gasResult(ethCoin, BigInteger.valueOf(2_861_460))
        coEvery {
            swapGasCalculator.rebaseEvmSwapNetworkFee(ethCoin, any(), routeGas = 286_146L)
        } returns rebased

        val outcome =
            pipeline.resolveNetworkFee(
                result = success(oneInchQuote(ethCoin, routeGas = 286_146L)),
                src = src,
                vaultId = "vault",
                gasFee = TokenValue(BigInteger.valueOf(6_000_000), ethCoin),
                gasFeeChain = Chain.Ethereum,
                networkFeeTokenValue = TokenValue(BigInteger.valueOf(6_000_000), ethCoin),
            )

        val set = assertIs<NetworkFeeUpdate.Set>(outcome.networkFee)
        assertEquals(BigInteger.valueOf(2_861_460), set.tokenValue.value)
    }

    @Test
    fun `leaves the network fee untouched for a Solana aggregator quote`() = runTest {
        val solCoin = coin(Chain.Solana)
        val outcome =
            pipeline.resolveNetworkFee(
                result = success(oneInchQuote(solCoin, routeGas = 0L)),
                src = sendSrc(solCoin),
                vaultId = "vault",
                gasFee = TokenValue(BigInteger.valueOf(5_000), solCoin),
                gasFeeChain = Chain.Solana,
                networkFeeTokenValue = TokenValue(BigInteger.valueOf(5_000), solCoin),
            )

        assertNull(outcome.networkFee)
        coVerify(exactly = 0) { swapGasCalculator.rebaseEvmSwapNetworkFee(any(), any(), any()) }
    }

    @Test
    fun `clears the stale fee when the gas fee lags the source chain`() = runTest {
        val ethCoin = coin(Chain.Ethereum)
        val outcome =
            pipeline.resolveNetworkFee(
                result = success(oneInchQuote(ethCoin, routeGas = 286_146L)),
                src = sendSrc(ethCoin),
                vaultId = "vault",
                gasFee = TokenValue(BigInteger.valueOf(6_000_000), ethCoin),
                // gasFeeChain lags srcToken.chain right after a token switch.
                gasFeeChain = Chain.Bitcoin,
                networkFeeTokenValue = TokenValue(BigInteger.valueOf(6_000_000), ethCoin),
            )

        assertEquals(NetworkFeeUpdate.Clear, outcome.networkFee)
        coVerify(exactly = 0) { swapGasCalculator.rebaseEvmSwapNetworkFee(any(), any(), any()) }
    }

    private fun oneInchQuote(dstToken: Coin, routeGas: Long) =
        SwapQuote.OneInch(
            expectedDstValue = TokenValue(BigInteger.valueOf(400), dstToken),
            fees = TokenValue(BigInteger.valueOf(9), dstToken),
            expiredAt = Clock.System.now(),
            data =
                EVMSwapQuoteJson(
                    dstAmount = "400",
                    tx =
                        OneInchSwapTxJson(
                            from = "0xsrc",
                            to = "0xrouter",
                            gas = routeGas,
                            data = "0xdata",
                            value = "0",
                            gasPrice = "1",
                        ),
                ),
            provider = "1inch",
        )

    private fun success(quote: SwapQuote) =
        SwapQuotePipelineResult.Success(
            quote = quote,
            provider = SwapProvider.ONEINCH,
            referralCodeToStore = null,
            discountInfo = DiscountInfo(),
            swapFeeFiat = FiatValue(BigDecimal.ZERO, "USD"),
            srcFiatValue = "0",
            providerUiText = UiText.DynamicString(""),
            estimatedDstTokenValue = "0",
            estimatedDstFiatValue = "0",
            expiredAt = Clock.System.now(),
            feeText = "0",
            outboundFeeText = null,
            swapFeePercent = null,
            isUtxoSwap = false,
            utxoDstAddress = null,
            utxoMemo = null,
            srcTokenValue = BigInteger.valueOf(1_000),
        )

    private fun coin(chain: Chain) =
        Coin(
            chain = chain,
            ticker = chain.raw,
            logo = "",
            address = "addr",
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = chain.raw,
            contractAddress = "",
            isNativeToken = true,
        )

    private fun sendSrc(coin: Coin): SendSrc {
        val account =
            Account(
                token = coin,
                tokenValue = TokenValue(BigInteger.valueOf(1_000_000_000_000_000_000L), coin),
                fiatValue = null,
                price = null,
            )
        return SendSrc(
            Address(chain = coin.chain, address = coin.address, accounts = listOf(account)),
            account,
        )
    }

    private fun gasResult(token: Coin, value: BigInteger) =
        GasCalculationResult(
            gasFee = TokenValue(value, token),
            estimated =
                EstimatedGasFee(
                    formattedTokenValue = "$value",
                    formattedFiatValue = "$0.00",
                    tokenValue = TokenValue(value, token),
                    fiatValue = FiatValue(BigDecimal.ZERO, "USD"),
                ),
            chain = token.chain,
        )
}
