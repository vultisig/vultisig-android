@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.HasCircleAccountUseCase
import com.vultisig.wallet.ui.models.mappers.ChainToDefiChainUiMapperImpl
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class DeFiChainSelectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var vaultRepository: VaultRepository
    private lateinit var defaultDeFiChainsRepository: DefaultDeFiChainsRepository
    private lateinit var hasCircleAccount: HasCircleAccountUseCase
    private lateinit var navigator: Navigator<Destination>
    private lateinit var requestResultRepository: RequestResultRepository

    private val vaultId = "vault-1"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        savedStateHandle = mockk()
        every { savedStateHandle.toRoute<Route.AddDeFiChainAccount>() } returns
            Route.AddDeFiChainAccount(vaultId = vaultId)
        vaultRepository = mockk()
        defaultDeFiChainsRepository = mockk(relaxed = true)
        hasCircleAccount = mockk()
        navigator = mockk(relaxed = true)
        requestResultRepository = mockk(relaxed = true)

        // Vault holding Ethereum (Circle) and ThorChain, both DeFi-supported.
        coEvery { vaultRepository.get(vaultId) } returns
            Vault(
                id = vaultId,
                name = "Test",
                coins = listOf(Coins.Ethereum.ETH, Coins.ThorChain.RUNE),
            )
        every { defaultDeFiChainsRepository.getDefaultChains(vaultId) } returns flowOf(emptySet())
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        DeFiChainSelectionViewModel(
            savedStateHandle = savedStateHandle,
            vaultRepository = vaultRepository,
            defaultDeFiChainsRepository = defaultDeFiChainsRepository,
            hasCircleAccount = hasCircleAccount,
            mapChainDefi = ChainToDefiChainUiMapperImpl(),
            navigator = navigator,
            requestResultRepository = requestResultRepository,
        )

    @Test
    fun `Circle entry is offered when the vault has a Circle account`() =
        runTest(testDispatcher) {
            coEvery { hasCircleAccount(vaultId) } returns true

            val vm = createViewModel()
            advanceUntilIdle()

            val chains = vm.uiState.value.defiChains.map { it.defiChain.chain }
            assertTrue(chains.contains(Chain.Ethereum))
            assertTrue(chains.contains(Chain.ThorChain))
        }

    @Test
    fun `Circle entry is hidden when the vault has no Circle account`() =
        runTest(testDispatcher) {
            coEvery { hasCircleAccount(vaultId) } returns false

            val vm = createViewModel()
            advanceUntilIdle()

            val chains = vm.uiState.value.defiChains.map { it.defiChain.chain }
            assertFalse(chains.contains(Chain.Ethereum))
            assertTrue(chains.contains(Chain.ThorChain))
        }
}
