@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.app.activity

import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateManager
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetDirectionByQrCodeUseCase
import com.vultisig.wallet.data.usecases.GetKeysignTransactionSummaryUseCase
import com.vultisig.wallet.data.usecases.InitializeThorChainNetworkIdUseCase
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.NetworkUtils
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val snackbarFlow: SnackbarFlow = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val appUpdateManager: AppUpdateManager = mockk(relaxed = true)
    private val initializeThorChainNetworkId: InitializeThorChainNetworkIdUseCase =
        mockk(relaxed = true)
    private val getDirectionByQrCodeUseCase: GetDirectionByQrCodeUseCase = mockk(relaxed = true)
    private val getKeysignTransactionSummary: GetKeysignTransactionSummaryUseCase =
        mockk(relaxed = true)
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper =
        mockk(relaxed = true)
    private val networkUtils: NetworkUtils = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { networkUtils.observeConnectivityAsFlow() } returns emptyFlow()
        // DeepLinkHelper decodes query params via android.net.Uri, which is unavailable in
        // plain JVM unit tests; pass values through unchanged.
        mockkStatic(Uri::class)
        every { Uri.decode(any()) } answers { firstArg() }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Uri::class)
    }

    private fun createViewModel() =
        MainViewModel(
            navigator = navigator,
            snackbarFlow = snackbarFlow,
            vaultRepository = vaultRepository,
            appUpdateManager = appUpdateManager,
            initializeThorChainNetworkId = initializeThorChainNetworkId,
            getDirectionByQrCodeUseCase = getDirectionByQrCodeUseCase,
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

            vm.startDestination.value.shouldBeInstanceOf<Route.Home>()
            vm.isLoading.value.shouldBeFalse()
        }

    @Test
    fun `hasVaults returns false - startDestination is AddVault and isLoading clears`() =
        runTest(dispatcher) {
            coEvery { vaultRepository.hasVaults() } returns false

            val vm = createViewModel()
            advanceUntilIdle()

            vm.startDestination.value shouldBe Route.AddVault
            vm.isLoading.value.shouldBeFalse()
        }

    @Test
    fun `hasVaults suspends past timeout - falls back to AddVault and isLoading clears`() =
        runTest(dispatcher) {
            val neverCompletes = CompletableDeferred<Boolean>()
            coEvery { vaultRepository.hasVaults() } coAnswers { neverCompletes.await() }

            val vm = createViewModel()
            advanceTimeBy(6.seconds)
            advanceUntilIdle()

            vm.startDestination.value shouldBe Route.AddVault
            vm.isLoading.value.shouldBeFalse()
        }

    @Test
    fun `hasVaults throws - falls back to AddVault and isLoading clears`() =
        runTest(dispatcher) {
            coEvery { vaultRepository.hasVaults() } throws IllegalStateException("db closed")

            val vm = createViewModel()
            advanceUntilIdle()

            vm.startDestination.value shouldBe Route.AddVault
            vm.isLoading.value.shouldBeFalse()
        }

    @Test
    fun `isLoading starts true before init coroutine runs`() =
        runTest(dispatcher) {
            coEvery { vaultRepository.hasVaults() } returns true

            val vm = createViewModel()
            // no advance — init coroutine has not executed yet

            vm.isLoading.value.shouldBeTrue()
        }

    @Test
    fun `clearForegroundNotification clears banner state`() =
        runTest(dispatcher) {
            coEvery { vaultRepository.hasVaults() } returns true
            coEvery { vaultRepository.getByEcdsa(any()) } returns null
            coEvery { getKeysignTransactionSummary.invoke(any()) } returns null

            val vm = createViewModel()
            advanceUntilIdle()

            vm.onForegroundPushReceived("vultisig://qr-payload")
            advanceUntilIdle()

            vm.foregroundNotification.value.shouldNotBeNull()

            vm.clearForegroundNotification()

            vm.foregroundNotification.value.shouldBeNull()
        }

    @Test
    fun `onForegroundBannerTapped navigates to Join and keeps banner until observer clears it`() =
        runTest(dispatcher) {
            val vault: Vault = mockk(relaxed = true)
            coEvery { vaultRepository.hasVaults() } returns true
            coEvery { vaultRepository.getByEcdsa(any()) } returns vault
            coEvery { getKeysignTransactionSummary.invoke(any()) } returns null
            coEvery { getDirectionByQrCodeUseCase(any(), any()) } returns
                Route.Keysign.Join(vaultId = "vault-id", qr = "qr")

            val vm = createViewModel()
            vm.onNavigationReady()
            advanceUntilIdle()

            vm.onForegroundPushReceived("vultisig://join?vault=ecdsa-key")
            advanceUntilIdle()
            vm.foregroundNotification.value.shouldNotBeNull()

            vm.onForegroundBannerTapped()
            advanceUntilIdle()

            // Navigation to the Join screen must force a FRESH entry by popping any prior
            // Keysign.Join entry inclusive. launchSingleTop = true would otherwise reuse a
            // stale (e.g. finished/polling) join entry and its ViewModel, so the new request
            // would never start — exactly the reported failure (issue #4623).
            coVerify {
                navigator.route(
                    ofType(Route.Keysign.Join::class),
                    NavigationOptions(popUpToRoute = Route.Keysign.Join::class, inclusive = true),
                )
            }
            // The ViewModel must NOT clear the banner itself for a Join route. The route-change
            // observer in MainActivityContent clears it once the destination is actually reached,
            // so the request stays actionable even if navigation is deferred inside a nested flow.
            vm.foregroundNotification.value.shouldNotBeNull()
        }

    @Test
    fun `onForegroundBannerTapped to Keygen Join pops prior join entry inclusive`() =
        runTest(dispatcher) {
            val vault: Vault = mockk(relaxed = true)
            coEvery { vaultRepository.hasVaults() } returns true
            coEvery { vaultRepository.getByEcdsa(any()) } returns vault
            coEvery { getKeysignTransactionSummary.invoke(any()) } returns null
            coEvery { getDirectionByQrCodeUseCase(any(), any()) } returns
                Route.Keygen.Join(qr = "qr")

            val vm = createViewModel()
            vm.onNavigationReady()
            advanceUntilIdle()

            vm.onForegroundPushReceived("vultisig://join?vault=ecdsa-key")
            advanceUntilIdle()

            vm.onForegroundBannerTapped()
            advanceUntilIdle()

            coVerify {
                navigator.route(
                    ofType(Route.Keygen.Join::class),
                    NavigationOptions(popUpToRoute = Route.Keygen.Join::class, inclusive = true),
                )
            }
            vm.foregroundNotification.value.shouldNotBeNull()
        }

    @Test
    fun `onForegroundBannerTapped to a non-join route navigates once and clears banner`() =
        runTest(dispatcher) {
            val vault: Vault = mockk(relaxed = true)
            coEvery { vaultRepository.hasVaults() } returns true
            coEvery { vaultRepository.getByEcdsa(any()) } returns vault
            coEvery { getKeysignTransactionSummary.invoke(any()) } returns null
            // Send never reaches the route-change observer, so the ViewModel clears the banner
            // itself and routes without pop options.
            coEvery { getDirectionByQrCodeUseCase(any(), any()) } returns
                Route.Send(vaultId = "vault-id", address = "addr")

            val vm = createViewModel()
            vm.onNavigationReady()
            advanceUntilIdle()

            vm.onForegroundPushReceived("vultisig://join?vault=ecdsa-key")
            advanceUntilIdle()

            vm.onForegroundBannerTapped()
            advanceUntilIdle()

            coVerify { navigator.route(ofType(Route.Send::class)) }
            vm.foregroundNotification.value.shouldBeNull()
        }

    @Test
    fun `onForegroundBannerTapped with missing vault clears banner and does not navigate`() =
        runTest(dispatcher) {
            coEvery { vaultRepository.hasVaults() } returns true
            coEvery { vaultRepository.getByEcdsa(any()) } returns null
            coEvery { getKeysignTransactionSummary.invoke(any()) } returns null

            val vm = createViewModel()
            vm.onNavigationReady()
            advanceUntilIdle()

            // No "vault" param -> vault lookup yields null. The route observer never fires for
            // this dead-end branch, so the ViewModel must clear the banner itself instead of
            // leaving it stranded on screen.
            vm.onForegroundPushReceived("vultisig://qr-payload")
            advanceUntilIdle()
            vm.foregroundNotification.value.shouldNotBeNull()

            vm.onForegroundBannerTapped()
            advanceUntilIdle()

            vm.foregroundNotification.value.shouldBeNull()
            coVerify(exactly = 0) { navigator.route(any()) }
        }
}
