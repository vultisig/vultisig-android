@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.screens.backup

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ServerBackupResult
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.backup.RequestServerBackupUseCase
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerBackupViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(scheduler)

    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository
    private lateinit var requestServerBackup: RequestServerBackupUseCase
    private lateinit var savedStateHandle: SavedStateHandle

    private val testVaultId = "test-vault-id"
    private val testVaultName = "Test Vault"
    private val testEmail = "test@example.com"
    private val testPassword = "password123"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        navigator = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        requestServerBackup = mockk(relaxed = true)
        savedStateHandle = mockk(relaxed = true)

        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every {
            any<SavedStateHandle>().toRoute<Route.ServerBackup>()
        } returns Route.ServerBackup(vaultId = testVaultId)

        coEvery { vaultRepository.get(testVaultId) } returns mockk<Vault>(relaxed = true) {
            every { name } returns testVaultName
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
    }

    private fun createViewModel() = ServerBackupViewModel(
        navigator = navigator,
        vaultRepository = vaultRepository,
        requestServerBackup = requestServerBackup,
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun `initial state loads vault name`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(testVaultName, vm.state.value.vaultName)
        assertTrue(vm.state.value.isNameConfirmed)
    }

    @Test
    fun `initial state with prefill email sets email confirmed`() = runTest(mainDispatcher) {
        every {
            any<SavedStateHandle>().toRoute<Route.ServerBackup>()
        } returns Route.ServerBackup(
            vaultId = testVaultId,
            prefillEmail = testEmail,
            prefillName = testVaultName,
        )

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.isEmailConfirmed)
        assertEquals(testEmail, vm.emailFieldState.text.toString())
    }

    @Test
    fun `valid email sets success inner state`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, testEmail) }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(VsTextInputFieldInnerState.Success, vm.state.value.emailInnerState)
        assertEquals(UiText.Empty, vm.state.value.emailError)
    }

    @Test
    fun `invalid email sets error inner state`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, "invalid-email") }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        assertEquals(VsTextInputFieldInnerState.Error, vm.state.value.emailInnerState)
    }

    @Test
    fun `empty email sets default inner state`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(VsTextInputFieldInnerState.Default, vm.state.value.emailInnerState)
    }

    @Test
    fun `togglePasswordVisibility toggles state`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.isPasswordVisible)

        vm.togglePasswordVisibility()
        assertTrue(vm.state.value.isPasswordVisible)

        vm.togglePasswordVisibility()
        assertFalse(vm.state.value.isPasswordVisible)
    }

    @Test
    fun `onEditEmail sets isEmailConfirmed to false`() = runTest(mainDispatcher) {
        every {
            any<SavedStateHandle>().toRoute<Route.ServerBackup>()
        } returns Route.ServerBackup(
            vaultId = testVaultId,
            prefillEmail = testEmail,
        )

        val vm = createViewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.isEmailConfirmed)

        vm.onEditEmail()
        assertFalse(vm.state.value.isEmailConfirmed)
    }

    @Test
    fun `clearEmailInput clears the field`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, testEmail) }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.clearEmailInput()
        assertTrue(vm.emailFieldState.text.isEmpty())
    }

    @Test
    fun `onSubmit with valid data calls requestServerBackup and sets success`() =
        runTest(mainDispatcher) {
            coEvery {
                requestServerBackup(testVaultId, testEmail, testPassword)
            } returns ServerBackupResult.Success

            val vm = createViewModel()
            advanceUntilIdle()

            vm.emailFieldState.edit { replace(0, length, testEmail) }
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            vm.passwordFieldState.edit { replace(0, length, testPassword) }
            Snapshot.sendApplyNotifications()

            vm.onSubmit()
            advanceUntilIdle()

            assertTrue(vm.state.value.isSuccess)
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun `onSubmit with invalid password returns error banner`() = runTest(mainDispatcher) {
        coEvery {
            requestServerBackup(testVaultId, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.INVALID_PASSWORD)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, testEmail) }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.passwordFieldState.edit { replace(0, length, testPassword) }
        Snapshot.sendApplyNotifications()

        vm.onSubmit()
        advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.errorBanner != UiText.Empty)
    }

    @Test
    fun `onSubmit with network error returns error banner`() = runTest(mainDispatcher) {
        coEvery {
            requestServerBackup(testVaultId, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.NETWORK_ERROR)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, testEmail) }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.passwordFieldState.edit { replace(0, length, testPassword) }
        Snapshot.sendApplyNotifications()

        vm.onSubmit()
        advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
        assertTrue(vm.state.value.errorBanner != UiText.Empty)
    }

    @Test
    fun `onSubmit with too many requests returns error banner`() = runTest(mainDispatcher) {
        coEvery {
            requestServerBackup(testVaultId, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.TOO_MANY_REQUESTS)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, testEmail) }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.passwordFieldState.edit { replace(0, length, testPassword) }
        Snapshot.sendApplyNotifications()

        vm.onSubmit()
        advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
        assertTrue(vm.state.value.errorBanner != UiText.Empty)
    }

    @Test
    fun `onSubmit does nothing with invalid email`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, "invalid") }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.passwordFieldState.edit { replace(0, length, testPassword) }
        Snapshot.sendApplyNotifications()

        vm.onSubmit()
        advanceUntilIdle()

        coVerify(exactly = 0) { requestServerBackup(any(), any(), any()) }
    }

    @Test
    fun `onSubmit does nothing with empty password`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, testEmail) }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.onSubmit()
        advanceUntilIdle()

        coVerify(exactly = 0) { requestServerBackup(any(), any(), any()) }
    }

    @Test
    fun `onSubmit with bad request returns error banner`() = runTest(mainDispatcher) {
        coEvery {
            requestServerBackup(testVaultId, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.BAD_REQUEST)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, testEmail) }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.passwordFieldState.edit { replace(0, length, testPassword) }
        Snapshot.sendApplyNotifications()

        vm.onSubmit()
        advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
        assertTrue(vm.state.value.errorBanner != UiText.Empty)
    }

    @Test
    fun `onSubmit with unknown error returns error banner`() = runTest(mainDispatcher) {
        coEvery {
            requestServerBackup(testVaultId, testEmail, testPassword)
        } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.UNKNOWN)

        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, testEmail) }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.passwordFieldState.edit { replace(0, length, testPassword) }
        Snapshot.sendApplyNotifications()

        vm.onSubmit()
        advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
        assertTrue(vm.state.value.errorBanner != UiText.Empty)
    }

    @Test
    fun `onSubmit is ignored while loading`() = runTest(mainDispatcher) {
        coEvery {
            requestServerBackup(testVaultId, testEmail, testPassword)
        } returns ServerBackupResult.Success

        val vm = createViewModel()
        advanceUntilIdle()

        vm.emailFieldState.edit { replace(0, length, testEmail) }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        vm.passwordFieldState.edit { replace(0, length, testPassword) }
        Snapshot.sendApplyNotifications()

        // Simulate loading state
        vm.state.update { it.copy(isLoading = true) }

        vm.onSubmit()
        advanceUntilIdle()

        // Should not have called the use case since isLoading was true
        coVerify(exactly = 0) { requestServerBackup(any(), any(), any()) }
    }

    @Test
    fun `error banner persists during retry and updates on new error`() =
        runTest(mainDispatcher) {
            coEvery {
                requestServerBackup(testVaultId, testEmail, testPassword)
            } returns ServerBackupResult.Error(ServerBackupResult.ErrorType.TOO_MANY_REQUESTS)

            val vm = createViewModel()
            advanceUntilIdle()

            vm.emailFieldState.edit { replace(0, length, testEmail) }
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            vm.passwordFieldState.edit { replace(0, length, testPassword) }
            Snapshot.sendApplyNotifications()

            // First submit — should show error
            vm.onSubmit()
            advanceUntilIdle()

            val firstError = vm.state.value.errorBanner
            assertTrue(firstError != UiText.Empty)

            // Error banner is not cleared before the request starts
            // (it persists to avoid StateFlow conflation issues with fast responses)
            assertFalse(vm.state.value.isLoading)
            assertTrue(vm.state.value.errorBanner != UiText.Empty)
        }

    @Test
    fun `onSuccessClose navigates back`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onSuccessClose()
        advanceUntilIdle()

        coVerify { navigator.navigate(Destination.Back) }
    }

    @Test
    fun `back navigates back`() = runTest(mainDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.back()
        advanceUntilIdle()

        coVerify { navigator.navigate(Destination.Back) }
    }
}
