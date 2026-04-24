@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CheckServerVaultExistsUseCase
import com.vultisig.wallet.data.usecases.GenerateUniqueName
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoEvent
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoViewModel
import com.vultisig.wallet.ui.models.v3.onboarding.StepType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [EnterVaultInfoViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EnterVaultInfoViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository
    private lateinit var isNameLengthValid: IsVaultNameValid
    private lateinit var generateUniqueName: GenerateUniqueName
    private lateinit var referralCodeSettingsRepository: ReferralCodeSettingsRepositoryContract
    private lateinit var keyImportRepository: KeyImportRepository
    private lateinit var checkServerVaultExists: CheckServerVaultExistsUseCase

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.EnterVaultInfo>() } returns
            Route.EnterVaultInfo(count = 2, tssAction = TssAction.KEYGEN)
        context = mockk(relaxed = true)
        navigator = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        isNameLengthValid = mockk(relaxed = true)
        generateUniqueName = mockk(relaxed = true)
        referralCodeSettingsRepository = mockk(relaxed = true)
        keyImportRepository = mockk(relaxed = true)
        checkServerVaultExists = mockk(relaxed = true)
        every { isNameLengthValid(any()) } returns true
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        EnterVaultInfoViewModel(
            navigator = navigator,
            vaultRepository = vaultRepository,
            isNameLengthValid = isNameLengthValid,
            generateUniqueName = generateUniqueName,
            referralCodeSettingsRepository = referralCodeSettingsRepository,
            keyImportRepository = keyImportRepository,
            checkServerVaultExists = checkServerVaultExists,
            context = context,
            savedStateHandle = SavedStateHandle(),
        )

    /** Verifies the initial active step is Name. */
    @Test
    fun `initial active step is Name`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            assertEquals(StepType.Name, vm.uiState.value.activeStep)
        }

    /** Verifies ShowMoreInfo event sets isMoreInfoVisible to true. */
    @Test
    fun `ShowMoreInfo event sets isMoreInfoVisible to true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.ShowMoreInfo)
            assertTrue(vm.uiState.value.isMoreInfoVisible)
        }

    /** Verifies HideMoreInfo event sets isMoreInfoVisible to false. */
    @Test
    fun `HideMoreInfo event sets isMoreInfoVisible to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.ShowMoreInfo)
            vm.onEvent(EnterVaultInfoEvent.HideMoreInfo)
            assertFalse(vm.uiState.value.isMoreInfoVisible)
        }

    /** Verifies TogglePasswordVisibility event toggles isPasswordVisible. */
    @Test
    fun `TogglePasswordVisibility event toggles isPasswordVisible`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.TogglePasswordVisibility)
            assertTrue(vm.uiState.value.isPasswordVisible)
            vm.onEvent(EnterVaultInfoEvent.TogglePasswordVisibility)
            assertFalse(vm.uiState.value.isPasswordVisible)
        }

    /** Verifies dismissServerVaultWarning clears showServerVaultExistsWarning. */
    @Test
    fun `dismissServerVaultWarning clears showServerVaultExistsWarning`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.dismissServerVaultWarning()
            assertFalse(vm.uiState.value.showServerVaultExistsWarning)
        }

    /** Verifies Back event from Name step navigates back. */
    @Test
    fun `Back event from Name step navigates back`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.Back)
            coVerify { navigator.navigate(Destination.Back) }
        }

    /**
     * Verifies Next event does not advance step when name validation fails (count=1 gives 3 steps
     * so advancement from Name to Email is observable).
     */
    @Test
    fun `Next event does not advance step when name is invalid`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.EnterVaultInfo>() } returns
                Route.EnterVaultInfo(count = 1, tssAction = TssAction.KEYGEN)
            every { isNameLengthValid(any()) } returns false
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.Next)
            assertEquals(StepType.Name, vm.uiState.value.activeStep)
        }

    /** Verifies Next event advances from Name to Email when device count is 1 (3-step flow). */
    @Test
    fun `Next event advances to Email step when device count is 1`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.EnterVaultInfo>() } returns
                Route.EnterVaultInfo(count = 1, tssAction = TssAction.KEYGEN)
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.Next)
            assertEquals(StepType.Email, vm.uiState.value.activeStep)
        }

    /** Verifies a secure vault (count=2) only exposes the Name step with no email or password. */
    @Test
    fun `secure vault with count 2 exposes only the Name step`() =
        runTest(testDispatcher) {
            val vm = createViewModel() // count=2 from setUp
            assertEquals(1, vm.uiState.value.stepAndStates.size)
            assertTrue(vm.uiState.value.stepAndStates.containsKey(StepType.Name))
            assertFalse(vm.uiState.value.stepAndStates.containsKey(StepType.Email))
        }

    /** Verifies a non-secure vault (count=1) exposes Name, Email, and Password steps. */
    @Test
    fun `non-secure vault with count 1 exposes Name Email and Password steps`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.EnterVaultInfo>() } returns
                Route.EnterVaultInfo(count = 1, tssAction = TssAction.KEYGEN)
            val vm = createViewModel()
            assertEquals(3, vm.uiState.value.stepAndStates.size)
            assertTrue(vm.uiState.value.stepAndStates.containsKey(StepType.Email))
            assertTrue(vm.uiState.value.stepAndStates.containsKey(StepType.Password))
        }
}
