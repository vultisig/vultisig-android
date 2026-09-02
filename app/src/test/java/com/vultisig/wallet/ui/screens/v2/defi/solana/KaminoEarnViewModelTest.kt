package com.vultisig.wallet.ui.screens.v2.defi.solana

import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoPnlJson
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.api.KaminoVaultMetricsJson
import com.vultisig.wallet.data.api.KaminoVaultStateJson
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoCurator
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRiskTier
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.KaminoVaultSelectionRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.defi.DeFiPositionsSnapshotCache
import com.vultisig.wallet.ui.models.defi.clearForTest
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.DefiFiatTotal
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class KaminoEarnViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var kaminoApi: KaminoApi
    private lateinit var selectionRepository: KaminoVaultSelectionRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var chainAccountAddressRepository: ChainAccountAddressRepository
    private lateinit var tokenPriceRepository: TokenPriceRepository
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    // The real cache, not a mock: these tests assert the round trip a nav pop and a re-entry make.
    private lateinit var snapshotCache: DeFiPositionsSnapshotCache

    private val defaultLocale = Locale.getDefault()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Amount rendering follows the user's locale, so the expected strings below only hold once
        // the locale the test runs under is pinned.
        Locale.setDefault(Locale.US)
        navigator = mockk(relaxed = true)
        kaminoApi = mockk(relaxed = true)
        selectionRepository = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        snapshotCache = DeFiPositionsSnapshotCache()

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        coEvery { chainAccountAddressRepository.getAddress(Chain.Solana, VAULT) } returns
            (WALLET_ADDRESS to "pubkey")
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { appCurrencyRepository.getCurrencyFormat(any()) } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns BigDecimal.ONE
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(defaultLocale)
    }

    private fun viewModel() =
        KaminoEarnViewModel(
            navigator = navigator,
            kaminoApi = kaminoApi,
            selectionRepository = selectionRepository,
            vaultRepository = vaultRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            tokenPriceRepository = tokenPriceRepository,
            appCurrencyRepository = appCurrencyRepository,
            balanceVisibilityRepository = balanceVisibilityRepository,
            snapshotCache = snapshotCache,
            ioDispatcher = testDispatcher,
        )

    @Test
    fun `a re-entry paints the cards the tab was last showing`() = runTest {
        // Earn is the tab the Solana screen opens on, so this cold start is the wait every
        // re-entry paid: no cards, no total, until the vault fan-out came back.
        snapshotCache.write(VAULT_ID, LAST_RENDERED)
        // Suspend the selection read so the only state on screen is the restored one.
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flow { awaitCancellation() }

        val state = viewModel().also { it.setData(VAULT_ID) }.state.value

        state.rows.map { it.vaultAddress } shouldBe listOf(STEAKHOUSE.address)
        state.rows.single().depositedDisplay shouldBe "100 USDC"
        state.totalValue.shouldNotBeNull().value shouldBe BigDecimal("100")
        state.hasEnabledVaults shouldBe true
    }

    @Test
    fun `a restored total survives the next load rather than being dropped on sight`() = runTest {
        // The total answers one question — this selection, in this currency — and the coverage it
        // was summed over travels with it. Without that the load drops it the moment it starts,
        // which is the flash the snapshot exists to remove.
        snapshotCache.write(VAULT_ID, LAST_RENDERED)
        // The selection the restored total was summed over, so the load gets far enough to run its
        // drop check for real; the fan-out behind it is held, so nothing can rebuild the figures
        // and the total on screen is still the restored one.
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } coAnswers { awaitCancellation() }

        val state = viewModel().also { it.setData(VAULT_ID) }.state.value

        state.totalValue.shouldNotBeNull().value shouldBe BigDecimal("100")
        state.rows.single().depositedDisplay shouldBe "100 USDC"
    }

    @Test
    fun `a re-entry does not reopen the vault picker`() = runTest {
        snapshotCache.write(
            VAULT_ID,
            LAST_RENDERED.copy(
                model =
                    LAST_RENDERED.model.copy(
                        isShowingPicker = true,
                        pendingSelection = setOf(STEAKHOUSE.address),
                    )
            ),
        )
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flow { awaitCancellation() }

        val state = viewModel().also { it.setData(VAULT_ID) }.state.value

        state.isShowingPicker shouldBe false
        state.pendingSelection shouldBe emptySet()
    }

    @Test
    fun `hands the rendered cards to the cache when the screen is popped`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns flowOf(emptySet())
        val vm = viewModel().also { it.setData(VAULT_ID) }
        val rendered = vm.state.value

        vm.clearForTest()

        snapshotCache.read(VAULT_ID, KaminoEarnSnapshot::class).shouldNotBeNull().model shouldBe
            rendered
    }

    private fun stubVault(name: String) =
        KaminoVaultStateJson(
            address = STEAKHOUSE.address,
            state =
                KaminoVaultStateJson.State(
                    name = name,
                    tokenMint = STEAKHOUSE.tokenMint,
                    tokenDecimals = 6,
                    sharesMint = STEAKHOUSE.sharesMint,
                    sharesDecimals = 6,
                ),
        )

    @Test
    fun `nothing enabled leaves the segment empty rather than showing every vault`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns flowOf(emptySet())

        val state = viewModel().apply { setData(VAULT_ID) }.state.value

        state.hasEnabledVaults shouldBe false
        state.rows.isEmpty() shouldBe true
        state.isLoading shouldBe false
    }

    @Test
    fun `nothing enabled hands the chain header a resolved zero, not an unknown`() = runTest {
        // The header adds this to native staking. Null there would read as "not loaded" and blank
        // the whole banner for everyone who never turned Earn on.
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns flowOf(emptySet())

        val state = viewModel().apply { setData(VAULT_ID) }.state.value

        BigDecimal.ZERO.compareTo(state.totalValue?.value) shouldBe 0
        state.totalValue?.currency shouldBe AppCurrency.USD
    }

    @Test
    fun `a failed load leaves the total unknown rather than reporting zero`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { vaultRepository.get(VAULT_ID) } returns null

        val state = viewModel().apply { setData(VAULT_ID) }.state.value

        state.loadFailed shouldBe true
        state.totalValue.shouldBeNull()
    }

    @Test
    fun `an unpriced vault leaves the total unknown rather than counting it as zero`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address, ALLEZ.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns
            listOf(
                KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100"),
                KaminoUserPositionJson(vaultAddress = ALLEZ.address, totalShares = "100"),
            )
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Vault")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")
        // The SOL vault holds a real deposit that no price landed for this round.
        coEvery { tokenPriceRepository.getCachedPrice(Coins.Solana.SOL.id, any()) } returns null
        coEvery {
            tokenPriceRepository.getPriceByContactAddress(
                chainId = Chain.Solana.id,
                contractAddress = Coins.Solana.SOL.contractAddress,
                appCurrency = AppCurrency.USD,
            )
        } throws RuntimeException("503")

        val state = viewModel().apply { setData(VAULT_ID) }.state.value

        // The USDC half alone is not what the wallet holds on Kamino, and the chain header adds
        // this figure to native staking — reporting it short would understate the whole chain.
        state.rows.size shouldBe 2
        state.totalValue.shouldBeNull()
    }

    @Test
    fun `a total from an earlier selection is dropped rather than reused for a new one`() =
        runTest {
            val selection = MutableStateFlow(setOf(STEAKHOUSE.address, ALLEZ.address))
            coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns selection
            coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns
                listOf(
                    KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100"),
                    KaminoUserPositionJson(vaultAddress = ALLEZ.address, totalShares = "100"),
                )
            coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Vault")
            coEvery { kaminoApi.getVaultMetrics(any()) } returns
                KaminoVaultMetricsJson(tokensPerShare = "1.0")

            val vm = viewModel().apply { setData(VAULT_ID) }
            BigDecimal("200").compareTo(vm.state.value.totalValue?.value) shouldBe 0

            // One vault is switched off, and the reload that follows never lands.
            coEvery { chainAccountAddressRepository.getAddress(Chain.Solana, VAULT) } throws
                RuntimeException("503")
            selection.value = setOf(STEAKHOUSE.address)

            // $200 covered both vaults; leaving it up for one would report a deselected position.
            vm.state.value.totalValue.shouldBeNull()
        }

    @Test
    fun `an enabled vault with no deposit still gets a card at zero`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns emptyList()
        coEvery { kaminoApi.getVaultState(STEAKHOUSE.address) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(apy30d = "0.039967764404019690278", tokensPerShare = "1.05")

        val state = viewModel().apply { setData(VAULT_ID) }.state.value

        state.hasEnabledVaults shouldBe true
        state.rows.size shouldBe 1
        val row = state.rows.single()
        row.name shouldBe "Steakhouse USDC"
        row.curator shouldBe KaminoCurator.STEAKHOUSE_FINANCIAL
        row.riskTier shouldBe KaminoRiskTier.CONSERVATIVE
        row.depositedDisplay shouldBe "0 USDC"
        row.apyDisplay shouldBe "4.00%"
        // Zero is not a gain: an untouched vault must not render green.
        row.pnlDirection shouldBe KaminoEarnRow.PnlDirection.FLAT
    }

    @Test
    fun `a funded position is valued from shares and tokensPerShare`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns
            listOf(
                KaminoUserPositionJson(
                    vaultAddress = STEAKHOUSE.address,
                    stakedShares = "1000",
                    unstakedShares = "0",
                    totalShares = "1000",
                )
            )
        coEvery { kaminoApi.getVaultState(STEAKHOUSE.address) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(
                apy30d = "0.039967764404019690278",
                tokensPerShare = "1.0544278224860290217",
                sharePrice = "1.0542216502138983284",
                tokenPrice = "0.99980447",
            )
        coEvery { kaminoApi.getPositionPnl(WALLET_ADDRESS, STEAKHOUSE.address) } returns
            KaminoPnlJson(totalPnl = KaminoPnlJson.Amounts(token = "54.427822", usd = "54.41"))

        val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

        row.depositedDisplay shouldBe "1,054.427822 USDC"
        row.pnlDisplay shouldBe "54.427822 USDC"
        row.pnlDirection shouldBe KaminoEarnRow.PnlDirection.UP
        row.depositedFiat.shouldNotBeNull()
    }

    @Test
    fun `a locale that writes decimals with a comma gets its own separators`() = runTest {
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns
            listOf(
                KaminoUserPositionJson(
                    vaultAddress = STEAKHOUSE.address,
                    stakedShares = "1000",
                    unstakedShares = "0",
                    totalShares = "1000",
                )
            )
        coEvery { kaminoApi.getVaultState(STEAKHOUSE.address) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(
                apy30d = "0.039967764404019690278",
                tokensPerShare = "1.0544278224860290217",
            )
        coEvery { kaminoApi.getPositionPnl(WALLET_ADDRESS, STEAKHOUSE.address) } returns
            KaminoPnlJson(totalPnl = KaminoPnlJson.Amounts(token = "54.427822"))

        val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

        row.depositedDisplay shouldBe "1\u00A0054,427822 USDC"
        row.pnlDisplay shouldBe "54,427822 USDC"
        row.apyDisplay shouldBe "4,00%"
    }

    @Test
    fun `a loss renders as a loss`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns emptyList()
        coEvery { kaminoApi.getVaultState(STEAKHOUSE.address) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")
        coEvery { kaminoApi.getPositionPnl(WALLET_ADDRESS, STEAKHOUSE.address) } returns
            KaminoPnlJson(totalPnl = KaminoPnlJson.Amounts(token = "-1.5"))

        val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

        row.pnlDirection shouldBe KaminoEarnRow.PnlDirection.DOWN
    }

    @Test
    fun `a failed metrics call leaves the card standing without an APY`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns emptyList()
        coEvery { kaminoApi.getVaultState(STEAKHOUSE.address) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } throws RuntimeException("503")

        val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

        // The vault is still one the user enabled; dropping the card would read as losing it.
        row.name shouldBe "Steakhouse USDC"
        row.apyDisplay.shouldBeNull()
    }

    @Test
    fun `a failed vault-state call falls back to the pinned name`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns emptyList()
        coEvery { kaminoApi.getVaultState(STEAKHOUSE.address) } throws RuntimeException("503")
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

        row.name shouldBe STEAKHOUSE.fallbackName
    }

    @Test
    fun `a vault the registry does not know is ignored`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf("2Z6C84pCc2ri8t39jvRCXnTGFQqUJf1mMpUMtpeFfhyB"))

        val state = viewModel().apply { setData(VAULT_ID) }.state.value

        state.rows.isEmpty() shouldBe true
    }

    @Test
    fun `positions across two vaults are summed into one total`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address, ALLEZ.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns
            listOf(
                KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100"),
                KaminoUserPositionJson(vaultAddress = ALLEZ.address, totalShares = "100"),
            )
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Vault")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val state = viewModel().apply { setData(VAULT_ID) }.state.value

        state.rows.size shouldBe 2
        // Priced at 1.0 each, 100 + 100 shares against a 1:1 share ratio. This is what the chain
        // header adds to native staking — the segment no longer shows a total of its own.
        BigDecimal("200").compareTo(state.totalValue?.value) shouldBe 0
    }

    @Test
    fun `hidden balances are surfaced to the view`() = runTest {
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns false
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns flowOf(emptySet())

        viewModel().apply { setData(VAULT_ID) }.state.value.isBalanceVisible shouldBe false
    }

    @Test
    fun `the picker seeds from what is currently enabled and saving persists it`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(any()) } returns emptyList()
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val vm = viewModel().apply { setData(VAULT_ID) }
        vm.openPicker()

        vm.state.value.isShowingPicker shouldBe true
        vm.state.value.pendingSelection shouldBe setOf(STEAKHOUSE.address)

        vm.onVaultToggled(ALLEZ.address, true)
        vm.savePicker()

        vm.state.value.isShowingPicker shouldBe false
        coVerify {
            selectionRepository.saveSelectedVaults(
                VAULT_ID,
                setOf(STEAKHOUSE.address, ALLEZ.address),
            )
        }
    }

    @Test
    fun `dismissing the picker leaves the saved selection untouched`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(any()) } returns emptyList()
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val vm = viewModel().apply { setData(VAULT_ID) }
        vm.openPicker()
        vm.onVaultToggled(STEAKHOUSE.address, false)
        vm.closePicker()

        vm.state.value.isShowingPicker shouldBe false
        coVerify(exactly = 0) { selectionRepository.saveSelectedVaults(any(), any()) }
    }

    @Test
    fun `saving an empty selection is how Earn gets turned off`() = runTest {
        // An empty set must reach the repository, not be treated as "no choice made".
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(any()) } returns emptyList()
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val vm = viewModel().apply { setData(VAULT_ID) }
        vm.openPicker()
        vm.onVaultToggled(STEAKHOUSE.address, false)
        vm.savePicker()

        coVerify { selectionRepository.saveSelectedVaults(VAULT_ID, emptySet()) }
    }

    @Test
    fun `the picker refuses to enable a vault outside the registry`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns flowOf(emptySet())

        val vm = viewModel().apply { setData(VAULT_ID) }
        vm.openPicker()
        vm.onVaultToggled("2Z6C84pCc2ri8t39jvRCXnTGFQqUJf1mMpUMtpeFfhyB", true)

        vm.state.value.pendingSelection.isEmpty() shouldBe true
    }

    @Test
    fun `an unread position keeps Withdraw available rather than hiding the way out`() = runTest {
        // A zero carries two different claims: "read, and holds nothing" and "not read yet". Only
        // the first may remove Withdraw — hiding it on an unread position strands a user who
        // deposited on another device or whose refresh failed straight after depositing.
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(any()) } throws RuntimeException("503")
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

        withClue("an unread position must not hide Withdraw") { row.hasPosition shouldBe true }
        // The figures stay off the card either way — the form reads the position itself.
        row.depositedFiat.shouldBeNull()
    }

    @Test
    fun `a position present but with an unparseable share count keeps Withdraw available`() =
        runTest {
            // The vault entry exists — this is not "the wallet holds nothing" — but its
            // `totalShares` field will not parse. That is a failed read of a real row, and must not
            // be folded into the same zero as a confirmed empty position.
            coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
                flowOf(setOf(STEAKHOUSE.address))
            coEvery { kaminoApi.getUserPositions(any()) } returns
                listOf(
                    KaminoUserPositionJson(
                        vaultAddress = STEAKHOUSE.address,
                        totalShares = "garbage",
                    )
                )
            coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
            coEvery { kaminoApi.getVaultMetrics(any()) } returns
                KaminoVaultMetricsJson(tokensPerShare = "1.0")

            val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

            withClue("an unreadable position must not hide Withdraw") {
                row.hasPosition shouldBe true
            }
            row.depositedFiat.shouldBeNull()
        }

    @Test
    fun `a total kept partial by one unresolved row does not silently drop to that row's share`() =
        runTest {
            coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
                flowOf(setOf(STEAKHOUSE.address, ALLEZ.address))
            coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns
                listOf(
                    KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100"),
                    KaminoUserPositionJson(vaultAddress = ALLEZ.address, totalShares = "100"),
                )
            coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Vault")
            // Steakhouse's rate fails this refresh; Allez's resolves.
            coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } throws RuntimeException("503")
            coEvery { kaminoApi.getVaultMetrics(ALLEZ.address) } returns
                KaminoVaultMetricsJson(tokensPerShare = "1.0")

            val state = viewModel().apply { setData(VAULT_ID) }.state.value

            val steakhouseRow = state.rows.single { it.vaultAddress == STEAKHOUSE.address }
            val allezRow = state.rows.single { it.vaultAddress == ALLEZ.address }
            steakhouseRow.fiatValue.shouldBeNull()
            allezRow.fiatValue.shouldNotBeNull()
            // No prior total exists yet, so a total short by the unresolved row must not be shown
            // as if it were confirmed — $100 would read as the whole position, not half of it.
            state.totalValue.shouldBeNull()
        }

    @Test
    fun `a total never blends a fresh row with another row's stale splice`() = runTest {
        // First refresh: both vaults price cleanly. Second refresh: Allez genuinely grows (a real,
        // freshly priced increase) while Steakhouse's metrics call merely fails — not a confirmed
        // zero, just unresolved this round. The row-level splice keeps Steakhouse's card showing
        // its
        // last known figure, but the total must not add that spliced value to Allez's new one: that
        // combination was never true of any single confirmed state, fresh and stale alike.
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address, ALLEZ.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returnsMany
            listOf(
                listOf(
                    KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100"),
                    KaminoUserPositionJson(vaultAddress = ALLEZ.address, totalShares = "100"),
                ),
                listOf(
                    KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100"),
                    KaminoUserPositionJson(vaultAddress = ALLEZ.address, totalShares = "300"),
                ),
            )
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Vault")
        coEvery { kaminoApi.getVaultMetrics(STEAKHOUSE.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0") andThenThrows
            RuntimeException("503")
        coEvery { kaminoApi.getVaultMetrics(ALLEZ.address) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val vm = viewModel().apply { setData(VAULT_ID) }
        val firstTotal = vm.state.value.totalValue
        firstTotal.shouldNotBeNull()

        vm.refresh()

        vm.state.value.totalValue shouldBe firstTotal
    }

    @Test
    fun `a confirmed full withdrawal with a failed metrics call still shows a real zero`() =
        runTest {
            // The entry is present with totalShares "0" — a confirmed real zero, not the "wallet
            // holds nothing at all" absent-entry case. `tokenAmount` must not wait on a rate read
            // to know that zero shares are worth zero: requiring `tokensPerShare` here would show
            // "Unavailable" on a real full withdrawal instead of the true balance.
            coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
                flowOf(setOf(STEAKHOUSE.address))
            coEvery { kaminoApi.getUserPositions(any()) } returns
                listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "0"))
            coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
            coEvery { kaminoApi.getVaultMetrics(any()) } throws RuntimeException("503")

            val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

            row.depositedDisplay shouldBe "0 USDC"
            BigDecimal.ZERO.compareTo(row.fiatValue.shouldNotBeNull()) shouldBe 0
            row.hasPosition shouldBe false
        }

    @Test
    fun `a full withdrawal does not leave the pre-withdrawal fiat value in the total`() = runTest {
        // First refresh: a real, priced deposit. Second refresh (after a full withdrawal):
        // totalShares confirms zero, but this same refresh's metrics call fails. The merge must
        // not splice the first refresh's nonzero fiatValue back in — the row is a confirmed zero,
        // not an unresolved read, so the total must reflect zero rather than money the user no
        // longer holds.
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(any()) } returnsMany
            listOf(
                listOf(
                    KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100")
                ),
                listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "0")),
            )
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0") andThenThrows
            RuntimeException("503")

        val vm = viewModel().apply { setData(VAULT_ID) }
        BigDecimal("100").compareTo(vm.state.value.totalValue?.value) shouldBe 0

        vm.refresh()

        BigDecimal.ZERO.compareTo(vm.state.value.totalValue?.value) shouldBe 0
    }

    @Test
    fun `a read position holding nothing does hide Withdraw`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(any()) } returns emptyList()
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

        row.hasPosition shouldBe false
    }

    @Test
    fun `a loss is reported unsigned, because the label says it was lost`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(any()) } returns emptyList()
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")
        coEvery { kaminoApi.getPositionPnl(any(), any()) } returns
            KaminoPnlJson(totalPnl = KaminoPnlJson.Amounts(token = "-3"))

        val row = viewModel().apply { setData(VAULT_ID) }.state.value.rows.single()

        row.pnlDirection shouldBe KaminoEarnRow.PnlDirection.DOWN
        row.pnlDisplay shouldBe "3 USDC"
        // The card prints this opposite the label, which already says the position lost — a
        // "-$3.00" there would say it twice, and it is the same figure the token amount is.
        row.pnlFiat shouldBe "$3.00"
    }

    @Test
    fun `a position whose share balance cannot be read is unknown rather than zero`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        // The position is there — only the figure saying how large it is failed to parse.
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "n/a"))
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val state = viewModel().apply { setData(VAULT_ID) }.state.value
        val row = state.rows.single()

        withClue("a deposit whose size failed to read must not hide Withdraw") {
            row.hasPosition shouldBe true
        }
        row.depositedFiat.shouldBeNull()
        row.fiatValue.shouldBeNull()
        // Counting it as a zero would report the whole wallet as short by a real deposit.
        state.totalValue.shouldBeNull()
    }

    @Test
    fun `a currency change clears the fiat on each card, and a failed reload leaves it clear`() =
        runTest {
            val currency = MutableStateFlow(AppCurrency.USD)
            every { appCurrencyRepository.currency } returns currency
            coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
                flowOf(setOf(STEAKHOUSE.address))
            coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns
                listOf(
                    KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100")
                )
            coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
            coEvery { kaminoApi.getVaultMetrics(any()) } returns
                KaminoVaultMetricsJson(tokensPerShare = "1.0")
            coEvery { kaminoApi.getPositionPnl(any(), any()) } returns
                KaminoPnlJson(totalPnl = KaminoPnlJson.Amounts(token = "5"))

            val vm = viewModel().apply { setData(VAULT_ID) }
            val priced = vm.state.value.rows.single()
            priced.depositedFiat.shouldNotBeNull()
            priced.pnlFiat.shouldNotBeNull()

            // The reload the switch triggers never lands, so nothing rebuilds these cards.
            coEvery { chainAccountAddressRepository.getAddress(Chain.Solana, VAULT) } throws
                RuntimeException("503")
            currency.value = AppCurrency.EUR

            val row = vm.state.value.rows.single()
            row.depositedFiat.shouldBeNull()
            row.fiatValue.shouldBeNull()
            // Earned carries a price of its own now, and it is priced in the same stale currency.
            row.pnlFiat.shouldBeNull()
            // What the vault holds is priced in neither currency, so the card keeps saying it.
            row.depositedDisplay shouldBe priced.depositedDisplay
            row.pnlDisplay shouldBe priced.pnlDisplay
        }

    @Test
    fun `fiat is stamped with the currency it was priced in, not a live read`() = runTest {
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.EUR)
        coEvery { appCurrencyRepository.getCurrencyFormat(AppCurrency.EUR) } returns
            NumberFormat.getCurrencyInstance(Locale.GERMANY)
        // Nothing may reach for the selection as it stands now: it can have moved on since this
        // load priced its figures, and the symbol would then belong to a currency they were not
        // priced in. Any such read shows up here as yen.
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.JAPAN)
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf(STEAKHOUSE.address))
        coEvery { kaminoApi.getUserPositions(WALLET_ADDRESS) } returns
            listOf(KaminoUserPositionJson(vaultAddress = STEAKHOUSE.address, totalShares = "100"))
        coEvery { kaminoApi.getVaultState(any()) } returns stubVault("Steakhouse USDC")
        coEvery { kaminoApi.getVaultMetrics(any()) } returns
            KaminoVaultMetricsJson(tokensPerShare = "1.0")

        val state = viewModel().apply { setData(VAULT_ID) }.state.value

        state.rows.single().depositedFiat.shouldNotBeNull() shouldContain "€"
        state.totalValue.shouldNotBeNull().currency shouldBe AppCurrency.EUR
    }

    private companion object {
        const val VAULT_ID = "vault-id"
        const val WALLET_ADDRESS = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

        val STEAKHOUSE = KaminoVaultRegistry.STEAKHOUSE_USDC
        val ALLEZ = KaminoVaultRegistry.ALLEZ_SOL

        /** A settled tab, as the cache would have it after the user walked away from one. */
        val LAST_RENDERED =
            KaminoEarnSnapshot(
                model =
                    KaminoEarnUiModel(
                        hasEnabledVaults = true,
                        rows =
                            listOf(
                                KaminoEarnRow(
                                    vaultAddress = STEAKHOUSE.address,
                                    name = "Steakhouse USDC",
                                    curator = STEAKHOUSE.curator,
                                    riskTier = STEAKHOUSE.riskTier,
                                    tokenLogo = "usdc",
                                    tokenTicker = "USDC",
                                    depositedDisplay = "100 USDC",
                                    depositedFiat = "$100.00",
                                    apyDisplay = "5.00%",
                                    pnlDisplay = null,
                                    pnlFiat = null,
                                    pnlDirection = KaminoEarnRow.PnlDirection.FLAT,
                                    fiatValue = BigDecimal("100"),
                                    hasPosition = true,
                                )
                            ),
                        totalValue = DefiFiatTotal(BigDecimal("100"), AppCurrency.USD),
                    ),
                totalCoverage = setOf(STEAKHOUSE.address),
                pricedCurrency = AppCurrency.USD,
            )

        val VAULT =
            Vault(
                id = VAULT_ID,
                name = "vault",
                pubKeyECDSA = "",
                pubKeyEDDSA = "",
                hexChainCode = "",
                localPartyID = "",
                signers = emptyList(),
                resharePrefix = "",
                libType = SigningLibType.DKLS,
            )
    }
}
