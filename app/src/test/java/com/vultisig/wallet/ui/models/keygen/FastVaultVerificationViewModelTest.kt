@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.BackupCodeVerifyResult
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.TemporaryVaultRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.data.usecases.fast.VerifyFastVaultBackupCodeUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class FastVaultVerificationViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val context: Context = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)
    private val saveVault: SaveVaultUseCase = mockk(relaxed = true)
    private val verifyUseCase: VerifyFastVaultBackupCodeUseCase = mockk(relaxed = true)
    private val temporaryVaultRepository: TemporaryVaultRepository = mockk(relaxed = true)
    private val vaultDataStoreRepository: VaultDataStoreRepository = mockk(relaxed = true)
    private val vaultPasswordRepository: VaultPasswordRepository = mockk(relaxed = true)
    private val vaultMetadataRepo: VaultMetadataRepo = mockk(relaxed = true)
    private val vaultRepository: VaultRepository = mockk(relaxed = true)
    private val chainAccountAddressRepository: ChainAccountAddressRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.FastVaultVerification>() } returns
            Route.FastVaultVerification(
                vaultId = "vault",
                pubKeyEcdsa = "pub",
                email = "a@b.c",
                tssAction = TssAction.KEYGEN,
                vaultName = "v",
                password = "pw",
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    @Test
    fun `verify maps Invalid result to Error state`() =
        runTest(testDispatcher) {
            coEvery { verifyUseCase(any(), any()) } returns BackupCodeVerifyResult.Invalid
            val vm = buildViewModel()

            vm.codeFieldState.setTextAndPlaceCursorAtEnd("1234")
            vm.processCode("1234")
            advanceUntilIdle()

            assertEquals(VerifyPinState.Error, vm.state.value.verifyPinState)
        }

    @Test
    fun `verify maps NetworkError result to NetworkError state`() =
        runTest(testDispatcher) {
            coEvery { verifyUseCase(any(), any()) } returns BackupCodeVerifyResult.NetworkError
            val vm = buildViewModel()

            vm.codeFieldState.setTextAndPlaceCursorAtEnd("1234")
            vm.processCode("1234")
            advanceUntilIdle()

            assertEquals(VerifyPinState.NetworkError, vm.state.value.verifyPinState)
        }

    @Test
    fun `retry re-runs verify after a network failure`() =
        runTest(testDispatcher) {
            coEvery { verifyUseCase(any(), any()) } returnsMany
                listOf(BackupCodeVerifyResult.NetworkError, BackupCodeVerifyResult.Invalid)
            val vm = buildViewModel()

            vm.codeFieldState.setTextAndPlaceCursorAtEnd("1234")
            vm.processCode("1234")
            advanceUntilIdle()
            assertEquals(VerifyPinState.NetworkError, vm.state.value.verifyPinState)

            vm.retry()
            advanceUntilIdle()
            assertEquals(VerifyPinState.Error, vm.state.value.verifyPinState)
        }

    private fun buildViewModel(): FastVaultVerificationViewModel =
        FastVaultVerificationViewModel(
            savedStateHandle = SavedStateHandle(),
            context = context,
            navigator = navigator,
            saveVault = saveVault,
            verifyFastVaultBackupCode = verifyUseCase,
            temporaryVaultRepository = temporaryVaultRepository,
            vaultDataStoreRepository = vaultDataStoreRepository,
            vaultPasswordRepository = vaultPasswordRepository,
            vaultMetadataRepo = vaultMetadataRepo,
            vaultRepository = vaultRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
        )
}
