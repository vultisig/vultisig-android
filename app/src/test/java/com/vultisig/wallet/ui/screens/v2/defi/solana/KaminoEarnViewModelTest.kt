package com.vultisig.wallet.ui.screens.v2.defi.solana

import com.vultisig.wallet.data.api.KaminoApi
import com.vultisig.wallet.data.api.KaminoPnlJson
import com.vultisig.wallet.data.api.KaminoUserPositionJson
import com.vultisig.wallet.data.api.KaminoVaultMetricsJson
import com.vultisig.wallet.data.api.KaminoVaultStateJson
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRiskTier
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.KaminoVaultSelectionRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        navigator = mockk(relaxed = true)
        kaminoApi = mockk(relaxed = true)
        selectionRepository = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        chainAccountAddressRepository = mockk(relaxed = true)
        tokenPriceRepository = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)

        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        coEvery { chainAccountAddressRepository.getAddress(Chain.Solana, VAULT) } returns
            (WALLET_ADDRESS to "pubkey")
        every { appCurrencyRepository.currency } returns flowOf(AppCurrency.USD)
        coEvery { appCurrencyRepository.getCurrencyFormat() } returns
            NumberFormat.getCurrencyInstance(Locale.US)
        coEvery { tokenPriceRepository.getCachedPrice(any(), any()) } returns BigDecimal.ONE
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
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
            ioDispatcher = testDispatcher,
        )

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

        assertFalse(state.hasEnabledVaults)
        assertTrue(state.rows.isEmpty())
        assertNull(state.totalFiat)
        assertFalse(state.isLoading)
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

        assertTrue(state.hasEnabledVaults)
        assertEquals(1, state.rows.size)
        val row = state.rows.single()
        assertEquals("Steakhouse USDC", row.name)
        assertEquals("Steakhouse Financial", row.curator)
        assertEquals(KaminoRiskTier.CONSERVATIVE, row.riskTier)
        assertEquals("0 USDC", row.depositedDisplay)
        assertEquals("4.00%", row.apyDisplay)
        // Zero is not a gain: an untouched vault must not render green.
        assertEquals(KaminoEarnRow.PnlDirection.FLAT, row.pnlDirection)
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

        assertEquals("1054.427822 USDC", row.depositedDisplay)
        assertEquals("54.427822 USDC", row.pnlDisplay)
        assertEquals(KaminoEarnRow.PnlDirection.UP, row.pnlDirection)
        assertNotNull(row.depositedFiat)
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

        assertEquals(KaminoEarnRow.PnlDirection.DOWN, row.pnlDirection)
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
        assertEquals("Steakhouse USDC", row.name)
        assertNull(row.apyDisplay)
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

        assertEquals(STEAKHOUSE.fallbackName, row.name)
    }

    @Test
    fun `a vault the registry does not know is ignored`() = runTest {
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns
            flowOf(setOf("2Z6C84pCc2ri8t39jvRCXnTGFQqUJf1mMpUMtpeFfhyB"))

        val state = viewModel().apply { setData(VAULT_ID) }.state.value

        assertTrue(state.rows.isEmpty())
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

        assertEquals(2, state.rows.size)
        // Priced at 1.0 each, 100 + 100 shares against a 1:1 share ratio.
        assertEquals("$200.00", state.totalFiat)
    }

    @Test
    fun `hidden balances are surfaced to the view`() = runTest {
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns false
        coEvery { selectionRepository.getSelectedVaults(VAULT_ID) } returns flowOf(emptySet())

        assertFalse(viewModel().apply { setData(VAULT_ID) }.state.value.isBalanceVisible)
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

        assertTrue(vm.state.value.isShowingPicker)
        assertEquals(setOf(STEAKHOUSE.address), vm.state.value.pendingSelection)

        vm.onVaultToggled(ALLEZ.address, true)
        vm.savePicker()

        assertFalse(vm.state.value.isShowingPicker)
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

        assertFalse(vm.state.value.isShowingPicker)
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

        assertTrue(vm.state.value.pendingSelection.isEmpty())
    }

    private companion object {
        const val VAULT_ID = "vault-id"
        const val WALLET_ADDRESS = "9ceRgz579BcfWogs3RE11FKNQaWW7Lmtnev3MXspxUjF"

        val STEAKHOUSE = KaminoVaultRegistry.STEAKHOUSE_USDC
        val ALLEZ = KaminoVaultRegistry.ALLEZ_SOL

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
