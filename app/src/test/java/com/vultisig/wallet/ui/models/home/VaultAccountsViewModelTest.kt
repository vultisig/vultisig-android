@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.home

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
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AddressBalancesUpdate
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
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.HasCircleAccountUseCase
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
import kotlinx.coroutines.CompletableDeferred
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
    private lateinit var getDiscountBpsUseCase: GetDiscountBpsUseCase
    private lateinit var cryptoConnectionTypeRepository: CryptoConnectionTypeRepository
    private lateinit var defaultDeFiChainsRepository: DefaultDeFiChainsRepository
    private lateinit var hasCircleAccount: HasCircleAccountUseCase
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
        getDiscountBpsUseCase = mockk(relaxed = true)
        cryptoConnectionTypeRepository = mockk(relaxed = true)
        defaultDeFiChainsRepository = mockk(relaxed = true)
        hasCircleAccount = mockk(relaxed = true)
        tiersNFTRepository = mockk(relaxed = true)
        remoteNFTService = mockk(relaxed = true)
        pushNotificationManager = mockk(relaxed = true)
        snackbarFlow = mockk(relaxed = true)
        every { cryptoConnectionTypeRepository.activeCryptoConnectionFlow } returns
            MutableStateFlow(CryptoConnectionType.Wallet)
        every { lastOpenedVaultRepository.lastOpenedVaultId } returns emptyFlow()
        every { vaultDataStoreRepository.readBuyVultBannerDismissed(any()) } returns flowOf(true)
        coEvery { getDiscountBpsUseCase.hasReachedSilverTier(any()) } returns false
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
            getDiscountBpsUseCase = getDiscountBpsUseCase,
            cryptoConnectionTypeRepository = cryptoConnectionTypeRepository,
            defaultDeFiChainsRepository = defaultDeFiChainsRepository,
            hasCircleAccount = hasCircleAccount,
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

    /**
     * Verifies refreshData re-triggers a balance reload via accountsRepository.loadAddressBalances.
     */
    @Test
    fun `refreshData re-invokes loadAddressBalances`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")

            val vm = createViewModel()
            advanceUntilIdle()
            // Drop any loadAddressBalances calls made during init; only count the refresh call.
            clearMocks(accountsRepository, answers = false)

            vm.refreshData()
            advanceUntilIdle()

            verify(exactly = 1) { accountsRepository.loadAddressBalances("vault-1") }
        }

    /**
     * Verifies refreshData drives the balance load to completion via the .catch block when the
     * underlying flow throws, leaving `isRefreshing` cleared. The verify guards against a no-op
     * implementation passing the assertion vacuously (default `isRefreshing` is already false).
     */
    @Test
    fun `isRefreshing is cleared after error during refresh`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddressBalances("vault-1") } returns
                flow { throw RuntimeException("Balance load failed") }

            val vm = createViewModel()
            advanceUntilIdle()
            // Sanity: init must not leave the spinner running.
            vm.uiState.value.isRefreshing.shouldBeFalse()
            // Drop any loadAddressBalances calls made during init; only count the refresh call.
            clearMocks(accountsRepository, answers = false)

            vm.refreshData()
            advanceUntilIdle()

            vm.uiState.value.isRefreshing.shouldBeFalse()
            verify(exactly = 1) { accountsRepository.loadAddressBalances("vault-1") }
        }

    /**
     * Verifies the pull-to-refresh spinner stays up until the whole refresh completes (matching
     * iOS/Windows) rather than clearing on the cached snapshot that loadAddressBalances emits
     * first. The spinner must remain while only the cached (isComplete = false) emission has been
     * seen, and clear only once the terminal (isComplete = true) emission arrives.
     */
    @Test
    fun `refresh spinner stays up until load completes`() =
        runTest(testDispatcher) {
            val address = buildTestAddress(chain = Chain.Ethereum, address = "0xabc")
            val completeGate = CompletableDeferred<Unit>()
            every { accountsRepository.loadAddressBalances("vault-1") } returns
                flow {
                    emit(AddressBalancesUpdate(listOf(address), isComplete = false))
                    completeGate.await()
                    emit(AddressBalancesUpdate(listOf(address), isComplete = true))
                }
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            // Stub the mappers so rendering the cached snapshot succeeds; an unstubbed mapper would
            // throw and trip the .catch in loadAccounts, clearing the spinner for the wrong reason.
            coEvery { addressToUiModelMapper(any()) } returns
                AccountUiModel(
                    model = address,
                    chainName = Chain.Ethereum.raw,
                    logo = 0,
                    address = address.address,
                    nativeTokenAmount = "1.0",
                    fiatAmount = "$10.00",
                    assetsSize = address.accounts.size,
                    nativeTokenTicker = "ETH",
                )
            coEvery { fiatValueToStringMapper(any(), any()) } returns "$10.00"

            val vm = createViewModel()
            advanceUntilIdle()

            vm.refreshData()
            advanceUntilIdle()

            // Only the cached snapshot has been emitted; the network balances are still loading,
            // so the spinner must still be up.
            vm.uiState.value.isRefreshing.shouldBeTrue()

            completeGate.complete(Unit)
            advanceUntilIdle()

            // Terminal emission arrived — the refresh is done and the spinner clears.
            vm.uiState.value.isRefreshing.shouldBeFalse()
        }

    /**
     * Verifies that when `accountsRepository.loadAddressBalances` emits a non-empty list of
     * `Address` during init, the mapped `AccountUiModel`s appear in `uiState.accounts` and preserve
     * the underlying address/chain identity from the source `Address`.
     */
    @Test
    fun `accounts from loadAddressBalances are surfaced in uiState`() =
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
            every { accountsRepository.loadAddressBalances("vault-1") } returns
                flowOf(AddressBalancesUpdate(listOf(testAddress), isComplete = true))
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
     * Verifies that an exception thrown by `accountsRepository.loadAddressBalances` during the init
     * load path is caught (via the `.catch` block in `loadAccounts`) so the ViewModel does not
     * crash, `accounts` stays empty, and `isRefreshing` remains cleared. Per-chain failures are
     * already swallowed inside `AccountsRepositoryImpl`, so the only externally observable error
     * channel from a flow consumer's perspective is a whole-flow throwable; this test exercises
     * that pathway from the init side (mirrors the refresh-side test above).
     */
    @Test
    fun `init load swallows loadAddressBalances failure without crashing`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddressBalances("vault-1") } returns
                flow { throw RuntimeException("Per-chain balance load failed") }

            val vm = createViewModel()
            advanceUntilIdle()

            // No crash, no spinner left running, and the list stays empty rather than being
            // populated with stale or partial data.
            vm.uiState.value.isRefreshing.shouldBeFalse()
            vm.uiState.value.accounts.isEmpty().shouldBeTrue()
            verify(atLeast = 1) { accountsRepository.loadAddressBalances("vault-1") }
        }

    /**
     * Verifies dismissBuyVultBanner persists the dismissed flag once the vault reaches Silver tier.
     */
    @Test
    fun `dismissBuyVultBanner persists dismissed flag at Silver tier`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            coEvery { getDiscountBpsUseCase.hasReachedSilverTier("vault-1") } returns true
            val vm = createViewModel()
            advanceUntilIdle()

            vm.dismissBuyVultBanner()
            advanceUntilIdle()

            coVerify { vaultDataStoreRepository.setBuyVultBannerDismissed("vault-1", true) }
        }

    /**
     * Verifies that below Silver tier, dismiss hides the banner for the visit without persisting.
     */
    @Test
    fun `dismissBuyVultBanner below Silver tier hides for visit without persisting`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            coEvery { getDiscountBpsUseCase.hasReachedSilverTier("vault-1") } returns false
            every { vaultDataStoreRepository.readBuyVultBannerDismissed("vault-1") } returns
                flowOf(false)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.showBuyVultBanner.shouldBeTrue()

            vm.dismissBuyVultBanner()
            advanceUntilIdle()

            vm.uiState.value.showBuyVultBanner.shouldBeFalse()
            coVerify(exactly = 0) {
                vaultDataStoreRepository.setBuyVultBannerDismissed(any(), any())
            }
        }

    /** Verifies showBuyVultBanner reflects the persisted dismissed flag (dismissed=false). */
    @Test
    fun `showBuyVultBanner is true when dismissed flag is false`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { vaultDataStoreRepository.readBuyVultBannerDismissed("vault-1") } returns
                flowOf(false)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.showBuyVultBanner.shouldBeTrue()
        }

    /** Verifies showBuyVultBanner is false when the persisted dismissed flag is true. */
    @Test
    fun `showBuyVultBanner is false when dismissed flag is true`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { vaultDataStoreRepository.readBuyVultBannerDismissed("vault-1") } returns
                flowOf(true)
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

    /**
     * Regression for #4768: when a chain's balance refetch is in flight its mapped fiat comes back
     * null, but the row must keep showing the last-known value instead of blanking. Here Solana
     * resolves to $5 on the first load, then a refresh re-emits Solana with an unresolved (null)
     * fiat — the Solana row must still read $5 while Ethereum updates normally.
     */
    @Test
    fun `per-chain row keeps last-known fiat while it refetches`() =
        runTest(testDispatcher) {
            val eth = buildTestAddress(Chain.Ethereum, "0xeth", fiat = BigDecimal("10"))
            val solResolved = buildTestAddress(Chain.Solana, "sol", fiat = BigDecimal("5"))
            val solPending = buildTestAddress(Chain.Solana, "sol", fiat = null)

            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddressBalances("vault-1") } returnsMany
                listOf(
                    flowOf(AddressBalancesUpdate(listOf(eth, solResolved), isComplete = true)),
                    flowOf(AddressBalancesUpdate(listOf(eth, solPending), isComplete = true)),
                )
            stubBalanceMappers()

            val vm = createViewModel()
            advanceUntilIdle()

            vm.solanaRow().fiatAmount shouldBe "$5"

            vm.refreshData()
            advanceUntilIdle()

            // Solana refetch returned null fiat — the row retains the previously-shown $5.
            vm.solanaRow().fiatAmount shouldBe "$5"
            // Ethereum stayed resolved and still shows its own value.
            vm.uiState.value.accounts.first { it.model.chain == Chain.Ethereum }.fiatAmount shouldBe
                "$10"
        }

    /**
     * Regression for #4768: the big portfolio total must not blank while a chain refetches. With
     * one chain resolved and another pending the total reflects the resolved chain rather than
     * going null.
     */
    @Test
    fun `total reflects resolved chains while another is pending`() =
        runTest(testDispatcher) {
            val eth = buildTestAddress(Chain.Ethereum, "0xeth", fiat = BigDecimal("10"))
            val solPending = buildTestAddress(Chain.Solana, "sol", fiat = null)

            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddressBalances("vault-1") } returns
                flowOf(AddressBalancesUpdate(listOf(eth, solPending), isComplete = true))
            stubBalanceMappers()

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.totalFiatValue shouldBe "$10"
        }

    private fun VaultAccountsViewModel.solanaRow(): AccountUiModel =
        uiState.value.accounts.first { it.model.chain == Chain.Solana }

    /**
     * Maps each [Address] to a UiModel whose fiat is null exactly when the chain hasn't resolved.
     */
    private fun stubBalanceMappers() {
        coEvery { addressToUiModelMapper(any()) } answers
            {
                val addr = firstArg<Address>()
                val native = addr.accounts.first()
                AccountUiModel(
                    model = addr,
                    chainName = addr.chain.raw,
                    logo = 0,
                    address = addr.address,
                    nativeTokenAmount = native.tokenValue?.let { "1.0" },
                    fiatAmount =
                        addr.accounts.calculateAccountsTotalFiatValue()?.let {
                            "$" + it.value.toPlainString()
                        },
                    assetsSize = addr.accounts.size,
                    nativeTokenTicker = native.token.ticker,
                )
            }
        coEvery { fiatValueToStringMapper(any(), any()) } answers
            {
                "$" + firstArg<FiatValue>().value.toPlainString()
            }
    }

    private fun buildTestAddress(chain: Chain, address: String, fiat: BigDecimal?): Address {
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
                    fiat?.let { TokenValue(value = BigInteger.ONE, unit = nativeCoin.ticker, 18) },
                fiatValue = fiat?.let { FiatValue(value = it, currency = "USD") },
                price = fiat?.let { FiatValue(value = it, currency = "USD") },
            )
        return Address(chain = chain, address = address, accounts = listOf(account))
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
