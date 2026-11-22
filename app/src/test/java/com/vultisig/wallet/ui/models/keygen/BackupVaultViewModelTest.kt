package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.BackupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
internal class BackupVaultViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    private fun createViewModel(vaultType: VaultType, action: TssAction): BackupVaultViewModel {
        every { any<SavedStateHandle>().toRoute<Route.BackupVault>(typeMap = any()) } returns
            Route.BackupVault(
                vaultId = "vault-1",
                vaultType = vaultType,
                action = action,
                passwordType = Route.BackupVault.BackupPasswordType.UserSelectionPassword,
            )
        coEvery { vaultRepository.get("vault-1") } returns null

        return BackupVaultViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            vaultRepository = vaultRepository,
        )
    }

    @Test
    fun `fast vault export requests a local password without offering an unencrypted backup`() =
        runTest(testDispatcher) {
            val vm = createViewModel(VaultType.Fast, TssAction.SingleKeygen)

            vm.backup()

            coVerify {
                navigator.route(
                    Route.BackupPassword(
                        vaultId = "vault-1",
                        backupType = BackupType.CurrentVault(
                            vaultType = VaultType.Fast,
                            action = TssAction.SingleKeygen,
                        ),
                    )
                )
            }
        }

    @Test
    fun `secure vault export preserves its backup options`() =
        runTest(testDispatcher) {
            val vm = createViewModel(VaultType.Secure, TssAction.KeyImport)

            vm.backup()

            coVerify {
                navigator.route(
                    Route.BackupPasswordRequest(
                        vaultId = "vault-1",
                        backupType = BackupType.CurrentVault(
                            vaultType = VaultType.Secure,
                            action = TssAction.KeyImport,
                        ),
                    )
                )
            }
        }
}
