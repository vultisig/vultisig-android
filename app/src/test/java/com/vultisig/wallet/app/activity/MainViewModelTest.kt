@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.app.activity

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetDirectionByQrCodeUseCase
import com.vultisig.wallet.data.usecases.GetKeysignTransactionSummaryUseCase
import com.vultisig.wallet.data.usecases.HandleTonConnectUriUseCase
import com.vultisig.wallet.data.usecases.InitializeThorChainNetworkIdUseCase
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.NetworkUtils
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val context: Context = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val snackbarFlow: SnackbarFlow = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val appUpdateManager: AppUpdateManager = mockk(relaxed = true)
    private val initializeThorChainNetworkId: InitializeThorChainNetworkIdUseCase =
        mockk(relaxed = true)
    private val getDirectionByQrCodeUseCase: GetDirectionByQrCodeUseCase = mockk(relaxed = true)
    private val handleTonConnectUri: HandleTonConnectUriUseCase = mockk(relaxed = true)
    private val getKeysignTransactionSummary: GetKeysignTransactionSummaryUseCase =
        mockk(relaxed = true)
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper =
        mockk(relaxed = true)
    private val networkUtils: NetworkUtils = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { networkUtils.observeConnectivityAsFlow() } returns emptyFlow()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        MainViewModel(
            context = context,
            navigator = navigator,
            snackbarFlow = snackbarFlow,
            vaultRepository = vaultRepository,
            appUpdateManager = appUpdateManager,
            initializeThorChainNetworkId = initializeThorChainNetworkId,
            getDirectionByQrCodeUseCase = getDirectionByQrCodeUseCase,
            handleTonConnectUri = handleTonConnectUri,
            getKeysignTransactionSummary = getKeysignTransactionSummary,
            mapTokenValueToStringWithUnit = mapTokenValueToStringWithUnit,
            networkUtils = networkUtils,
        )

    @Test
    fun `hasVaults returns true - startDestination is Home and isLoading clears`() =
        runTest(dispatcher) {
            coEvery { vaultRepository.hasVaults() } returns true

            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(vm.startDestination.value is Route.Home)
            assertFalse(vm.isLoading.value)
        }

    @Test
    fun `hasVaults returns false - startDestination is AddVault and isLoading clears`() =
        runTest(dispatcher) {
            coEvery { vaultRepository.hasVaults() } returns false

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(Route.AddVault, vm.startDestination.value)
            assertFalse(vm.isLoading.value)
        }

    @Test
    fun `hasVaults suspends past timeout - falls back to AddVault and isLoading clears`() =
        runTest(dispatcher) {
            val neverCompletes = CompletableDeferred<Boolean>()
            coEvery { vaultRepository.hasVaults() } coAnswers { neverCompletes.await() }

            val vm = createViewModel()
            advanceTimeBy(6.seconds)
            advanceUntilIdle()

            assertEquals(Route.AddVault, vm.startDestination.value)
            assertFalse(vm.isLoading.value)
        }

    @Test
    fun `hasVaults throws - falls back to AddVault and isLoading clears`() =
        runTest(dispatcher) {
            coEvery { vaultRepository.hasVaults() } throws IllegalStateException("db closed")

            val vm = createViewModel()
            advanceUntilIdle()

            assertEquals(Route.AddVault, vm.startDestination.value)
            assertFalse(vm.isLoading.value)
        }

    @Test
    fun `isLoading starts true before init coroutine runs`() =
        runTest(dispatcher) {
            coEvery { vaultRepository.hasVaults() } returns true

            val vm = createViewModel()
            // no advance — init coroutine has not executed yet

            assertTrue(vm.isLoading.value)
        }
}
