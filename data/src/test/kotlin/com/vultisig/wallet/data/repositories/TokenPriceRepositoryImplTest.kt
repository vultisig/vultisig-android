package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.LiQuestApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.MayaNodePool
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.ThorChainPoolJson
import com.vultisig.wallet.data.api.models.thorchain.VaultRedemptionResponseJson
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.settings.AppCurrency
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class TokenPriceRepositoryImplTest {

    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var coinGeckoApi: CoinGeckoApi
    private lateinit var liQuestApi: LiQuestApi
    private lateinit var thorApi: ThorChainApi
    private lateinit var mayaApi: MayaChainApi
    private lateinit var tokenPriceDao: TokenPriceDao
    private lateinit var repository: TokenPriceRepositoryImpl

    @BeforeEach
    fun setUp() {
        appCurrencyRepository = mockk()
        coinGeckoApi = mockk()
        liQuestApi = mockk()
        thorApi = mockk()
        mayaApi = mockk()
        tokenPriceDao = mockk(relaxed = true)

        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        // No thor/maya tokens in these tests, so pool fetches are not exercised.

        repository =
            TokenPriceRepositoryImpl(
                appCurrencyRepository = appCurrencyRepository,
                coinGeckoApi = coinGeckoApi,
                liQuestApi = liQuestApi,
                thorApi = thorApi,
                mayaApi = mayaApi,
                tokenPriceDao = tokenPriceDao,
            )
    }

    private val ezEth =
        Coin(
            chain = Chain.Base,
            ticker = "ezETH",
            logo = "ezeth",
            address = "",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ezETH",
            contractAddress = "0x2416092f143378750bb29b79eD961ab195CcEea5",
            isNativeToken = false,
        )

    @Test
    fun `falls back to contract-address lookup when priceProviderID returns no price`() = runTest {
        // CoinGecko does not recognize the "ezETH" id, so the provider-id lookup is empty.
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        // The contract-address lookup returns a valid price.
        coEvery { coinGeckoApi.getContractsPrice(eq(Chain.Base), any(), any()) } returns
            mapOf(ezEth.contractAddress to mapOf("usd" to BigDecimal("3500.0")))

        repository.refresh(listOf(ezEth))

        // The contract-address fallback must have been attempted for ezETH's contract.
        coVerify {
            coinGeckoApi.getContractsPrice(
                Chain.Base,
                match { it.contains(ezEth.contractAddress) },
                any(),
            )
        }

        val price = repository.getPrice(ezEth, AppCurrency.USD).first()
        assertEquals(BigDecimal("3500.0"), price)
    }

    @Test
    fun `getPrice falls back to the persisted price before any refresh`() = runTest {
        // Cold start: the in-memory map is empty, but Room holds a last-known price. getPrice must
        // serve it instead of $0 so decoupled balance fetches don't flash cached fiat to zero.
        coEvery { tokenPriceDao.getTokenPrice(ezEth.id, "usd") } returns "3500.0"

        val price = repository.getPrice(ezEth, AppCurrency.USD).first()

        assertEquals(BigDecimal("3500.0"), price)
    }

    @Test
    fun `getPrice prefers the refreshed in-memory price over the persisted price`() = runTest {
        // Room holds a stale price, but a refresh populated the in-memory map with a newer one.
        coEvery { tokenPriceDao.getTokenPrice(ezEth.id, "usd") } returns "3000.0"
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns
            mapOf("ezETH" to mapOf("usd" to BigDecimal("3400.0")))

        repository.refresh(listOf(ezEth))
        val price = repository.getPrice(ezEth, AppCurrency.USD).first()

        assertEquals(BigDecimal("3400.0"), price)
    }

    private val discoveredSuiToken =
        Coin(
            chain = Chain.Sui,
            ticker = "HASUI",
            logo = "",
            address = "",
            decimal = 9,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress =
                "0xbde4ba4c2e274a60ce15c1cfff9e5c42e41654ac8b6d906a57efa4bd3c29f47d::hasui::HASUI",
            isNativeToken = false,
        )

    @Test
    fun `auto-discovered Sui token with no priceProviderID is priced via its contract address`() =
        runTest {
            coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
            coEvery { coinGeckoApi.getContractsPrice(eq(Chain.Sui), any(), any()) } returns
                mapOf(discoveredSuiToken.contractAddress to mapOf("usd" to BigDecimal("0.42")))

            repository.refresh(listOf(discoveredSuiToken))

            coVerify {
                coinGeckoApi.getContractsPrice(
                    Chain.Sui,
                    match { it.contains(discoveredSuiToken.contractAddress) },
                    any(),
                )
            }
            val price = repository.getPrice(discoveredSuiToken, AppCurrency.USD).first()
            assertEquals(BigDecimal("0.42"), price)
        }

    @Test
    fun `does not call contract-address lookup when priceProviderID returns a price`() = runTest {
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns
            mapOf("ezETH" to mapOf("usd" to BigDecimal("3400.0")))

        repository.refresh(listOf(ezEth))

        coVerify(exactly = 0) { coinGeckoApi.getContractsPrice(any(), any(), any()) }

        val price = repository.getPrice(ezEth, AppCurrency.USD).first()
        assertEquals(BigDecimal("3400.0"), price)
    }

    private val bRune = Coins.ThorChain.bRUNE
    private val ybRune = Coins.ThorChain.ybRUNE

    // Silences the CoinGecko/LiFi contract-address fallback that any THORChain bank denom (empty
    // priceProviderID) is fanned through, so only fetchThorContractPrices sets bRUNE/ybRUNE prices.
    private fun stubEmptyContractFallback() {
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { liQuestApi.getLifiContractPriceUsd(any(), any()) } throws
            RuntimeException("no lifi")
        coEvery { thorApi.getPools() } returns emptyList()
    }

    // BigDecimal.equals is scale-sensitive (5.0 != 5.00000000); compare by value instead.
    private fun assertPriceEquals(expected: String, actual: BigDecimal) =
        assertEquals(
            0,
            BigDecimal(expected).compareTo(actual),
            "expected $expected but was $actual",
        )

    private fun redemption(bondSize: String, bondShares: String): VaultRedemptionResponseJson =
        Json.decodeFromString(
            """{"data":{"liquid_bond_size":"$bondSize","liquid_bond_shares":"$bondShares"}}"""
        )

    @Test
    fun `bRUNE tracks RUNE at parity and ybRUNE is NAV times RUNE`() = runTest {
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        // RUNE price is served from the cache, keyed by Coin.id.
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns "5.0"
        // ybRUNE NAV = 200 / 100 = 2.
        coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
            redemption(bondSize = "200", bondShares = "100")

        repository.refresh(listOf(bRune, ybRune))

        assertPriceEquals("5", repository.getPrice(bRune, AppCurrency.USD).first())
        assertPriceEquals("10", repository.getPrice(ybRune, AppCurrency.USD).first())
    }

    @Test
    fun `runePriceUsd reads the cache by coin id, not price provider id`() = runTest {
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns "5.0"

        repository.refresh(listOf(bRune))

        assertPriceEquals("5", repository.getPrice(bRune, AppCurrency.USD).first())
        // A correct cache hit means no live RUNE fetch was needed.
        coVerify(exactly = 0) {
            coinGeckoApi.getCryptoPrices(match { it.contains("thorchain") }, any())
        }
    }

    @Test
    fun `runePriceUsd live fallback fetches RUNE in USD and applies FX only once`() = runTest {
        // Non-USD app currency, no cached RUNE price: the live fallback must fetch RUNE in USD and
        // the caller applies the tether (currency-per-USD) rate exactly once.
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.EUR)
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns null
        coEvery {
            coinGeckoApi.getCryptoPrices(
                match { it.contains("thorchain") },
                match { it.contains("usd") },
            )
        } returns mapOf("thorchain" to mapOf("usd" to BigDecimal("5.0")))
        coEvery { coinGeckoApi.getCryptoPrices(match { it.contains("tether") }, any()) } returns
            mapOf("tether" to mapOf("eur" to BigDecimal("0.9")))

        repository.refresh(listOf(bRune))

        // 5 USD × 0.9 EUR/USD = 4.5 EUR. A double-applied FX would yield 4.05.
        assertPriceEquals("4.5", repository.getPrice(bRune, AppCurrency.EUR).first())
    }

    @Test
    fun `refreshing bRUNE and ybRUNE together fetches the live RUNE price at most once`() =
        runTest {
            // Cold RUNE cache + both denoms in one refresh: bRUNE and ybRUNE both price off
            // RUNE-in-USD,
            // so the live CoinGecko RUNE fetch must be shared, not fired once per token.
            stubEmptyContractFallback()
            coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns null
            coEvery {
                coinGeckoApi.getCryptoPrices(
                    match { it.contains("thorchain") },
                    match { it.contains("usd") },
                )
            } returns mapOf("thorchain" to mapOf("usd" to BigDecimal("5.0")))
            // Any other id (e.g. the contract-fallback fan-out) resolves to nothing.
            coEvery {
                coinGeckoApi.getCryptoPrices(match { !it.contains("thorchain") }, any())
            } returns emptyMap()
            // ybRUNE NAV = 200 / 100 = 2.
            coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
                redemption(bondSize = "200", bondShares = "100")

            repository.refresh(listOf(bRune, ybRune))

            assertPriceEquals("5", repository.getPrice(bRune, AppCurrency.USD).first())
            assertPriceEquals("10", repository.getPrice(ybRune, AppCurrency.USD).first())
            coVerify(exactly = 1) {
                coinGeckoApi.getCryptoPrices(match { it.contains("thorchain") }, any())
            }
        }

    @Test
    fun `does not persist a zero ybRUNE price when the NAV field is malformed`() = runTest {
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns "5.0"
        // Unparseable bond size with positive shares → NAV 0 → price 0, which must not overwrite
        // the
        // last-known price.
        coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
            redemption(bondSize = "n/a", bondShares = "100")

        repository.refresh(listOf(ybRune))

        coVerify(exactly = 0) { tokenPriceDao.insertTokenPrice(match { it.tokenId == ybRune.id }) }
    }

    @Test
    fun `does not persist a nonzero parity price when the NAV status is empty`() = runTest {
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUNE.id, "usd") } returns "5.0"
        // A malformed/empty 2xx leaves both bond fields at their "" default. NAV must resolve to 0
        // (dropped), not 1 (RUNE parity), so it can't overwrite the last-known ybRUNE price.
        coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
            redemption(bondSize = "", bondShares = "")

        repository.refresh(listOf(ybRune))

        coVerify(exactly = 0) { tokenPriceDao.insertTokenPrice(match { it.tokenId == ybRune.id }) }
    }

    private val sTcy = Coins.ThorChain.sTCY

    @Test
    fun `sTCY prices off the TCY cache keyed by coin id, not the tcy price provider`() = runTest {
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        // TCY-in-USD served from the cache, keyed by Coin.id ("TCY-THORChain") — a lookup by the
        // "tcy" priceProviderID never hits, so this pins the coin-id path.
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.TCY.id, "usd") } returns "2.0"
        // sTCY NAV = 200 / 100 = 2, so sTCY = 2 (TCY) × 2 (NAV) = 4.
        coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
            redemption(bondSize = "200", bondShares = "100")

        repository.refresh(listOf(sTcy))

        assertPriceEquals("4", repository.getPrice(sTcy, AppCurrency.USD).first())
    }

    @Test
    fun `sTCY live TCY fallback fetches TCY in USD and applies FX only once`() = runTest {
        // Non-USD app currency, no cached TCY price: the live fallback must fetch TCY in USD and
        // the
        // caller applies the tether (currency-per-USD) rate exactly once. The old provider-id path
        // returned the app currency and then multiplied by tether, so a EUR/JPY user saw sTCY
        // roughly FX-times too high.
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.EUR)
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.TCY.id, "usd") } returns null
        coEvery {
            coinGeckoApi.getCryptoPrices(match { it.contains("tcy") }, match { it.contains("usd") })
        } returns mapOf("tcy" to mapOf("usd" to BigDecimal("2.0")))
        coEvery { coinGeckoApi.getCryptoPrices(match { it.contains("tether") }, any()) } returns
            mapOf("tether" to mapOf("eur" to BigDecimal("0.9")))
        // sTCY NAV = 200 / 100 = 2.
        coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
            redemption(bondSize = "200", bondShares = "100")

        repository.refresh(listOf(sTcy))

        // 2 USD (TCY) × 2 (NAV) × 0.9 EUR/USD = 3.6 EUR. A double-applied FX would yield 3.24.
        assertPriceEquals("3.6", repository.getPrice(sTcy, AppCurrency.EUR).first())
    }

    private fun pool(asset: String, torPrice: String) =
        ThorChainPoolJson(asset = asset, assetTorPrice = BigInteger(torPrice), status = "Available")

    @Test
    fun `a THORChain contract lookup prices the staking receipt off NAV instead of returning zero`() =
        runTest {
            // The contract route is the last resort for a DeFi position the vault doesn't hold.
            // CoinGecko has no THORChain asset platform and LI.FI has no THORChain id, so this used
            // to be dead code that answered zero for every `x/…` denom.
            coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
            coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.TCY.id, "usd") } returns "2.0"
            // sTCY NAV = 200 / 100 = 2, so sTCY = 2 (TCY) × 2 (NAV) = 4.
            coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
                redemption(bondSize = "200", bondShares = "100")

            val price =
                repository.getPriceByContactAddress(Chain.ThorChain.id, sTcy.contractAddress)

            assertPriceEquals("4", price)
        }

    @Test
    fun `a THORChain contract with no receipt route falls back to its pool price`() = runTest {
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        // Pool TOR prices carry 8 decimals: 1_50000000 → $1.50.
        coEvery { thorApi.getPools() } returns listOf(pool("THOR.RUJI", "150000000"))

        val price =
            repository.getPriceByContactAddress(
                Chain.ThorChain.id,
                Coins.ThorChain.RUJI.contractAddress,
            )

        assertPriceEquals("1.5", price)
    }

    @Test
    fun `a resolved contract price is cached under the coin id readers query by`() = runTest {
        // The row used to be keyed by the raw contract address, which nothing ever reads back, so
        // the write was dead and every reload repeated the live lookup.
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.TCY.id, "usd") } returns "2.0"
        coEvery { thorApi.getThorchainTokenPriceByContract(any()) } returns
            redemption(bondSize = "200", bondShares = "100")

        repository.getPriceByContactAddress(Chain.ThorChain.id, sTcy.contractAddress)

        coVerify { tokenPriceDao.insertTokenPrice(match { it.tokenId == sTcy.id }) }
        coVerify(exactly = 0) {
            tokenPriceDao.insertTokenPrice(match { it.tokenId == sTcy.contractAddress })
        }
    }

    @Test
    fun `a non-USD price is left unresolved when the FX rate cannot be fetched`() = runTest {
        // tetherPriceFor answers a CoinGecko miss with ZERO. Multiplying blind turned a good USD
        // price into a $0.00 indistinguishable from "no price", and only ever for non-USD users.
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.EUR)
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { thorApi.getPools() } returns listOf(pool("THOR.RUJI", "150000000"))

        val price =
            repository.getPriceByContactAddress(
                Chain.ThorChain.id,
                Coins.ThorChain.RUJI.contractAddress,
            )

        assertPriceEquals("0", price)
        coVerify(exactly = 0) { tokenPriceDao.insertTokenPrice(any()) }
    }

    @Test
    fun `a contract nothing can price is never written to the cache as zero`() = runTest {
        // The old path mapped an unresolved LI.FI price to ZERO, which made the result look like a
        // real quote: it was returned as $0.00 *and* persisted, and on a chain with no working
        // contract source that poisoned row was the only price the token would ever have.
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { thorApi.getPools() } returns emptyList()

        val price = repository.getPriceByContactAddress(Chain.ThorChain.id, "x/unknown-denom")

        assertPriceEquals("0", price)
        coVerify(exactly = 0) { tokenPriceDao.insertTokenPrice(any()) }
    }

    @Test
    fun `a THORChain pool price is not cached when the FX rate cannot be fetched`() = runTest {
        // The batch route had the same hole the single-contract route was fixed for: every pool
        // price is multiplied by the USDT rate, and a CoinGecko miss answers ZERO — so a perfectly
        // good pool quote landed in Room as a confident $0.00, and only for non-USD users.
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.EUR)
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { thorApi.getPools() } returns listOf(pool("THOR.RUJI", "150000000"))

        repository.refresh(listOf(Coins.ThorChain.RUJI))

        coVerify(exactly = 0) {
            tokenPriceDao.insertTokenPrice(match { it.tokenId == Coins.ThorChain.RUJI.id })
        }
    }

    @Test
    fun `the auto-compounding receipt is priced off RUJI's row, not one of its own`() = runTest {
        // Both RUJI legs are reported in RUJI, and the pool price is the one every other RUJI
        // reading in the app comes from. The receipt borrows RUJI's `rujira` provider id, so a row
        // of its own kept CoinGecko's quote while RUJI's row was overwritten by the pool moments
        // later — the same RUJI at two prices, one on the DeFi tab and one on the position card.
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns
            mapOf("rujira" to mapOf("usd" to BigDecimal("0.171154")))
        coEvery { thorApi.getPools() } returns listOf(pool("THOR.RUJI", "17177730"))

        repository.refresh(listOf(Coins.ThorChain.sRUJI))

        assertPriceEquals(
            "0.17177730",
            repository.getPrice(Coins.ThorChain.sRUJI, AppCurrency.USD).first(),
        )
        coVerify(exactly = 0) {
            tokenPriceDao.insertTokenPrice(match { it.tokenId == Coins.ThorChain.sRUJI.id })
        }
    }

    @Test
    fun `a cached price read for the receipt is served from RUJI's row`() = runTest {
        // A row written before this change is still in Room, and the cached DeFi emission reads
        // one on every cold start — it must not be preferred over the row the position is valued
        // from.
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.sRUJI.id, "usd") } returns "0.171154"
        coEvery { tokenPriceDao.getTokenPrice(Coins.ThorChain.RUJI.id, "usd") } returns "0.17177730"

        val price = repository.getCachedPrice(Coins.ThorChain.sRUJI.id, AppCurrency.USD)

        assertPriceEquals("0.17177730", price!!)
    }

    @Test
    fun `sTCY does not inherit TCY's price through their shared provider id`() = runTest {
        // sTCY carries TCY's `tcy` priceProviderID, so the provider batch would cache raw TCY under
        // sTCY's row. fetchThorContractPrices corrects it to NAV x TCY afterwards — but when that
        // correction fails, the uncorrected row is what survives, pinning sTCY at bare TCY parity
        // instead of leaving its last-known good price alone.
        stubEmptyContractFallback()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns
            mapOf("tcy" to mapOf("usd" to BigDecimal("1.0")))
        coEvery { thorApi.getThorchainTokenPriceByContract(any()) } throws RuntimeException("NAV")

        repository.refresh(listOf(Coins.ThorChain.sTCY))

        coVerify(exactly = 0) {
            tokenPriceDao.insertTokenPrice(match { it.tokenId == Coins.ThorChain.sTCY.id })
        }
    }

    @Test
    fun `a Maya pool price is computed in the currency it is persisted under`() = runTest {
        // The currency was captured once for the label and reread live for the computation, so a
        // switch mid-refresh priced the token in the new currency and filed it under the old one.
        // Emit USD first (what refresh captures) and EUR after, so a reread is visible.
        var reads = 0
        coEvery { appCurrencyRepository.currency } answers
            {
                flowOf(if (reads++ == 0) AppCurrency.USD else AppCurrency.EUR)
            }
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns
            mapOf("cacao" to mapOf("usd" to BigDecimal("0.5")))
        coEvery { thorApi.getPools() } returns emptyList()
        // Cold cache, so CACAO is fetched live. The relaxed dao answers "" rather than null, which
        // BigDecimal would reject.
        coEvery { tokenPriceDao.getTokenPrice(Coins.MayaChain.CACAO.id, any()) } returns null
        coEvery { mayaApi.getPool(any()) } returns
            MayaNodePool(
                asset = "MAYA.MAYA",
                status = "Available",
                balanceCacao = "1000000000000",
                balanceAsset = "1000000",
            )

        repository.refresh(listOf(Coins.MayaChain.MAYA))

        // CACAO must be priced in the currency the row is labeled with, never the reread one.
        coVerify(exactly = 0) { coinGeckoApi.getCryptoPrices(any(), listOf("eur")) }
        coVerify {
            tokenPriceDao.insertTokenPrice(
                match { it.tokenId == Coins.MayaChain.MAYA.id && it.currency == "usd" }
            )
        }
    }

    @Test
    fun `a pool quoting zero is a miss, not a valuation worth caching`() = runTest {
        // Distinct from an absent pool: the pool exists and answers, it just answers 0. Only the
        // null case used to be rejected, so a zero survived to savePrices — which filters empty
        // maps, not zero prices — and permanently blocked the token's later real price.
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { thorApi.getPools() } returns listOf(pool("THOR.RUJI", "0"))

        val price =
            repository.getPriceByContactAddress(
                Chain.ThorChain.id,
                Coins.ThorChain.RUJI.contractAddress,
            )

        assertPriceEquals("0", price)
        coVerify(exactly = 0) { tokenPriceDao.insertTokenPrice(any()) }
    }

    @Test
    fun `a non-EVM contract lookup never reaches LI_FI`() = runTest {
        // LI.FI only indexes EVM chains, so a THORChain contract could only ever miss there.
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { thorApi.getPools() } returns emptyList()

        repository.getPriceByContactAddress(Chain.ThorChain.id, "x/unknown-denom")

        coVerify(exactly = 0) { liQuestApi.getLifiContractPriceUsd(any(), any()) }
    }

    @Test
    fun `an EVM contract LI_FI cannot price is dropped rather than cached as zero`() = runTest {
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { liQuestApi.getLifiContractPriceUsd(any(), any()) } throws
            RuntimeException("no lifi")

        val price = repository.getPriceByContactAddress(Chain.Base.id, ezEth.contractAddress)

        assertPriceEquals("0", price)
        coVerify(exactly = 0) { tokenPriceDao.insertTokenPrice(any()) }
    }

    @Test
    fun `the NAV batch gives up on a missing FX rate before paying for a NAV call`() = runTest {
        // Every price in the batch is multiplied by the FX rate, and a CoinGecko miss arrives as
        // ZERO, so without it the batch can only produce zeros the filter discards — after a live
        // NAV call per token. The guard the sibling call sites already had was missing here.
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.EUR)
        coEvery { coinGeckoApi.getCryptoPrices(any(), any()) } returns emptyMap()
        coEvery { thorApi.getPools() } returns emptyList()

        repository.refresh(listOf(sTcy))

        coVerify(exactly = 0) { thorApi.getThorchainTokenPriceByContract(any()) }
        coVerify(exactly = 0) { tokenPriceDao.insertTokenPrice(any()) }
    }

    @Test
    fun `a contract lookup quotes in the currency the caller captured`() = runTest {
        // The app currency can change while a lookup is in flight, and the caller labels both the
        // number it renders and the row this writes with the currency it captured. Rereading the
        // app currency here filed a price resolved in the newer currency under the older label.
        coEvery { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { coinGeckoApi.getContractsPrice(any(), any(), any()) } returns emptyMap()
        coEvery {
            coinGeckoApi.getCryptoPrices(
                match { it.contains("tether") },
                match { it.contains("eur") },
            )
        } returns mapOf("tether" to mapOf("eur" to BigDecimal("0.5")))
        coEvery { thorApi.getPools() } returns listOf(pool("THOR.RUJI", "150000000"))

        val price =
            repository.getPriceByContactAddress(
                chainId = Chain.ThorChain.id,
                contractAddress = Coins.ThorChain.RUJI.contractAddress,
                appCurrency = AppCurrency.EUR,
            )

        // $1.50 × 0.5 EUR/USD, cached under "eur" rather than the app's "usd".
        assertPriceEquals("0.75", price)
        coVerify { tokenPriceDao.insertTokenPrice(match { it.currency == "eur" }) }
    }

    @Test
    fun `VaultRedemption response maps the liquid bond JSON fields`() = runTest {
        // Pins the @SerialName mapping for the {"status":{}} contract query: a renamed field would
        // otherwise deserialize to the empty-string default and silently price ybRUNE at parity.
        val response = redemption(bondSize = "123", bondShares = "45")
        assertEquals("123", response.data.liquidBondSize)
        assertEquals("45", response.data.liquidBondShares)
    }
}
