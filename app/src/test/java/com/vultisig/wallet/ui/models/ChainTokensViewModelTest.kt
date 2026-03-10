@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainDashboardBottomBarVisibilityRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.mockk.mockk
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
