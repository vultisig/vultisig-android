package com.vultisig.wallet.ui.screens.migration

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class MigrationOnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val vaultId = "vault-1"
    private val savedStateHandle = SavedStateHandle()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { savedStateHandle.toRoute<Route.Migration.Onboarding>() } returns
            Route.Migration.Onboarding(vaultId = vaultId)
        navigator = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `secure vault upgrade routes straight to peer discovery without showing sheet`() = runTest {
        val vault = secureVault()
        coEvery { vaultRepository.get(vaultId) } returns vault

        val vm = createViewModel()
        vm.upgrade()

        coVerify(exactly = 1) {
            navigator.route(
                Route.Keygen.PeerDiscovery(
                    action = TssAction.Migrate,
                    vaultName = vault.name,
                    vaultId = vaultId,
                )
            )
        }
        coVerify(exactly = 0) { navigator.route(match { it is Route.Migration.Password }) }
        assertFalse(vm.state.first().isServerShareLocationSheetVisible)
    }

    @Test
    fun `self-hosted server vault is treated as secure and skips the choice sheet`() = runTest {
        val vault =
            secureVault().copy(localPartyID = "Server-1", signers = listOf("Android-1", "Server-1"))
        coEvery { vaultRepository.get(vaultId) } returns vault

        val vm = createViewModel()
        vm.upgrade()

        coVerify(exactly = 1) {
            navigator.route(
                Route.Keygen.PeerDiscovery(
                    action = TssAction.Migrate,
                    vaultName = vault.name,
                    vaultId = vaultId,
                )
            )
        }
        assertFalse(vm.state.first().isServerShareLocationSheetVisible)
    }

    @Test
    fun `fast vault upgrade shows the server share location sheet instead of navigating`() =
        runTest {
            coEvery { vaultRepository.get(vaultId) } returns fastVault()

            val vm = createViewModel()
            vm.upgrade()

            assertTrue(vm.state.first().isServerShareLocationSheetVisible)
            coVerify(exactly = 0) {
                navigator.route(
                    match { it is Route.Migration.Password || it is Route.Keygen.PeerDiscovery }
                )
            }
        }

    @Test
    fun `choosing online VultiServer hides the sheet and routes to the password screen`() =
        runTest {
            coEvery { vaultRepository.get(vaultId) } returns fastVault()
            val vm = createViewModel()
            vm.upgrade()

            vm.continueWithOnlineVultiServer()

            assertFalse(vm.state.first().isServerShareLocationSheetVisible)
            coVerify(exactly = 1) { navigator.route(Route.Migration.Password(vaultId = vaultId)) }
            coVerify(exactly = 0) { navigator.route(match { it is Route.Keygen.PeerDiscovery }) }
        }

    @Test
    fun `choosing self-hosted server hides the sheet and routes to peer discovery for migrate`() =
        runTest {
            val vault = fastVault()
            coEvery { vaultRepository.get(vaultId) } returns vault
            val vm = createViewModel()
            vm.upgrade()

            vm.continueWithSelfHostedServer()

            assertFalse(vm.state.first().isServerShareLocationSheetVisible)
            coVerify(exactly = 1) {
                navigator.route(
                    Route.Keygen.PeerDiscovery(
                        action = TssAction.Migrate,
                        vaultName = vault.name,
                        vaultId = vaultId,
                    )
                )
            }
            coVerify(exactly = 0) { navigator.route(match { it is Route.Migration.Password }) }
        }

    @Test
    fun `choosing self-hosted server without an open sheet is a no-op`() = runTest {
        coEvery { vaultRepository.get(vaultId) } returns fastVault()
        val vm = createViewModel()

        vm.continueWithSelfHostedServer()

        coVerify(exactly = 0) {
            navigator.route(
                match { it is Route.Migration.Password || it is Route.Keygen.PeerDiscovery }
            )
        }
    }

    @Test
    fun `dismissing the sheet does not navigate anywhere`() = runTest {
        coEvery { vaultRepository.get(vaultId) } returns fastVault()
        val vm = createViewModel()
        vm.upgrade()

        vm.dismissServerShareLocationSheet()

        assertFalse(vm.state.first().isServerShareLocationSheetVisible)
        coVerify(exactly = 0) {
            navigator.route(
                match { it is Route.Migration.Password || it is Route.Keygen.PeerDiscovery }
            )
        }
    }

    @Test
    fun `initial vault load classifies fast vault as Fast in ui state`() = runTest {
        coEvery { vaultRepository.get(vaultId) } returns fastVault()
        val vm = createViewModel()
        assertEquals(Route.VaultInfo.VaultType.Fast, vm.state.first().vaultType)
    }

    @Test
    fun `initial vault load classifies secure vault as Secure in ui state`() = runTest {
        coEvery { vaultRepository.get(vaultId) } returns secureVault()
        val vm = createViewModel()
        assertEquals(Route.VaultInfo.VaultType.Secure, vm.state.first().vaultType)
    }

    @Test
    fun `back delegates to navigator with Back destination`() = runTest {
        coEvery { vaultRepository.get(vaultId) } returns secureVault()
        val vm = createViewModel()

        vm.back()

        coVerify(exactly = 1) { navigator.navigate(Destination.Back) }
    }

    private fun createViewModel(): MigrationOnboardingViewModel =
        MigrationOnboardingViewModel(
            savedStateHandle = savedStateHandle,
            navigator = navigator,
            vaultRepository = vaultRepository,
        )

    private fun fastVault(): Vault =
        Vault(
            id = vaultId,
            name = "Fast Vault",
            localPartyID = "Android-1",
            signers = listOf("Android-1", "Server-1"),
            libType = SigningLibType.GG20,
        )

    private fun secureVault(): Vault =
        Vault(
            id = vaultId,
            name = "Secure Vault",
            localPartyID = "Android-1",
            signers = listOf("Android-1", "iPhone-1"),
            libType = SigningLibType.GG20,
        )
}
