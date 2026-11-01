@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.vault

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.data.repositories.ChainDashboardBottomBarVisibilityRepository
import com.vultisig.wallet.data.repositories.CryptoConnectionTypeRepository
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ChainDashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val vaultId = "vault-1"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel(route: ChainDashboardRoute): ChainDashboardViewModel {
        every { any<SavedStateHandle>().toRoute<Route.ChainDashboard>(any()) } returns
            Route.ChainDashboard(route = route)
        val cryptoConnectionTypeRepository =
            mockk<CryptoConnectionTypeRepository>(relaxed = true) {
                every { activeCryptoConnectionFlow } returns
                    MutableStateFlow(CryptoConnectionType.Defi)
            }
        val bottomBarVisibility =
            mockk<ChainDashboardBottomBarVisibilityRepository> {
                every { getBottomBarVisibility() } returns MutableStateFlow(true)
            }
        return ChainDashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            cryptoConnectionTypeRepository = cryptoConnectionTypeRepository,
            bottomBarVisibility = bottomBarVisibility,
            navigator = mockk(relaxed = true),
        )
    }

    @Test
    fun `Ethereum token list has no DeFi page`() {
        val vm =
            createViewModel(
                ChainDashboardRoute.Wallet(vaultId = vaultId, chainId = Chain.Ethereum.id)
            )

        vm.uiState.value.availableCryptoTypes shouldBe ONLY_WALLET
        vm.uiState.value.routes shouldBe
            mapOf(
                CryptoConnectionType.Wallet to
                    ChainDashboardRoute.Wallet(vaultId = vaultId, chainId = Chain.Ethereum.id)
            )
    }

    @Test
    fun `Circle entry keeps the Circle page next to the Ethereum wallet`() {
        val vm = createViewModel(ChainDashboardRoute.PositionCircle(vaultId = vaultId))

        vm.uiState.value.availableCryptoTypes shouldBe BOTH_CRYPTO_CONNECTION_TYPES
        vm.uiState.value.routes shouldBe
            mapOf(
                CryptoConnectionType.Wallet to
                    ChainDashboardRoute.Wallet(vaultId = vaultId, chainId = Chain.Ethereum.id),
                CryptoConnectionType.Defi to ChainDashboardRoute.PositionCircle(vaultId = vaultId),
            )
    }

    @Test
    fun `chains with their own DeFi page still page into it`() {
        val vm =
            createViewModel(ChainDashboardRoute.Wallet(vaultId = vaultId, chainId = Chain.Tron.id))

        vm.uiState.value.routes[CryptoConnectionType.Defi] shouldBe
            ChainDashboardRoute.PositionTron(vaultId = vaultId)
    }
}
