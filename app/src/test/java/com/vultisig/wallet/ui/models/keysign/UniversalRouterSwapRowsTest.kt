package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.TokenMetadata
import com.vultisig.wallet.data.repositories.TokenMetadataResolver
import com.vultisig.wallet.data.repositories.UniversalRouterDecoder
import com.vultisig.wallet.data.repositories.UniversalRouterSwapIntent
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class UniversalRouterSwapRowsTest {

    private val tokenMetadataResolver: TokenMetadataResolver = mockk(relaxed = true)

    private val ethCoin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "ethereum",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun erc20(ticker: String, decimals: Int, contractAddress: String) =
        Coin(
            chain = Chain.Ethereum,
            ticker = ticker,
            logo = ticker.lowercase(),
            address = "",
            decimal = decimals,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contractAddress,
            isNativeToken = false,
        )

    private val usdcContract = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    private val daiContract = "0x6b175474e89094c44da98b954eedeac495271d0f"

    private suspend fun buildRows(
        intent: UniversalRouterSwapIntent?,
        vaultCoins: List<Coin> = emptyList(),
        nativeLookup: suspend (Chain) -> Coin? = { ethCoin },
    ): List<DecodedFunctionParam>? =
        universalRouterSwapRows(
            chain = Chain.Ethereum,
            intent = intent,
            allVaults = listOf(Vault(id = "v", name = "vault", coins = vaultCoins)),
            tokenMetadataResolver = tokenMetadataResolver,
            nativeTokenLookup = nativeLookup,
        )

    @Test
    fun `returns null when intent is null`() = runTest { assertNull(buildRows(intent = null)) }

    @Test
    fun `exact-in swap renders four labelled rows with from-to-amountIn-amountOut`() = runTest {
        val intent =
            UniversalRouterSwapIntent(
                fromToken = usdcContract,
                toToken = daiContract,
                amountIn = BigInteger.valueOf(1_000_000L),
                amountOutMin = BigInteger("990000000000000000"),
                isExactOut = false,
            )
        val usdc = erc20("USDC", 6, usdcContract)
        val dai = erc20("DAI", 18, daiContract)

        val rs = assertNotNull(buildRows(intent, vaultCoins = listOf(usdc, dai)))
        assertEquals(4, rs.size)
        assertResId(R.string.decoded_function_from_token, rs[0].label)
        assertEquals("USDC", (rs[0].value as UiText.DynamicString).text)
        assertEquals(usdcContract, rs[0].copyableValue)
        assertEquals(usdcContract, rs[0].secondary)

        assertResId(R.string.decoded_function_amount_in, rs[1].label)
        assertEquals("1 USDC", (rs[1].value as UiText.DynamicString).text)

        assertResId(R.string.decoded_function_to_token, rs[2].label)
        assertEquals("DAI", (rs[2].value as UiText.DynamicString).text)

        assertResId(R.string.decoded_function_min_amount_out, rs[3].label)
        assertEquals("0.99 DAI", (rs[3].value as UiText.DynamicString).text)
    }

    @Test
    fun `exact-out swap uses max-in and amount-out labels`() = runTest {
        val intent =
            UniversalRouterSwapIntent(
                fromToken = usdcContract,
                toToken = daiContract,
                amountIn = BigInteger.valueOf(2_000_000L),
                amountOutMin = BigInteger("1000000000000000000"),
                isExactOut = true,
            )

        val rs = assertNotNull(buildRows(intent))
        assertResId(R.string.decoded_function_max_amount_in, rs[1].label)
        assertResId(R.string.decoded_function_amount_out, rs[3].label)
    }

    @Test
    fun `native ETH side renders the chain fee coin ticker and hides the zero address`() = runTest {
        val intent =
            UniversalRouterSwapIntent(
                fromToken = UniversalRouterDecoder.NATIVE_TOKEN_ADDRESS,
                toToken = usdcContract,
                amountIn = BigInteger("1000000000000000000"),
                amountOutMin = BigInteger.valueOf(2_000_000L),
                isExactOut = false,
            )

        val rs = assertNotNull(buildRows(intent))
        val fromRow = rs[0]
        assertEquals("ETH", (fromRow.value as UiText.DynamicString).text)
        // Native row should not expose the 40-zero sentinel for copy / secondary.
        assertNull(fromRow.copyableValue)
        assertNull(fromRow.secondary)

        val amountInRow = rs[1]
        assertEquals("1 ETH", (amountInRow.value as UiText.DynamicString).text)
    }

    @Test
    fun `unknown ERC20 falls back to metadata resolver`() = runTest {
        coEvery { tokenMetadataResolver.resolve(Chain.Ethereum, usdcContract) } returns
            TokenMetadata(symbol = "USDC", decimals = 6)
        val intent =
            UniversalRouterSwapIntent(
                fromToken = usdcContract,
                toToken = UniversalRouterDecoder.NATIVE_TOKEN_ADDRESS,
                amountIn = BigInteger.valueOf(1_500_000L),
                amountOutMin = BigInteger("700000000000000000"),
                isExactOut = false,
            )

        val rs = assertNotNull(buildRows(intent))
        assertEquals("USDC", (rs[0].value as UiText.DynamicString).text)
        // 1_500_000 / 10^6 = 1.5
        assertEquals("1.5 USDC", (rs[1].value as UiText.DynamicString).text)
    }

    @Test
    fun `unresolved token leaves bare address and raw amount`() = runTest {
        coEvery { tokenMetadataResolver.resolve(Chain.Ethereum, usdcContract) } returns null
        val intent =
            UniversalRouterSwapIntent(
                fromToken = usdcContract,
                toToken = UniversalRouterDecoder.NATIVE_TOKEN_ADDRESS,
                amountIn = BigInteger.valueOf(1_500_000L),
                amountOutMin = BigInteger("700000000000000000"),
                isExactOut = false,
            )

        val rs = assertNotNull(buildRows(intent))
        // Falls back to the bare address (no symbol, no secondary).
        assertEquals(usdcContract, (rs[0].value as UiText.DynamicString).text)
        assertEquals(usdcContract, rs[0].copyableValue)
        assertNull(rs[0].secondary)
        // Raw integer because decimals are unknown.
        assertEquals("1500000", (rs[1].value as UiText.DynamicString).text)
    }

    @Test
    fun `swallows null native lookup gracefully`() = runTest {
        val intent =
            UniversalRouterSwapIntent(
                fromToken = UniversalRouterDecoder.NATIVE_TOKEN_ADDRESS,
                toToken = usdcContract,
                amountIn = BigInteger("1000000000000000000"),
                amountOutMin = BigInteger.valueOf(2_000_000L),
                isExactOut = false,
            )
        val rs = assertNotNull(buildRows(intent, nativeLookup = { null }))
        // No ticker resolved — value is just the raw amount, label still the localised one.
        assertEquals("1000000000000000000", (rs[1].value as UiText.DynamicString).text)
    }

    private fun assertResId(expected: Int, label: UiText) {
        assertTrue(
            label is UiText.StringResource && label.resId == expected,
            "expected R.string $expected, got $label",
        )
    }
}
