@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.chaintokens.GetChainTokensUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class TokenSelectionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val requestResultRepository: RequestResultRepository = mockk(relaxed = true)
    private val enableTokenUseCase: EnableTokenUseCase = mockk(relaxed = true)
    private val getChainTokens: GetChainTokensUseCase = mockk(relaxed = true)

    private val vault = Vault(id = VAULT_ID, name = "Main")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.SelectTokens>() } returns
            Route.SelectTokens(vaultId = VAULT_ID, chainId = Chain.Arbitrum.id)
        coEvery { vaultRepository.get(VAULT_ID) } returns vault
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `a name finds both an enabled and a not-yet-enabled token`() =
        runTest(testDispatcher) {
            val usdc = Coins.Arbitrum.USDC
            val bridgedUsdc = Coins.Arbitrum.USDC_e
            val arb = Coins.Arbitrum.ARB
            every { vaultRepository.getEnabledTokens(VAULT_ID) } returns flowOf(listOf(usdc))
            every { getChainTokens(Chain.Arbitrum, vault) } returns
                flowOf(listOf(usdc, bridgedUsdc, arb))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.searchTextFieldState.setTextAndPlaceCursorAtEnd("usd coin")
            // Nothing composes in a JVM test, so the snapshot the edit lands in has to be
            // published by hand before `snapshotFlow` sees it.
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            vm.uiState.value.tokens.map { it.coin.id to it.isEnabled } shouldBe
                listOf(usdc.id to true, bridgedUsdc.id to false)
        }

    @Test
    fun `a blank query lists every token`() =
        runTest(testDispatcher) {
            val usdc = Coins.Arbitrum.USDC
            val arb = Coins.Arbitrum.ARB
            every { vaultRepository.getEnabledTokens(VAULT_ID) } returns flowOf(listOf(usdc))
            every { getChainTokens(Chain.Arbitrum, vault) } returns flowOf(listOf(usdc, arb))
            val vm = createViewModel()
            advanceUntilIdle()

            vm.uiState.value.tokens.map { it.coin.id } shouldBe listOf(usdc.id, arb.id)
        }

    private fun createViewModel() =
        TokenSelectionViewModel(
            savedStateHandle = mockk(relaxed = true),
            navigator = navigator,
            vaultRepository = vaultRepository,
            requestResultRepository = requestResultRepository,
            enableTokenUseCase = enableTokenUseCase,
            getChainTokens = getChainTokens,
        )

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
