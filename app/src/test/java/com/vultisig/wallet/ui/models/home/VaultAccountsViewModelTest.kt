@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.TierRemoteNFTService
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
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
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.models.VaultAccountsViewModel
import com.vultisig.wallet.ui.models.mappers.AddressToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        every { vaultDataStoreRepository.readBuyVultBannerDismissed() } returns flowOf(true)
        // Function-type-interface mocks need explicit return-type stubs; relaxed mode auto-stubs
        // to a generic Object that fails the implicit cast at the VM call site.
        coEvery { isGlobalBackupReminderRequired() } returns false
        coEvery { enableTokenUseCase(any(), any()) } returns null
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
            ioDispatcher = testDispatcher,
        )

    /** Verifies dismissBackupReminder sets showMonthlyBackupReminder to false. */
    @Test
    fun `dismissBackupReminder sets showMonthlyBackupReminder to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.dismissBackupReminder()
            vm.uiState.value.showMonthlyBackupReminder.shouldBeFalse()
        }

    /** Verifies tempRemoveBanner sets isBannerVisible to false. */
    @Test
    fun `tempRemoveBanner sets isBannerVisible to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.tempRemoveBanner()
            vm.uiState.value.isBannerVisible.shouldBeFalse()
        }

    /** Verifies onNotificationPermissionResult true sets showNotificationVaultSheet. */
    @Test
    fun `onNotificationPermissionResult true sets showNotificationVaultSheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onNotificationPermissionResult(true)
            vm.uiState.value.showNotificationVaultSheet.shouldBeTrue()
        }

    /** Verifies onNotificationPermissionResult false does not show vault sheet. */
    @Test
    fun `onNotificationPermissionResult false does not show vault sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onNotificationPermissionResult(false)
            vm.uiState.value.showNotificationVaultSheet.shouldBeFalse()
        }

    /** Verifies onNotificationVaultSheetDismiss hides the vault sheet. */
    @Test
    fun `onNotificationVaultSheetDismiss hides the vault sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onNotificationPermissionResult(true)
            vm.onNotificationVaultSheetDismiss()
            vm.uiState.value.showNotificationVaultSheet.shouldBeFalse()
        }

    /** Verifies cryptoConnectionType defaults to Wallet. */
    @Test
    fun `cryptoConnectionType defaults to Wallet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.uiState.value.cryptoConnectionType shouldBe CryptoConnectionType.Wallet
        }

    /** Verifies refreshData re-invokes accountsRepository with isRefresh=true. */
    @Test
    fun `refreshData re-invokes accountsRepository with isRefresh true`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")

            val vm = createViewModel()
            advanceUntilIdle()
            // Drop any loadAddresses calls made during init; only count the refresh call.
            clearMocks(accountsRepository, answers = false)

            vm.refreshData()
            advanceUntilIdle()

            verify(exactly = 1) { accountsRepository.loadAddresses("vault-1", true) }
        }

    /**
     * Verifies refreshData drives `loadAddresses` to completion via the .catch block when the
     * underlying flow throws, leaving `isRefreshing` cleared. The verify guards against a no-op
     * implementation passing the assertion vacuously (default `isRefreshing` is already false).
     */
    @Test
    fun `isRefreshing is cleared after error during refresh`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddresses("vault-1", true) } returns
                flow { throw RuntimeException("Balance load failed") }

            val vm = createViewModel()
            advanceUntilIdle()
            // Sanity: init must not leave the spinner running.
            vm.uiState.value.isRefreshing.shouldBeFalse()

            vm.refreshData()
            advanceUntilIdle()

            vm.uiState.value.isRefreshing.shouldBeFalse()
            verify(atLeast = 1) { accountsRepository.loadAddresses("vault-1", true) }
        }

    /**
     * Verifies that when `accountsRepository.loadAddresses` emits a non-empty list of `Address`
     * during init, the mapped `AccountUiModel`s appear in `uiState.accounts` and preserve the
     * underlying address/chain identity from the source `Address`.
     */
    @Test
    fun `accounts from loadAddresses are surfaced in uiState`() =
        runTest(testDispatcher) {
            val testAddress = buildTestAddress(chain = Chain.Ethereum, address = "0xabc")
            val mappedUiModel =
                AccountUiModel(
                    model = testAddress,
                    chainName = Chain.Ethereum.raw,
                    logo = 0,
                    address = testAddress.address,
                    nativeTokenAmount = "1.0",
                    fiatAmount = "$10.00",
                    assetsSize = testAddress.accounts.size,
                    nativeTokenTicker = "ETH",
                )

            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddresses("vault-1", any()) } returns
                flowOf(listOf(testAddress))
            coEvery { addressToUiModelMapper(any()) } returns mappedUiModel
            coEvery { fiatValueToStringMapper(any(), any()) } returns "$10.00"

            val vm = createViewModel()
            advanceUntilIdle()

            val accounts = vm.uiState.value.accounts
            accounts.isNotEmpty().shouldBeTrue()
            val first = accounts.first()
            first.address shouldBe testAddress.address
            first.chainName shouldBe Chain.Ethereum.raw
            first.model.shouldNotBeNull()
            first.model.chain shouldBe testAddress.chain
        }

    /**
     * Verifies that an exception thrown by `accountsRepository.loadAddresses` during the init load
     * path is caught (via the `.catch` block in `loadAccounts`) so the ViewModel does not crash,
     * `accounts` stays empty, and `isRefreshing` remains cleared. Per-chain failures are already
     * swallowed inside `AccountsRepositoryImpl`, so the only externally observable error channel
     * from a flow consumer's perspective is a whole-flow throwable; this test exercises that
     * pathway from the init side (mirrors the refresh-side test above).
     */
    @Test
    fun `init load swallows loadAddresses failure without crashing`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddresses("vault-1", any()) } returns
                flow { throw RuntimeException("Per-chain balance load failed") }

            val vm = createViewModel()
            advanceUntilIdle()

            // No crash, no spinner left running, and the list stays empty rather than being
            // populated with stale or partial data.
            vm.uiState.value.isRefreshing.shouldBeFalse()
            vm.uiState.value.accounts.isEmpty().shouldBeTrue()
            verify(atLeast = 1) { accountsRepository.loadAddresses("vault-1", any()) }
        }

    /** Verifies dismissBuyVultBanner persists the dismissed flag. */
    @Test
    fun `dismissBuyVultBanner persists dismissed flag`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.dismissBuyVultBanner()
            advanceUntilIdle()
            coVerify { vaultDataStoreRepository.setBuyVultBannerDismissed(true) }
        }

    /** Verifies showBuyVultBanner reflects the persisted dismissed flag (dismissed=false). */
    @Test
    fun `showBuyVultBanner is true when dismissed flag is false`() =
        runTest(testDispatcher) {
            every { vaultDataStoreRepository.readBuyVultBannerDismissed() } returns flowOf(false)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.showBuyVultBanner.shouldBeTrue()
        }

    /** Verifies showBuyVultBanner is false when the persisted dismissed flag is true. */
    @Test
    fun `showBuyVultBanner is false when dismissed flag is true`() =
        runTest(testDispatcher) {
            every { vaultDataStoreRepository.readBuyVultBannerDismissed() } returns flowOf(true)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.showBuyVultBanner.shouldBeFalse()
        }

    /** Verifies buyVult navigates to Swap with VULT preselected when vault already has VULT. */
    @Test
    fun `buyVult navigates to Swap with VULT preselected when vault has VULT`() =
        runTest(testDispatcher) {
            val vault =
                Vault(
                    id = "vault-1",
                    name = "Test",
                    coins = listOf(Coins.Ethereum.VULT.copy(address = "0x1")),
                )
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns vault

            val vm = createViewModel()
            advanceUntilIdle()
            clearMocks(navigator, enableTokenUseCase, answers = false)

            vm.buyVult()
            advanceUntilIdle()

            coVerify(exactly = 0) { enableTokenUseCase(any(), any()) }
            coVerify(exactly = 1) {
                navigator.route(
                    Route.Swap(
                        vaultId = "vault-1",
                        chainId = Chain.Ethereum.id,
                        dstTokenId = Coins.Ethereum.VULT.id,
                    )
                )
            }
        }

    /** Verifies buyVult skips navigation when vault has no Ethereum chain. */
    @Test
    fun `buyVult skips navigation when vault has no Ethereum chain`() =
        runTest(testDispatcher) {
            val vault = Vault(id = "vault-1", name = "Test", coins = emptyList())
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns vault

            val vm = createViewModel()
            advanceUntilIdle()
            clearMocks(navigator, enableTokenUseCase, answers = false)

            vm.buyVult()
            advanceUntilIdle()

            coVerify(exactly = 0) { enableTokenUseCase(any(), any()) }
            coVerify(exactly = 0) { navigator.route(any<Route.Swap>()) }
        }

    /** Verifies buyVult enables VULT then navigates when vault has Ethereum but no VULT. */
    @Test
    fun `buyVult enables VULT and navigates when vault has Ethereum but no VULT`() =
        runTest(testDispatcher) {
            val ethCoin =
                Coin(
                    chain = Chain.Ethereum,
                    ticker = "ETH",
                    logo = "",
                    address = "0xabc",
                    decimal = 18,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                )
            val vault = Vault(id = "vault-1", name = "Test", coins = listOf(ethCoin))
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns vault
            coEvery { enableTokenUseCase("vault-1", Coins.Ethereum.VULT) } returns
                Coins.Ethereum.VULT.id

            val vm = createViewModel()
            advanceUntilIdle()
            clearMocks(navigator, answers = false)

            vm.buyVult()
            advanceUntilIdle()

            coVerify(atLeast = 1) { enableTokenUseCase("vault-1", Coins.Ethereum.VULT) }
            coVerify(exactly = 1) {
                navigator.route(
                    Route.Swap(
                        vaultId = "vault-1",
                        chainId = Chain.Ethereum.id,
                        dstTokenId = Coins.Ethereum.VULT.id,
                    )
                )
            }
        }

    /**
     * Verifies buyVult skips navigation when enableTokenUseCase returns null (e.g. row rejected by
     * SQLiteConstraintException) — the swap form would otherwise open with src and dst both
     * resolving to native ETH, triggering SwapException.SameAssets.
     */
    @Test
    fun `buyVult skips navigation when enableTokenUseCase returns null`() =
        runTest(testDispatcher) {
            val ethCoin =
                Coin(
                    chain = Chain.Ethereum,
                    ticker = "ETH",
                    logo = "",
                    address = "0xabc",
                    decimal = 18,
                    hexPublicKey = "",
                    priceProviderID = "",
                    contractAddress = "",
                    isNativeToken = true,
                )
            val vault = Vault(id = "vault-1", name = "Test", coins = listOf(ethCoin))
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns vault
            coEvery { enableTokenUseCase("vault-1", Coins.Ethereum.VULT) } returns null

            val vm = createViewModel()
            advanceUntilIdle()
            clearMocks(navigator, answers = false)

            vm.buyVult()
            advanceUntilIdle()

            coVerify(exactly = 0) { navigator.route(any<Route.Swap>()) }
        }

    private fun buildTestAddress(chain: Chain, address: String): Address {
        val nativeCoin =
            Coin(
                chain = chain,
                ticker = chain.feeUnit,
                logo = "",
                address = address,
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = "",
                isNativeToken = true,
            )
        val account =
            Account(
                token = nativeCoin,
                tokenValue =
                    TokenValue(value = BigInteger.ONE, unit = nativeCoin.ticker, decimals = 18),
                fiatValue = FiatValue(value = BigDecimal.TEN, currency = "USD"),
                price = FiatValue(value = BigDecimal.TEN, currency = "USD"),
            )
        return Address(chain = chain, address = address, accounts = listOf(account))
    }
}
