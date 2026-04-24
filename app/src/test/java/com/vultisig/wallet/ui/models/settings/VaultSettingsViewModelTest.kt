@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.settings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
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
    }

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

    @Test
    fun `clicking Advanced sets isAdvanceSetting to true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Advanced)
            assertTrue(vm.uiModel.value.isAdvanceSetting)
        }

    @Test
    fun `onBackClick when isAdvanceSetting is true resets it to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Advanced)
            vm.onBackClick()
            assertFalse(vm.uiModel.value.isAdvanceSetting)
        }

    @Test
    fun `onBackClick when not in advanced mode navigates back`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onBackClick()
            coVerify { navigator.navigate(Destination.Back) }
        }

    @Test
    fun `onDismissBackupVaultBottomSheet hides the sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onDismissBackupVaultBottomSheet()
            assertFalse(vm.uiModel.value.isBackupVaultBottomSheetVisible)
        }

    @Test
    fun `onDismissBiometricFastSignBottomSheet hides biometric sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onDismissBiometricFastSignBottomSheet()
            assertFalse(vm.uiModel.value.isBiometricFastSignBottomSheetVisible)
        }

    @Test
    fun `togglePasswordVisibility toggles isPasswordVisible`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val before = vm.uiModel.value.biometricsEnableUiModel.isPasswordVisible
            vm.togglePasswordVisibility()
            assertTrue(vm.uiModel.value.biometricsEnableUiModel.isPasswordVisible != before)
        }

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
