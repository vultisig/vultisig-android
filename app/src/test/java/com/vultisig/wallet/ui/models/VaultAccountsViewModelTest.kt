package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AddressBalancesUpdate
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.CryptoConnectionTypeRepository
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.HasCircleAccountUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the DeFi list being re-read when home comes back to the front: positions are managed a
 * screen deeper, and nothing else on the way back asks the list to look again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VaultAccountsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var accountsRepository: AccountsRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var lastOpenedVaultRepository: LastOpenedVaultRepository
    private lateinit var cryptoConnectionTypeRepository: CryptoConnectionTypeRepository
    private lateinit var defaultDeFiChainsRepository: DefaultDeFiChainsRepository
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var vaultDataStoreRepository: VaultDataStoreRepository
    private lateinit var requestResultRepository: RequestResultRepository
    private lateinit var hasCircleAccount: HasCircleAccountUseCase

    private val connectionType = MutableStateFlow(CryptoConnectionType.Wallet)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        accountsRepository = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        lastOpenedVaultRepository = mockk(relaxed = true)
        cryptoConnectionTypeRepository = mockk(relaxed = true)
        defaultDeFiChainsRepository = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        vaultDataStoreRepository = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
        hasCircleAccount = mockk(relaxed = true)

        every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf(VAULT_ID)
        coEvery { vaultRepository.get(VAULT_ID) } returns VAULT
        every { cryptoConnectionTypeRepository.activeCryptoConnectionFlow } returns connectionType
        every { accountsRepository.loadAddressBalances(VAULT_ID) } returns flowOf()
        coEvery { accountsRepository.loadDeFiAddresses(VAULT_ID, any()) } returns
            flowOf(emptyList())
        every { defaultDeFiChainsRepository.getDefaultChains(VAULT_ID) } returns flowOf(emptySet())
        coEvery { balanceVisibilityRepository.getVisibility(VAULT_ID) } returns true
        coEvery { hasCircleAccount(any()) } returns false
        coEvery { vaultDataStoreRepository.readBackupStatus(VAULT_ID) } returns flowOf(true)
        coEvery { requestResultRepository.request<Unit>(any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `returning to the DeFi tab re-reads the positions`() = runTest {
        connectionType.value = CryptoConnectionType.Defi
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onScreenResumed()
        advanceUntilIdle()

        // A network read, not the cached one the first load settles for: the position that changed
        // one screen deeper is only visible once its balance is fetched again.
        coVerify(atLeast = 1) { accountsRepository.loadDeFiAddresses(VAULT_ID, true) }
    }

    @Test
    fun `returning to the wallet tab leaves the DeFi list alone`() = runTest {
        connectionType.value = CryptoConnectionType.Wallet
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onScreenResumed()
        advanceUntilIdle()

        coVerify(exactly = 0) { accountsRepository.loadDeFiAddresses(VAULT_ID, true) }
    }

    @Test
    fun `pulling to refresh on the DeFi tab refreshes the list being pulled`() = runTest {
        connectionType.value = CryptoConnectionType.Defi
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.refreshData()
        advanceUntilIdle()

        coVerify(atLeast = 1) { accountsRepository.loadDeFiAddresses(VAULT_ID, true) }
    }

    @Test
    fun `resuming right after a read reuses it instead of fetching again`() = runTest {
        connectionType.value = CryptoConnectionType.Defi
        val viewModel = viewModel()
        runCurrent()

        // Stands in for anything that reloads the list on its own on the way back — the chain
        // picker does exactly this once its result lands, a step before home resumes behind it.
        viewModel.refreshData()
        runCurrent()
        viewModel.onScreenResumed()
        runCurrent()

        coVerify(exactly = 1) { accountsRepository.loadDeFiAddresses(VAULT_ID, true) }
    }

    @Test
    fun `resuming once the read has gone stale fetches again`() = runTest {
        connectionType.value = CryptoConnectionType.Defi
        val viewModel = viewModel()
        runCurrent()

        viewModel.refreshData()
        runCurrent()
        advanceTimeBy(THROTTLE_WINDOW + 1.seconds)
        viewModel.onScreenResumed()
        runCurrent()

        coVerify(exactly = 2) { accountsRepository.loadDeFiAddresses(VAULT_ID, true) }
    }

    @Test
    fun `pulling on the DeFi tab keeps the spinner up until the DeFi list lands`() = runTest {
        connectionType.value = CryptoConnectionType.Defi
        val deFiAddresses = Channel<List<Address>>(Channel.UNLIMITED)
        coEvery { accountsRepository.loadDeFiAddresses(VAULT_ID, true) } returns
            deFiAddresses.consumeAsFlow()
        // The wallet stream is done as soon as the pull starts; it says nothing about the DeFi
        // rows the user is actually pulling on.
        every { accountsRepository.loadAddressBalances(VAULT_ID) } returns
            flowOf(AddressBalancesUpdate(addresses = emptyList(), isComplete = true))
        val viewModel = viewModel()
        runCurrent()

        viewModel.refreshData()
        runCurrent()

        viewModel.uiState.value.isRefreshing shouldBe true

        deFiAddresses.close()
        runCurrent()

        viewModel.uiState.value.isRefreshing shouldBe false
    }

    @Test
    fun `pulling on the wallet tab still ends with the wallet stream`() = runTest {
        connectionType.value = CryptoConnectionType.Wallet
        every { accountsRepository.loadAddressBalances(VAULT_ID) } returns
            flowOf(AddressBalancesUpdate(addresses = emptyList(), isComplete = true))
        val viewModel = viewModel()
        runCurrent()

        viewModel.refreshData()
        runCurrent()

        viewModel.uiState.value.isRefreshing shouldBe false
    }

    @Test
    fun `an unrelated preference write does not reload the vault`() = runTest {
        connectionType.value = CryptoConnectionType.Defi
        val vaultIds = MutableSharedFlow<String?>(replay = 1)
        vaultIds.emit(VAULT_ID)
        every { lastOpenedVaultRepository.lastOpenedVaultId } returns vaultIds

        viewModel()
        advanceUntilIdle()

        // AppDataStore reads the whole preferences file, so a write to any other key re-emits the
        // id already on screen. Emitted from a SharedFlow, like the DataStore one it stands in
        // for: a StateFlow would conflate the repeat away before the screen ever saw it.
        vaultIds.emit(VAULT_ID)
        advanceUntilIdle()

        verify(exactly = 1) { accountsRepository.loadAddressBalances(VAULT_ID) }
        coVerify(exactly = 1) { accountsRepository.loadDeFiAddresses(VAULT_ID, any()) }
    }

    @Test
    fun `a reload landing mid-pull still takes the spinner down`() = runTest {
        connectionType.value = CryptoConnectionType.Defi
        val deFiAddresses = Channel<List<Address>>(Channel.UNLIMITED)
        coEvery { accountsRepository.loadDeFiAddresses(VAULT_ID, true) } returns
            deFiAddresses.receiveAsFlow()
        val viewModel = viewModel()
        runCurrent()

        viewModel.refreshData()
        runCurrent()

        viewModel.uiState.value.isRefreshing shouldBe true

        // Reachable from the wallet tab: its chain picker reloads everything on the way back, the
        // DeFi list included, cancelling the fetch the pull started on the other tab is waiting on.
        viewModel.setCryptoConnectionType(CryptoConnectionType.Wallet)
        viewModel.openAddChainAccount()
        runCurrent()

        // The replacement carries the pull's read forward instead of answering it from the cache,
        // so the pull still ends on a fetch rather than leaving the spinner up for good.
        coVerify(exactly = 2) { accountsRepository.loadDeFiAddresses(VAULT_ID, true) }

        deFiAddresses.close()
        runCurrent()

        viewModel.uiState.value.isRefreshing shouldBe false
    }

    @Test
    fun `backing the vault up takes the warning down`() = runTest {
        val isBackedUp = MutableStateFlow(false)
        coEvery { vaultDataStoreRepository.readBackupStatus(VAULT_ID) } returns isBackedUp
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.uiState.value.showBackupWarning shouldBe true

        // Backing up happens a screen away while this one is still alive.
        isBackedUp.value = true
        advanceUntilIdle()

        viewModel.uiState.value.showBackupWarning shouldBe false
    }

    private fun viewModel() =
        VaultAccountsViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = mockk(relaxed = true),
            requestResultRepository = requestResultRepository,
            addressToUiModelMapper = mockk(relaxed = true),
            fiatValueToStringMapper = mockk(relaxed = true),
            vaultRepository = vaultRepository,
            vaultDataStoreRepository = vaultDataStoreRepository,
            accountsRepository = accountsRepository,
            balanceVisibilityRepository = balanceVisibilityRepository,
            vaultMetadataRepo = mockk(relaxed = true),
            isGlobalBackupReminderRequired = mockk(relaxed = true),
            setNeverShowGlobalBackupReminder = mockk(relaxed = true),
            lastOpenedVaultRepository = lastOpenedVaultRepository,
            enableTokenUseCase = mockk(relaxed = true),
            promoBannerDismissalRepository = mockk(relaxed = true),
            cryptoConnectionTypeRepository = cryptoConnectionTypeRepository,
            defaultDeFiChainsRepository = defaultDeFiChainsRepository,
            hasCircleAccount = hasCircleAccount,
            tiersNFTRepository = mockk(relaxed = true),
            remoteNFTService = mockk(relaxed = true),
            pushNotificationManager = mockk(relaxed = true),
            snackbarFlow = mockk(relaxed = true),
            ioDispatcher = testDispatcher,
        )

    private companion object {
        const val VAULT_ID = "vault-id"

        // Mirrors DEFI_REFRESH_THROTTLE, which is private to the ViewModel.
        val THROTTLE_WINDOW = 15.seconds

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
