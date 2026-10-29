@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.home

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositDeeplinkData
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetOrderedVaults
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VaultListViewModelDepositDeeplinkTest {

    private val dispatcher = StandardTestDispatcher()
    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val snackbarFlow: SnackbarFlow = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val getOrderedVaults: GetOrderedVaults = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getOrderedVaults(any()) } returns emptyFlow()
        every { vaultRepository.getAllAsFlow() } returns emptyFlow()
    }

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() =
        VaultListViewModel(
            vaultOrderRepository = mockk(relaxed = true),
            folderRepository = mockk(relaxed = true),
            folderOrderRepository = mockk(relaxed = true),
            getOrderedVaults = getOrderedVaults,
            navigator = navigator,
            lastOpenedVaultRepository = mockk(relaxed = true),
            vaultRepository = vaultRepository,
            vaultAndBalanceUseCase = mockk(relaxed = true),
            fiatValueToStringMapper = mockk(relaxed = true),
            snackbarFlow = snackbarFlow,
        )

    @Test
    fun `picking a vault that holds the coin opens the Custom deposit form prefilled`() =
        runTest(dispatcher) {
            givenVaultCoins(Coins.ThorChain.RUNE, Coins.ThorChain.TCY)
            val vm = createViewModel()
            vm.init(depositLink(assetTicker = "tcy", amount = "1e5"))

            vm.selectVault("vault-id")
            advanceUntilIdle()

            coVerify {
                navigator.route(
                    Route.Deposit(
                        vaultId = "vault-id",
                        chainId = "THORChain",
                        customTicker = "TCY",
                        customAmount = null,
                        customMemo = "TCY+",
                    )
                )
            }
        }

    @Test
    fun `picking a vault without the coin shows an error and stays put`() =
        runTest(dispatcher) {
            givenVaultCoins(Coins.ThorChain.RUNE)
            val vm = createViewModel()
            vm.init(depositLink(assetTicker = "TCY", amount = "0.01"))

            vm.selectVault("vault-id")
            advanceUntilIdle()

            coVerify { snackbarFlow.showMessage(any<UiText>(), SnackbarType.Error) }
            coVerify(exactly = 0) { navigator.route(any()) }
        }

    private fun givenVaultCoins(vararg coins: Coin) {
        val vault: Vault =
            mockk(relaxed = true) { every { this@mockk.coins } returns coins.toList() }
        coEvery { vaultRepository.get("vault-id") } returns vault
    }

    private fun depositLink(assetTicker: String?, amount: String?) =
        Route.VaultList.OpenType.DepositDeepLink(
            DepositDeeplinkData(
                assetChain = "THORChain",
                assetTicker = assetTicker,
                amount = amount,
                memo = "TCY+",
            )
        )
}
