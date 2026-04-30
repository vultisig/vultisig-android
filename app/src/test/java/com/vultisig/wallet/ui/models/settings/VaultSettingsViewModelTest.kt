@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.settings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsItem
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsViewModel
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [VaultSettingsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VaultSettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var navigator: Navigator<Destination>
    private lateinit var isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase
    private lateinit var vaultRepository: VaultRepository
    private lateinit var vaultPasswordRepository: VaultPasswordRepository
    private lateinit var vaultDataStoreRepository: VaultDataStoreRepository
    private lateinit var vultiSignerRepository: VultiSignerRepository
    private lateinit var snackbarFlow: SnackbarFlow

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.VaultSettings>() } returns
            Route.VaultSettings(vaultId = VAULT_ID)
        context = mockk(relaxed = true)
        navigator = mockk(relaxed = true)
        isVaultHasFastSignById = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        vaultPasswordRepository = mockk(relaxed = true)
        vaultDataStoreRepository = mockk(relaxed = true)
        vultiSignerRepository = mockk(relaxed = true)
        snackbarFlow = mockk(relaxed = true)
        // Function-type-interface mocks need an explicit Boolean stub; otherwise relaxed mode
        // returns a generic Object that fails the implicit cast inside the VM.
        coEvery { isVaultHasFastSignById(any()) } returns false
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        VaultSettingsViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            isVaultHasFastSignById = isVaultHasFastSignById,
            vaultRepository = vaultRepository,
            context = context,
            vaultPasswordRepository = vaultPasswordRepository,
            vaultDataStoreRepository = vaultDataStoreRepository,
            vultiSignerRepository = vultiSignerRepository,
            snackbarFlow = snackbarFlow,
        )

    /** Verifies clicking Advanced sets isAdvanceSetting to true. */
    @Test
    fun `clicking Advanced sets isAdvanceSetting to true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Advanced)
            assertTrue(vm.uiModel.value.isAdvanceSetting)
        }

    /** Verifies onBackClick when isAdvanceSetting is true resets it to false. */
    @Test
    fun `onBackClick when isAdvanceSetting is true resets it to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Advanced)
            vm.onBackClick()
            assertFalse(vm.uiModel.value.isAdvanceSetting)
        }

    /** Verifies onBackClick when not in advanced mode navigates back. */
    @Test
    fun `onBackClick when not in advanced mode navigates back`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onBackClick()
            coVerify { navigator.navigate(Destination.Back) }
        }

    /** Verifies onDismissBackupVaultBottomSheet hides the sheet after it was opened. */
    @Test
    fun `onDismissBackupVaultBottomSheet hides the sheet after it was opened`() =
        runTest(testDispatcher) {
            // hasFastSign requires both: isVaultHasFastSignById = true AND vault has 2 signers.
            coEvery { isVaultHasFastSignById(VAULT_ID) } returns true
            coEvery { vaultRepository.get(VAULT_ID) } returns
                Vault(
                    id = VAULT_ID,
                    name = "Test",
                    libType = SigningLibType.DKLS,
                    signers = listOf("p1", "p2"),
                )
            val vm = createViewModel()

            vm.onSettingsItemClick(VaultSettingsItem.BackupVaultShare)
            assertTrue(vm.uiModel.value.isBackupVaultBottomSheetVisible)

            vm.onDismissBackupVaultBottomSheet()
            assertFalse(vm.uiModel.value.isBackupVaultBottomSheetVisible)
        }

    /** Verifies onDismissBiometricFastSignBottomSheet hides biometric sheet after it was opened. */
    @Test
    fun `onDismissBiometricFastSignBottomSheet hides biometric sheet after it was opened`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.onSettingsItemClick(
                VaultSettingsItem.BiometricFastSign(isEnabled = false, isBiometricEnabled = false)
            )
            assertTrue(vm.uiModel.value.isBiometricFastSignBottomSheetVisible)

            vm.onDismissBiometricFastSignBottomSheet()
            assertFalse(vm.uiModel.value.isBiometricFastSignBottomSheetVisible)
        }

    /** Verifies togglePasswordVisibility flips isPasswordVisible to true and back to false. */
    @Test
    fun `togglePasswordVisibility flips isPasswordVisible`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            // The default value is false; flipping should show, flipping again should hide.
            assertFalse(vm.uiModel.value.biometricsEnableUiModel.isPasswordVisible)

            vm.togglePasswordVisibility()
            assertTrue(vm.uiModel.value.biometricsEnableUiModel.isPasswordVisible)

            vm.togglePasswordVisibility()
            assertFalse(vm.uiModel.value.biometricsEnableUiModel.isPasswordVisible)
        }

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
