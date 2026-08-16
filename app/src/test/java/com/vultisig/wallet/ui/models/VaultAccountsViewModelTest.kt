package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.CryptoConnectionTypeRepository
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.HasCircleAccountUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

    private fun viewModel() =
        VaultAccountsViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = mockk(relaxed = true),
            requestResultRepository = mockk(relaxed = true),
            addressToUiModelMapper = mockk(relaxed = true),
            fiatValueToStringMapper = mockk(relaxed = true),
            vaultRepository = vaultRepository,
            vaultDataStoreRepository = mockk(relaxed = true),
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
