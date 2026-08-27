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
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.calculateAccountsPartialFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AddressBalancesUpdate
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.CryptoConnectionTypeRepository
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.PromoBanner
import com.vultisig.wallet.data.repositories.PromoBannerDismissalRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TiersNFTRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.HasCircleAccountUseCase
import com.vultisig.wallet.data.usecases.IsGlobalBackupReminderRequiredUseCase
import com.vultisig.wallet.data.usecases.NeverShowGlobalBackupReminderUseCase
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.models.VaultAccountsViewModel
import com.vultisig.wallet.ui.models.mappers.AddressToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.HomeBannerType
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
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
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private lateinit var promoBannerDismissalRepository: PromoBannerDismissalRepository
    private lateinit var referralCodeSettingsRepository: ReferralCodeSettingsRepositoryContract
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
        promoBannerDismissalRepository = mockk(relaxed = true)
        referralCodeSettingsRepository = mockk(relaxed = true)
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
        every { promoBannerDismissalRepository.isDismissed(any()) } returns flowOf(false)
        // The banner carousel combines the backup flow, so a relaxed mock's non-emitting Flow
        // would stall it. Backed up by default, which keeps the backup banner out of the way.
        coEvery { vaultDataStoreRepository.readBackupStatus(any()) } returns flowOf(true)
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
            promoBannerDismissalRepository = promoBannerDismissalRepository,
            referralCodeSettingsRepository = referralCodeSettingsRepository,
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

    /** Verifies dismissing the Follow-X banner records a global dismissal for that banner. */
    @Test
    fun `onBannerDismiss records a global dismissal for Follow X`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onBannerDismiss(HomeBannerType.FollowX)
            advanceUntilIdle()
            coVerify { promoBannerDismissalRepository.dismiss(PromoBanner.FollowXVultisig) }
        }

    /** Verifies dismissing the upgrade banner records a global dismissal for that banner. */
    @Test
    fun `onBannerDismiss records a global dismissal for Upgrade`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onBannerDismiss(HomeBannerType.UpgradeVault)
            advanceUntilIdle()
            coVerify { promoBannerDismissalRepository.dismiss(PromoBanner.UpgradeVaultDkls) }
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

    /** Verifies dismissing the Buy VULT banner records a global dismissal for that banner. */
    @Test
    fun `onBannerDismiss records a global dismissal for Buy VULT`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onBannerDismiss(HomeBannerType.BuyVult)
            advanceUntilIdle()

            coVerify { promoBannerDismissalRepository.dismiss(PromoBanner.BuyVultSwap) }
        }

    /** Verifies the Buy VULT banner is offered while it is not within its dismissal TTL. */
    @Test
    fun `banners include Buy VULT when not dismissed`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { promoBannerDismissalRepository.isDismissed(PromoBanner.BuyVultSwap) } returns
                flowOf(false)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldContain(HomeBannerType.BuyVult)
        }

    /** Verifies the Buy VULT banner is withheld while it is within its dismissal TTL. */
    @Test
    fun `banners exclude Buy VULT when dismissed within TTL`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { promoBannerDismissalRepository.isDismissed(PromoBanner.BuyVultSwap) } returns
                flowOf(true)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldNotContain(HomeBannerType.BuyVult)
        }

    /** Verifies the upgrade banner shows only for a GG20 (migration-eligible) vault. */
    @Test
    fun `banners include Upgrade for a GG20 vault that is not dismissed`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns
                Vault(id = "vault-1", name = "Test", libType = SigningLibType.GG20)
            every {
                promoBannerDismissalRepository.isDismissed(PromoBanner.UpgradeVaultDkls)
            } returns flowOf(false)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldContain(HomeBannerType.UpgradeVault)
        }

    /** Verifies the upgrade banner is hidden for a non-GG20 vault even when not dismissed. */
    @Test
    fun `banners exclude Upgrade for a non-GG20 vault`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns
                Vault(id = "vault-1", name = "Test", libType = SigningLibType.DKLS)
            every {
                promoBannerDismissalRepository.isDismissed(PromoBanner.UpgradeVaultDkls)
            } returns flowOf(false)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldNotContain(HomeBannerType.UpgradeVault)
        }

    /**
     * Kamino and Rujira each open one chain's DeFi screen, so a vault without that chain has
     * nowhere for them to go and must not be offered them.
     */
    @Test
    fun `banners exclude chain-gated promos for a vault without those chains`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns
                Vault(id = "vault-1", name = "Test", coins = listOf(Coins.Bitcoin.BTC))
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldNotContain(HomeBannerType.KaminoEarn)
            vm.uiState.value.banners.shouldNotContain(HomeBannerType.RujiraStaking)
        }

    /** Verifies the chain-gated promos are offered once their chain is enabled on the vault. */
    @Test
    fun `banners include chain-gated promos for a vault with Solana and THORChain`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns
                Vault(
                    id = "vault-1",
                    name = "Test",
                    coins = listOf(Coins.Solana.SOL, Coins.ThorChain.RUNE),
                )
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldContain(HomeBannerType.KaminoEarn)
            vm.uiState.value.banners.shouldContain(HomeBannerType.RujiraStaking)
        }

    /** Verifies the referral promo is withheld once the vault already carries a referred code. */
    @Test
    fun `banners exclude Referral when the vault already has a referred code`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { referralCodeSettingsRepository.getExternalReferralBy("vault-1") } returns "ABC"
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldNotContain(HomeBannerType.ReferralRewards)
        }

    /** Verifies the referral promo is offered while no referred code has been entered. */
    @Test
    fun `banners include Referral when the vault has no referred code`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { referralCodeSettingsRepository.getExternalReferralBy("vault-1") } returns null
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldContain(HomeBannerType.ReferralRewards)
        }

    /**
     * The backup reminder tracks the vault's live backup state rather than a snapshot: backing the
     * vault up happens on another screen while home is still alive.
     */
    @Test
    fun `banners drop Backup once the vault is backed up`() =
        runTest(testDispatcher) {
            val backupStatus = MutableStateFlow(false)
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            coEvery { vaultDataStoreRepository.readBackupStatus("vault-1") } returns backupStatus
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldContain(HomeBannerType.BackupVault)

            backupStatus.value = true
            advanceUntilIdle()

            vm.uiState.value.banners.shouldNotContain(HomeBannerType.BackupVault)
        }

    /** Carousel order is the declaration order of [HomeBannerType], which is Figma's order. */
    @Test
    fun `banners keep Figma carousel order`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns
                Vault(
                    id = "vault-1",
                    name = "Test",
                    libType = SigningLibType.GG20,
                    coins = listOf(Coins.Solana.SOL, Coins.ThorChain.RUNE),
                )
            coEvery { vaultDataStoreRepository.readBackupStatus("vault-1") } returns flowOf(false)
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldContainExactly(HomeBannerType.entries)
        }

    /**
     * The chain dashboard renders the side of the wallet / DeFi toggle that is active rather than
     * the route it was opened with, so a banner tapped from the wallet tab has to move the toggle
     * or it lands on Solana's token list instead of Kamino.
     */
    @Test
    fun `Kamino banner switches to DeFi before opening Solana positions`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns
                Vault(id = "vault-1", name = "Test", coins = listOf(Coins.Solana.SOL))
            val vm = createViewModel()
            advanceUntilIdle()
            clearMocks(navigator, cryptoConnectionTypeRepository, answers = false)

            vm.onBannerClick(HomeBannerType.KaminoEarn)
            advanceUntilIdle()

            verify(exactly = 1) {
                cryptoConnectionTypeRepository.setActiveCryptoConnection(CryptoConnectionType.Defi)
            }
            coVerify(exactly = 1) {
                navigator.route(
                    Route.ChainDashboard(ChainDashboardRoute.PositionSolana(vaultId = "vault-1"))
                )
            }
        }

    /**
     * THORChain's positions screen opens on Bonded, which is the node-bonding list — a tab away
     * from the Rujira staking the banner advertises.
     */
    @Test
    fun `Rujira banner opens THORChain positions on the Staked tab`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns
                Vault(id = "vault-1", name = "Test", coins = listOf(Coins.ThorChain.RUNE))
            val vm = createViewModel()
            advanceUntilIdle()
            clearMocks(navigator, cryptoConnectionTypeRepository, answers = false)

            vm.onBannerClick(HomeBannerType.RujiraStaking)
            advanceUntilIdle()

            verify(exactly = 1) {
                cryptoConnectionTypeRepository.setActiveCryptoConnection(CryptoConnectionType.Defi)
            }
            coVerify(exactly = 1) {
                navigator.route(
                    Route.ChainDashboard(
                        ChainDashboardRoute.PositionTokens(
                            vaultId = "vault-1",
                            tab = DeFiTab.STAKED,
                        )
                    )
                )
            }
        }

    /**
     * The code is entered on the screen the banner itself opens, and home is still alive behind it,
     * so the promo has to be re-evaluated on the way back or it keeps asking for a code the user
     * has already given.
     */
    @Test
    fun `onScreenResumed drops the Referral banner once a code has been entered`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { referralCodeSettingsRepository.getExternalReferralBy("vault-1") } returns null
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldContain(HomeBannerType.ReferralRewards)

            every { referralCodeSettingsRepository.getExternalReferralBy("vault-1") } returns "ABC"
            vm.onScreenResumed()
            advanceUntilIdle()

            vm.uiState.value.banners.shouldNotContain(HomeBannerType.ReferralRewards)
        }

    /** The reverse: removing the code offers the promo again on the next return to home. */
    @Test
    fun `onScreenResumed restores the Referral banner once the code is removed`() =
        runTest(testDispatcher) {
            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { referralCodeSettingsRepository.getExternalReferralBy("vault-1") } returns "ABC"
            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.banners.shouldNotContain(HomeBannerType.ReferralRewards)

            every { referralCodeSettingsRepository.getExternalReferralBy("vault-1") } returns null
            vm.onScreenResumed()
            advanceUntilIdle()

            vm.uiState.value.banners.shouldContain(HomeBannerType.ReferralRewards)
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

    /**
     * Regression for #4768: the portfolio total must equal the sum of the values its rows render.
     * Solana resolves to $5 then refetches (null); because retain keeps Solana's $5 in both the row
     * and the total, the headline stays $15 (10 + 5) rather than dropping to $10 and disagreeing
     * with the row that still shows $5.
     */
    @Test
    fun `total stays consistent with the rows while a chain refetches`() =
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
            vm.uiState.value.totalFiatValue shouldBe "$15"

            vm.refreshData()
            advanceUntilIdle()

            // Row still shows the retained $5, and the total still counts it: 10 + 5 = 15.
            vm.solanaRow().fiatAmount shouldBe "$5"
            vm.uiState.value.totalFiatValue shouldBe "$15"
        }

    /**
     * Regression for #4768: within a single chain holding several tokens, a still-pending token
     * must not blank the whole row. The partial total counts the resolved tokens, so the row must
     * too — here Ethereum's native token resolves to $10 while an ERC-20 is still pending; the row
     * reads $10 (not blank) and equals its contribution to the total.
     */
    @Test
    fun `multi-token row shows resolved sum while one token is pending`() =
        runTest(testDispatcher) {
            val ethPartial =
                buildMultiTokenAddress(
                    Chain.Ethereum,
                    "0xeth",
                    nativeFiat = BigDecimal("10"),
                    tokenFiat = null,
                )

            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddressBalances("vault-1") } returns
                flowOf(AddressBalancesUpdate(listOf(ethPartial), isComplete = true))
            stubBalanceMappers()

            val vm = createViewModel()
            advanceUntilIdle()

            // The pending ERC-20 does not blank the row; it shows the resolved native $10.
            vm.uiState.value.accounts.first { it.model.chain == Chain.Ethereum }.fiatAmount shouldBe
                "$10"
            // And the row equals its contribution to the headline total.
            vm.uiState.value.totalFiatValue shouldBe "$10"
        }

    /**
     * Regression for #4768: switching vaults must not leak the previous vault's total or rows. When
     * vault-2's first emission resolves nothing, the total/row must reset rather than carrying
     * vault-1's $10 forward via the retain logic.
     */
    @Test
    fun `switching vault clears the previous vault total and rows`() =
        runTest(testDispatcher) {
            // Same chain + address in both vaults so their retainKey collides — only the
            // vault-change reset (not a key mismatch) can stop vault-1's $10 from leaking through.
            val vault1Eth = buildTestAddress(Chain.Ethereum, "0xeth", fiat = BigDecimal("10"))
            val vault2EthPending = buildTestAddress(Chain.Ethereum, "0xeth", fiat = null)

            every { lastOpenedVaultRepository.lastOpenedVaultId } returns
                flowOf("vault-1", "vault-2")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "One")
            coEvery { vaultRepository.get("vault-2") } returns Vault(id = "vault-2", name = "Two")
            every { accountsRepository.loadAddressBalances("vault-1") } returns
                flowOf(AddressBalancesUpdate(listOf(vault1Eth), isComplete = true))
            every { accountsRepository.loadAddressBalances("vault-2") } returns
                flowOf(AddressBalancesUpdate(listOf(vault2EthPending), isComplete = true))
            stubBalanceMappers()

            val vm = createViewModel()
            advanceUntilIdle()

            // vault-2 resolved nothing — neither vault-1's $10 total nor its row may leak through.
            vm.uiState.value.totalFiatValue shouldBe null
            vm.uiState.value.accounts.none { it.fiatAmount == "$10" }.shouldBeTrue()
        }

    /**
     * Regression for #4768: a progressive snapshot can re-emit an address with no accounts. Retain
     * must fall back to the cached accounts for that cycle rather than blanking the row/total.
     */
    @Test
    fun `row keeps cached value when an address re-emits with empty accounts`() =
        runTest(testDispatcher) {
            val ethResolved = buildTestAddress(Chain.Ethereum, "0xeth", fiat = BigDecimal("10"))
            val ethEmpty =
                Address(chain = Chain.Ethereum, address = "0xeth", accounts = emptyList())

            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddressBalances("vault-1") } returnsMany
                listOf(
                    flowOf(AddressBalancesUpdate(listOf(ethResolved), isComplete = true)),
                    flowOf(AddressBalancesUpdate(listOf(ethEmpty), isComplete = true)),
                )
            stubBalanceMappers()

            val vm = createViewModel()
            advanceUntilIdle()
            vm.uiState.value.totalFiatValue shouldBe "$10"

            vm.refreshData()
            advanceUntilIdle()

            // The empty re-emission must not blank the row or the total.
            vm.uiState.value.accounts.first { it.model.chain == Chain.Ethereum }.fiatAmount shouldBe
                "$10"
            vm.uiState.value.totalFiatValue shouldBe "$10"
        }

    /**
     * The DeFi list has to be ordered by the fiat each row paints. Dedo's report — Circle Yield
     * $0.00, TerraClassic $4.24, Tron $1.75, THORChain $0.61, Solana $11.03, Maya $0.00, Terra
     * $0.00 — left the largest row fifth, behind a provider row showing nothing, because the sort
     * key was the strict all-or-nothing fold: one account still resolving scored the whole row
     * null, and null is the top of the list.
     */
    @Test
    fun `DeFi rows are ordered by the fiat each row shows`() =
        runTest(testDispatcher) {
            // The provider row paints $0.00 off its resolved native account while a second one is
            // still pending — the shape that lifted it above every funded chain.
            val circle =
                buildMultiTokenAddress(
                    Chain.Ethereum,
                    "0xeth",
                    nativeFiat = BigDecimal.ZERO,
                    tokenFiat = null,
                )
            stubDeFi(
                listOf(
                    circle,
                    buildTestAddress(Chain.TerraClassic, "lunc", BigDecimal("4.24")),
                    buildTestAddress(Chain.Tron, "tron", BigDecimal("1.75")),
                    buildTestAddress(Chain.ThorChain, "thor", BigDecimal("0.61")),
                    buildTestAddress(Chain.Solana, "sol", BigDecimal("11.03")),
                    buildTestAddress(Chain.MayaChain, "maya", BigDecimal.ZERO),
                    buildTestAddress(Chain.Terra, "luna", BigDecimal.ZERO),
                )
            )
            stubBalanceMappers()

            val vm = createViewModel()
            advanceUntilIdle()

            vm.deFiChains() shouldBe
                listOf(
                    Chain.Solana,
                    Chain.TerraClassic,
                    Chain.Tron,
                    Chain.ThorChain,
                    // The zeros tie on value and fall back to the chain name.
                    Chain.Ethereum,
                    Chain.MayaChain,
                    Chain.Terra,
                )
        }

    /**
     * A row with nothing resolved yet renders no figure at all, so it belongs under the rows that
     * do — the strict fold scored it null and put it first.
     */
    @Test
    fun `a DeFi row with nothing resolved yet sits below the funded ones`() =
        runTest(testDispatcher) {
            stubDeFi(
                listOf(
                    buildTestAddress(Chain.Tron, "tron", fiat = null),
                    buildTestAddress(Chain.Solana, "sol", BigDecimal("5")),
                )
            )
            stubBalanceMappers()

            val vm = createViewModel()
            advanceUntilIdle()

            vm.deFiChains() shouldBe listOf(Chain.Solana, Chain.Tron)
        }

    /**
     * Rows restored from the last-known snapshot have to be ordered by what they end up showing.
     * Sorting the incoming list instead ranked both chains on an emission that carried no fiat at
     * all, which collapsed them onto the chain-name tie-break and dropped the $20 row under the $5
     * one.
     */
    @Test
    fun `DeFi rows restored from the cache are ordered by the value they show`() =
        runTest(testDispatcher) {
            val deFiAddresses = MutableSharedFlow<List<Address>>()
            stubDeFi(emptyList(), chains = setOf(Chain.Terra, Chain.Solana))
            coEvery { accountsRepository.loadDeFiAddresses("vault-1", any()) } returns deFiAddresses
            stubBalanceMappers()

            val vm = createViewModel()
            advanceUntilIdle()

            deFiAddresses.emit(
                listOf(
                    buildTestAddress(Chain.Solana, "sol", BigDecimal("5")),
                    buildTestAddress(Chain.Terra, "luna", BigDecimal("20")),
                )
            )
            advanceUntilIdle()

            vm.deFiChains() shouldBe listOf(Chain.Terra, Chain.Solana)

            // Both chains come back mid-refetch with no fiat; the merge restores $20 and $5, and
            // the order has to follow those restored figures rather than the empty emission.
            deFiAddresses.emit(
                listOf(
                    buildTestAddress(Chain.Solana, "sol", fiat = null),
                    buildTestAddress(Chain.Terra, "luna", fiat = null),
                )
            )
            advanceUntilIdle()

            vm.deFiChains() shouldBe listOf(Chain.Terra, Chain.Solana)
            vm.uiState.value.defiAccounts.first().fiatAmount shouldBe "$20"
        }

    /**
     * The DeFi ordering is a DeFi-only change. This pins the wallet list to the order it has today
     * — its own strict fold, pending chains first — so the shared helper is not "fixed" out from
     * under it.
     */
    @Test
    fun `the wallet list order is left alone`() =
        runTest(testDispatcher) {
            val eth = buildTestAddress(Chain.Ethereum, "0xeth", BigDecimal("10"))
            val solPending = buildTestAddress(Chain.Solana, "sol", fiat = null)

            every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
            coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
            every { accountsRepository.loadAddressBalances("vault-1") } returns
                flowOf(AddressBalancesUpdate(listOf(eth, solPending), isComplete = true))
            stubBalanceMappers()

            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.accounts.map { it.model.chain } shouldBe
                listOf(Chain.Solana, Chain.Ethereum)
        }

    private fun VaultAccountsViewModel.deFiChains(): List<Chain> =
        uiState.value.defiAccounts.map { it.model.chain }

    /** Serves [addresses] as the vault's DeFi list, with every chain in it switched on. */
    private fun stubDeFi(addresses: List<Address>, chains: Set<Chain>? = null) {
        every { lastOpenedVaultRepository.lastOpenedVaultId } returns flowOf("vault-1")
        coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")
        coEvery { accountsRepository.loadDeFiAddresses("vault-1", any()) } returns flowOf(addresses)
        every { defaultDeFiChainsRepository.getDefaultChains("vault-1") } returns
            flowOf(chains ?: addresses.mapTo(mutableSetOf()) { it.chain })
        // The Ethereum row is only surfaced for vaults that already opened a Circle account.
        coEvery { hasCircleAccount("vault-1") } returns true
    }

    private fun VaultAccountsViewModel.solanaRow(): AccountUiModel =
        uiState.value.accounts.first { it.model.chain == Chain.Solana }

    /**
     * Maps each [Address] to a UiModel using the same lenient per-row fold as production
     * (`calculateAccountsPartialFiatValue`): fiat is null only when nothing in the address has
     * resolved, and a partially-resolved address sums its resolved tokens (#4768).
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
                        addr.accounts.calculateAccountsPartialFiatValue()?.let {
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

    /**
     * Builds an [Address] with a resolved native account plus a second (ERC-20-style) token whose
     * fiat may be pending, to exercise the lenient per-row fold (#4768).
     */
    private fun buildMultiTokenAddress(
        chain: Chain,
        address: String,
        nativeFiat: BigDecimal,
        tokenFiat: BigDecimal?,
    ): Address {
        fun coin(ticker: String, native: Boolean) =
            Coin(
                chain = chain,
                ticker = ticker,
                logo = "",
                address = address,
                decimal = 18,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = if (native) "" else "0xtoken",
                isNativeToken = native,
            )

        fun account(coin: Coin, fiat: BigDecimal?) =
            Account(
                token = coin,
                tokenValue =
                    fiat?.let { TokenValue(value = BigInteger.ONE, unit = coin.ticker, 18) },
                fiatValue = fiat?.let { FiatValue(value = it, currency = "USD") },
                price = fiat?.let { FiatValue(value = it, currency = "USD") },
            )

        return Address(
            chain = chain,
            address = address,
            accounts =
                listOf(
                    account(coin(chain.feeUnit, native = true), nativeFiat),
                    account(coin("TKN", native = false), tokenFiat),
                ),
        )
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
