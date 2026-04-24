@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.TierRemoteNFTService
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.CryptoConnectionTypeRepository
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TiersNFTRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.IsGlobalBackupReminderRequiredUseCase
import com.vultisig.wallet.data.usecases.NeverShowGlobalBackupReminderUseCase
import com.vultisig.wallet.ui.models.VaultAccountsViewModel
import com.vultisig.wallet.ui.models.mappers.AddressToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [VaultAccountsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VaultAccountsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var navigator: Navigator<Destination>
    private lateinit var requestResultRepository: RequestResultRepository
    private lateinit var addressToUiModelMapper: AddressToUiModelMapper
    private lateinit var fiatValueToStringMapper: FiatValueToStringMapper
    private lateinit var vaultRepository: VaultRepository
    private lateinit var vaultDataStoreRepository: VaultDataStoreRepository
    private lateinit var accountsRepository: AccountsRepository
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var vaultMetadataRepo: VaultMetadataRepo
    private lateinit var isGlobalBackupReminderRequired: IsGlobalBackupReminderRequiredUseCase
    private lateinit var setNeverShowGlobalBackupReminder: NeverShowGlobalBackupReminderUseCase
    private lateinit var lastOpenedVaultRepository: LastOpenedVaultRepository
    private lateinit var enableTokenUseCase: EnableTokenUseCase
    private lateinit var cryptoConnectionTypeRepository: CryptoConnectionTypeRepository
    private lateinit var defaultDeFiChainsRepository: DefaultDeFiChainsRepository
    private lateinit var tiersNFTRepository: TiersNFTRepository
    private lateinit var remoteNFTService: TierRemoteNFTService
    private lateinit var pushNotificationManager: PushNotificationManager
    private lateinit var snackbarFlow: SnackbarFlow

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.Home>() } returns Route.Home()
        context = mockk(relaxed = true)
        navigator = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
        addressToUiModelMapper = mockk(relaxed = true)
        fiatValueToStringMapper = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        vaultDataStoreRepository = mockk(relaxed = true)
        accountsRepository = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        vaultMetadataRepo = mockk(relaxed = true)
        isGlobalBackupReminderRequired = mockk(relaxed = true)
        setNeverShowGlobalBackupReminder = mockk(relaxed = true)
        lastOpenedVaultRepository = mockk(relaxed = true)
        enableTokenUseCase = mockk(relaxed = true)
        cryptoConnectionTypeRepository = mockk(relaxed = true)
        defaultDeFiChainsRepository = mockk(relaxed = true)
        tiersNFTRepository = mockk(relaxed = true)
        remoteNFTService = mockk(relaxed = true)
        pushNotificationManager = mockk(relaxed = true)
        snackbarFlow = mockk(relaxed = true)
        every { cryptoConnectionTypeRepository.activeCryptoConnectionFlow } returns
            MutableStateFlow(CryptoConnectionType.Wallet)
        every { lastOpenedVaultRepository.lastOpenedVaultId } returns emptyFlow()
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        VaultAccountsViewModel(
            context = context,
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            requestResultRepository = requestResultRepository,
            addressToUiModelMapper = addressToUiModelMapper,
            fiatValueToStringMapper = fiatValueToStringMapper,
            vaultRepository = vaultRepository,
            vaultDataStoreRepository = vaultDataStoreRepository,
            accountsRepository = accountsRepository,
            balanceVisibilityRepository = balanceVisibilityRepository,
            vaultMetadataRepo = vaultMetadataRepo,
            isGlobalBackupReminderRequired = isGlobalBackupReminderRequired,
            setNeverShowGlobalBackupReminder = setNeverShowGlobalBackupReminder,
            lastOpenedVaultRepository = lastOpenedVaultRepository,
            enableTokenUseCase = enableTokenUseCase,
            cryptoConnectionTypeRepository = cryptoConnectionTypeRepository,
            defaultDeFiChainsRepository = defaultDeFiChainsRepository,
            tiersNFTRepository = tiersNFTRepository,
            remoteNFTService = remoteNFTService,
            pushNotificationManager = pushNotificationManager,
            snackbarFlow = snackbarFlow,
        )

    /** Verifies dismissBackupReminder sets showMonthlyBackupReminder to false. */
    @Test
    fun `dismissBackupReminder sets showMonthlyBackupReminder to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.dismissBackupReminder()
            assertFalse(vm.uiState.value.showMonthlyBackupReminder)
        }

    /** Verifies tempRemoveBanner sets isBannerVisible to false. */
    @Test
    fun `tempRemoveBanner sets isBannerVisible to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.tempRemoveBanner()
            assertFalse(vm.uiState.value.isBannerVisible)
        }

    /** Verifies onNotificationPermissionResult true sets showNotificationVaultSheet. */
    @Test
    fun `onNotificationPermissionResult true sets showNotificationVaultSheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onNotificationPermissionResult(true)
            assertTrue(vm.uiState.value.showNotificationVaultSheet)
        }

    /** Verifies onNotificationPermissionResult false does not show vault sheet. */
    @Test
    fun `onNotificationPermissionResult false does not show vault sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onNotificationPermissionResult(false)
            assertFalse(vm.uiState.value.showNotificationVaultSheet)
        }

    /** Verifies onNotificationVaultSheetDismiss hides the vault sheet. */
    @Test
    fun `onNotificationVaultSheetDismiss hides the vault sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onNotificationPermissionResult(true)
            vm.onNotificationVaultSheetDismiss()
            assertFalse(vm.uiState.value.showNotificationVaultSheet)
        }

    /** Verifies cryptoConnectionType defaults to Wallet. */
    @Test
    fun `cryptoConnectionType defaults to Wallet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            assertTrue(vm.uiState.value.cryptoConnectionType == CryptoConnectionType.Wallet)
        }

    /** Verifies refreshData re-invokes accountsRepository with isRefresh=true. */
    @Test
    fun `refreshData re-invokes accountsRepository with isRefresh true`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")

            val vm = createViewModel()
            vm.refreshData()

            verify { accountsRepository.loadAddresses("vault-1", true) }
        }

    /** Verifies isRefreshing resets to false when account load errors during refresh. */
    @Test
    fun `isRefreshing resets to false after error during refresh`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddresses(any(), any()) } returns
                flow { throw RuntimeException("Balance load failed") }

            val vm = createViewModel()
            vm.refreshData()

            assertFalse(vm.uiState.value.isRefreshing)
        }
}
