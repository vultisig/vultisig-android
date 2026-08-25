@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainDashboardBottomBarVisibilityRepository
import com.vultisig.wallet.data.repositories.DismissPolicy
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.PromoBanner
import com.vultisig.wallet.data.repositories.PromoBannerDismissalRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ChainTokensViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var fiatValueToStringMapper: FiatValueToStringMapper
    private lateinit var tokenValueToStringWithUnitMapper: TokenValueToStringWithUnitMapper
    private lateinit var discoverTokenUseCase: DiscoverTokenUseCase
    private lateinit var explorerLinkRepository: ExplorerLinkRepository
    private lateinit var accountsRepository: AccountsRepository
    private lateinit var balanceVisibilityRepository: BalanceVisibilityRepository
    private lateinit var bottomBarVisibility: ChainDashboardBottomBarVisibilityRepository
    private lateinit var vaultRepository: VaultRepository
    private lateinit var requestResultRepository: RequestResultRepository
    private lateinit var balanceRepository: BalanceRepository
    private lateinit var promoBannerDismissalRepository: FakePromoBannerDismissalRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        navigator = mockk(relaxed = true)
        fiatValueToStringMapper = mockk(relaxed = true)
        tokenValueToStringWithUnitMapper = mockk(relaxed = true)
        discoverTokenUseCase = mockk(relaxed = true)
        explorerLinkRepository = mockk(relaxed = true)
        accountsRepository = mockk(relaxed = true)
        balanceVisibilityRepository = mockk(relaxed = true)
        bottomBarVisibility = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)
        balanceRepository = mockk(relaxed = true)
        promoBannerDismissalRepository = FakePromoBannerDismissalRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        ChainTokensViewModel(
            navigator = navigator,
            fiatValueToStringMapper = fiatValueToStringMapper,
            mapTokenValueToStringWithUnitMapper = tokenValueToStringWithUnitMapper,
            discoverTokenUseCase = discoverTokenUseCase,
            explorerLinkRepository = explorerLinkRepository,
            accountsRepository = accountsRepository,
            balanceVisibilityRepository = balanceVisibilityRepository,
            bottomBarVisibility = bottomBarVisibility,
            vaultRepository = vaultRepository,
            requestResultRepository = requestResultRepository,
            balanceRepository = balanceRepository,
            promoBannerDismissalRepository = promoBannerDismissalRepository,
        )

    @Test
    fun `showSearchBar sets isSearchMode to true`() {
        val vm = createViewModel()

        vm.showSearchBar()

        assertTrue(vm.uiState.value.isSearchMode)
    }

    @Test
    fun `hideSearchBar sets isSearchMode to false`() {
        val vm = createViewModel()
        vm.showSearchBar()

        vm.hideSearchBar()

        assertFalse(vm.uiState.value.isSearchMode)
    }

    @Test
    fun `isSearchMode is false by default`() {
        val vm = createViewModel()

        assertFalse(vm.uiState.value.isSearchMode)
    }

    @Test
    fun `showSearchBar then hideSearchBar toggles state correctly`() {
        val vm = createViewModel()

        assertFalse(vm.uiState.value.isSearchMode)

        vm.showSearchBar()
        assertTrue(vm.uiState.value.isSearchMode)

        vm.hideSearchBar()
        assertFalse(vm.uiState.value.isSearchMode)
    }

    @Test
    fun `the qbtc claim banner shows on bitcoin for a vault that can claim`() {
        val vm = createEligibleViewModel()

        assertTrue(vm.uiState.value.showQbtcClaimBanner)
    }

    @Test
    fun `closing the qbtc claim banner hides it straight away`() {
        val vm = createEligibleViewModel()

        vm.dismissQbtcClaimBanner()

        assertFalse(vm.uiState.value.showQbtcClaimBanner)
    }

    @Test
    fun `a qbtc claim banner closed earlier does not come back`() {
        promoBannerDismissalRepository.setDismissed(PromoBanner.ClaimQbtc)

        val vm = createEligibleViewModel()

        assertFalse(vm.uiState.value.showQbtcClaimBanner)
    }

    /** A view model on the Bitcoin screen of a DKLS vault — the state that surfaces the banner. */
    private fun createEligibleViewModel(): ChainTokensViewModel {
        // A relaxed mock answers this Unit-returning function type with an Object, which the call
        // site then fails to cast.
        every { discoverTokenUseCase(any(), any()) } returns Unit
        coEvery { vaultRepository.get(VAULT_ID) } returns
            Vault(id = VAULT_ID, name = "vault", libType = SigningLibType.DKLS)

        return createViewModel().apply { initData(vaultId = VAULT_ID, chainId = Chain.Bitcoin.raw) }
    }

    /**
     * Dismissals held in memory, so a write is observed by an in-flight read as it is in DataStore.
     */
    private class FakePromoBannerDismissalRepository : PromoBannerDismissalRepository {
        private val dismissed = MutableStateFlow(emptySet<PromoBanner>())

        fun setDismissed(banner: PromoBanner) = dismissed.update { it + banner }

        override fun isDismissed(banner: PromoBanner, policy: DismissPolicy): Flow<Boolean> =
            dismissed.map { banner in it }

        override suspend fun dismiss(banner: PromoBanner) = setDismissed(banner)
    }

    private companion object {
        const val VAULT_ID = "vault-id"
    }
}
