@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.OneInchTokenJson
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.utils.NetworkException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class EvmCoinFinderTest {

    private val oneInchApi: OneInchApi = mockk()
    private val evmApi: EvmApi = mockk()
    private val evmApiFactory: EvmApiFactory = mockk()
    private val finder = EvmCoinFinderImpl(oneInchApi, evmApiFactory)

    @Test
    fun `ONE_INCH_SUPPORTED_CHAINS matches the SDK resolver`() {
        // Mirrors `vultisig-sdk/.../find/resolvers/evm/index.ts` and iOS
        // `EvmCoinFinder.oneInchSupportedChains`; changing this set requires a
        // matching change in the SDK and iOS.
        assertEquals(
            setOf(
                Chain.Ethereum,
                Chain.Base,
                Chain.Arbitrum,
                Chain.Polygon,
                Chain.Optimism,
                Chain.BscChain,
                Chain.Avalanche,
            ),
            EvmCoinFinderImpl.ONE_INCH_SUPPORTED_CHAINS,
        )
    }

    @Test
    fun `EVM chains without a 1inch surface are not in the supported set`() {
        listOf(
                Chain.Blast,
                Chain.CronosChain,
                Chain.ZkSync,
                Chain.Mantle,
                Chain.Sei,
                Chain.Hyperliquid,
            )
            .forEach { chain ->
                assertTrue(
                    chain !in EvmCoinFinderImpl.ONE_INCH_SUPPORTED_CHAINS,
                    "$chain must route through the TokensStore fallback path",
                )
            }
    }

    @Test
    fun `find drops native sentinel and tokens that fail the allowlist`() = runTest {
        coEvery { oneInchApi.getContractsWithBalance(Chain.BscChain, ADDRESS) } returns
            listOf(NATIVE_SENTINEL, GOOD_CONTRACT, EMPTY_LOGO_CONTRACT, NON_COINGECKO_CONTRACT)
        coEvery {
            oneInchApi.getTokensByContracts(
                Chain.BscChain,
                listOf(GOOD_CONTRACT, EMPTY_LOGO_CONTRACT, NON_COINGECKO_CONTRACT),
            )
        } returns
            mapOf(
                GOOD_CONTRACT to
                    oneInchToken(
                        address = GOOD_CONTRACT,
                        symbol = "GOOD",
                        logoURI = "https://tokens.example.com/good.png",
                        providers = listOf("1inch", "CoinGecko"),
                    ),
                EMPTY_LOGO_CONTRACT to
                    oneInchToken(
                        address = EMPTY_LOGO_CONTRACT,
                        symbol = "NOLOGO",
                        logoURI = "",
                        providers = listOf("CoinGecko"),
                    ),
                NON_COINGECKO_CONTRACT to
                    oneInchToken(
                        address = NON_COINGECKO_CONTRACT,
                        symbol = "DUST",
                        logoURI = "https://tokens.example.com/dust.png",
                        providers = listOf("1inch"),
                    ),
            )

        val coins = finder.find(Chain.BscChain, ADDRESS)

        assertEquals(1, coins.size)
        val coin = coins.single()
        assertEquals(GOOD_CONTRACT, coin.contractAddress)
        assertEquals("GOOD", coin.ticker)
        assertEquals("https://tokens.example.com/good.png", coin.logo)
        assertEquals(Chain.BscChain, coin.chain)
    }

    @Test
    fun `find prefers the curated catalog entry over the 1inch metadata`() = runTest {
        // USDC on Ethereum is in `Coins.coins` with a bundled logo + working priceProviderID;
        // we must use those rather than the raw 1inch fields. 1inch returns lowercase, the
        // curated entry uses mixed case — lookup must be case-insensitive.
        val usdcLowercase = USDC_ETHEREUM_CONTRACT.lowercase()
        coEvery { oneInchApi.getContractsWithBalance(Chain.Ethereum, ADDRESS) } returns
            listOf(usdcLowercase)
        coEvery { oneInchApi.getTokensByContracts(Chain.Ethereum, listOf(usdcLowercase)) } returns
            mapOf(
                usdcLowercase to
                    oneInchToken(
                        address = usdcLowercase,
                        symbol = "USDC",
                        logoURI = "https://tokens.1inch.io/$usdcLowercase.png",
                        providers = listOf("CoinGecko"),
                    )
            )
        every { evmApiFactory.createEvmApi(Chain.Ethereum) } returns evmApi
        coEvery { evmApi.getERC20Balance(ADDRESS, VULT_ETHEREUM_CONTRACT_LOWER) } returns
            BigInteger.ZERO

        val coins = finder.find(Chain.Ethereum, ADDRESS)

        val usdc = coins.single { it.ticker == "USDC" }
        assertEquals("usdc", usdc.logo, "Curated bundled logo, not the 1inch CDN URL")
        assertEquals(
            "usd-coin",
            usdc.priceProviderID,
            "Curated priceProviderID, not the empty 1inch default",
        )
    }

    @Test
    fun `find adds VULT on Ethereum when held but 1inch omits it`() = runTest {
        coEvery { oneInchApi.getContractsWithBalance(Chain.Ethereum, ADDRESS) } returns emptyList()
        every { evmApiFactory.createEvmApi(Chain.Ethereum) } returns evmApi
        coEvery { evmApi.getERC20Balance(ADDRESS, VULT_ETHEREUM_CONTRACT_LOWER) } returns
            BigInteger.valueOf(1_000_000)

        val coins = finder.find(Chain.Ethereum, ADDRESS)

        assertEquals(1, coins.size)
        val vult = coins.single()
        assertEquals("VULT", vult.ticker)
        assertEquals("vulti", vult.logo)
        assertEquals("vultisig", vult.priceProviderID)
    }

    @Test
    fun `find does not add VULT on non-Ethereum chains`() = runTest {
        // Defensive: even if a non-Ethereum probe somehow surfaced VULT, the top-up branch must
        // never fire. We assert by leaving `createEvmApi(Chain.BscChain)` un-stubbed — any call
        // would throw `MockKException` and fail the test.
        coEvery { oneInchApi.getContractsWithBalance(Chain.BscChain, ADDRESS) } returns emptyList()

        val coins = finder.find(Chain.BscChain, ADDRESS)

        assertTrue(coins.isEmpty())
        coVerify(exactly = 0) { evmApi.getERC20Balance(any(), any()) }
    }

    @Test
    fun `find skips the VULT top-up probe when 1inch already surfaced VULT`() = runTest {
        val vultLowercase = VULT_ETHEREUM_CONTRACT_LOWER
        coEvery { oneInchApi.getContractsWithBalance(Chain.Ethereum, ADDRESS) } returns
            listOf(vultLowercase)
        coEvery { oneInchApi.getTokensByContracts(Chain.Ethereum, listOf(vultLowercase)) } returns
            mapOf(
                vultLowercase to
                    oneInchToken(
                        address = vultLowercase,
                        symbol = "VULT",
                        logoURI = "https://tokens.1inch.io/$vultLowercase.png",
                        providers = listOf("CoinGecko"),
                    )
            )

        val coins = finder.find(Chain.Ethereum, ADDRESS)

        assertEquals(1, coins.count { it.ticker == "VULT" })
        coVerify(exactly = 0) { evmApi.getERC20Balance(any(), any()) }
    }

    @Test
    fun `find still tops up VULT on Ethereum when the 1inch metadata call fails`() = runTest {
        val unknownContract = "0xdeadbeef00000000000000000000000000000abc"
        coEvery { oneInchApi.getContractsWithBalance(Chain.Ethereum, ADDRESS) } returns
            listOf(unknownContract)
        coEvery { oneInchApi.getTokensByContracts(Chain.Ethereum, listOf(unknownContract)) } throws
            NetworkException(500, "boom")
        every { evmApiFactory.createEvmApi(Chain.Ethereum) } returns evmApi
        coEvery { evmApi.getERC20Balance(ADDRESS, VULT_ETHEREUM_CONTRACT_LOWER) } returns
            BigInteger.ONE

        val coins = finder.find(Chain.Ethereum, ADDRESS)

        assertEquals(listOf("VULT"), coins.map { it.ticker })
    }

    @Test
    fun `find returns empty when the 1inch balance call fails on a non-Ethereum chain`() = runTest {
        coEvery { oneInchApi.getContractsWithBalance(Chain.BscChain, ADDRESS) } throws
            NetworkException(500, "boom")

        val coins = finder.find(Chain.BscChain, ADDRESS)

        assertTrue(coins.isEmpty())
        coVerify(exactly = 0) { oneInchApi.getTokensByContracts(any(), any()) }
    }

    @Test
    fun `find on Ethereum still tops up VULT when the 1inch balance call fails`() = runTest {
        coEvery { oneInchApi.getContractsWithBalance(Chain.Ethereum, ADDRESS) } throws
            NetworkException(500, "boom")
        every { evmApiFactory.createEvmApi(Chain.Ethereum) } returns evmApi
        coEvery { evmApi.getERC20Balance(ADDRESS, VULT_ETHEREUM_CONTRACT_LOWER) } returns
            BigInteger.valueOf(42)

        val coins = finder.find(Chain.Ethereum, ADDRESS)

        assertEquals(listOf("VULT"), coins.map { it.ticker })
        coVerify(exactly = 0) { oneInchApi.getTokensByContracts(any(), any()) }
    }

    @Test
    fun `find on an unsupported EVM chain iterates curated balances and skips 1inch`() = runTest {
        // Pick whichever curated coin Blast happens to have first — keeps the test stable as
        // tokens are added or removed.
        val curated =
            requireNotNull(Coins.coins[Chain.Blast]).first {
                !it.isNativeToken && it.contractAddress.isNotEmpty()
            }
        every { evmApiFactory.createEvmApi(Chain.Blast) } returns evmApi
        coEvery { evmApi.getERC20Balance(ADDRESS, any()) } returns BigInteger.ZERO
        coEvery { evmApi.getERC20Balance(ADDRESS, curated.contractAddress) } returns
            BigInteger.valueOf(7)

        val coins = finder.find(Chain.Blast, ADDRESS)

        assertEquals(listOf(curated), coins)
        coVerify(exactly = 0) { oneInchApi.getContractsWithBalance(any(), any()) }
        coVerify(exactly = 0) { oneInchApi.getTokensByContracts(any(), any()) }
    }

    private fun oneInchToken(
        address: String,
        symbol: String,
        logoURI: String?,
        providers: List<String>?,
    ): OneInchTokenJson =
        OneInchTokenJson(
            address = address,
            symbol = symbol,
            decimals = 18,
            name = symbol,
            logoURI = logoURI,
            providers = providers,
        )

    private companion object {
        const val ADDRESS = "0xd8da6bf26964af9d7eed9e03e53415d37aa96045"

        const val NATIVE_SENTINEL = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
        // Synthetic non-curated contracts so the finder exercises the build-from-1inch path
        // rather than swapping in a curated entry from `Coins.coins`.
        const val GOOD_CONTRACT = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
        const val EMPTY_LOGO_CONTRACT = "0x1111111111111111111111111111111111111111"
        const val NON_COINGECKO_CONTRACT = "0x2222222222222222222222222222222222222222"

        const val USDC_ETHEREUM_CONTRACT = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
        const val VULT_ETHEREUM_CONTRACT_LOWER = "0xb788144df611029c60b859df47e79b7726c4deba"
    }
}
