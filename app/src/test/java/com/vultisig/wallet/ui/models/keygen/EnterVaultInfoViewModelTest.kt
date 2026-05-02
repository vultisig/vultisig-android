@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.KeyImportRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CheckServerVaultExistsUseCase
import com.vultisig.wallet.data.usecases.GenerateUniqueName
import com.vultisig.wallet.data.usecases.IsEmailValid
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoEvent
import com.vultisig.wallet.ui.models.v3.onboarding.EnterVaultInfoViewModel
import com.vultisig.wallet.ui.models.v3.onboarding.StepType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository
    private lateinit var isNameLengthValid: IsVaultNameValid
    private lateinit var isEmailValid: IsEmailValid
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
        navigator = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        isNameLengthValid = mockk(relaxed = true)
        isEmailValid = mockk(relaxed = true)
        generateUniqueName = mockk(relaxed = true)
        referralCodeSettingsRepository = mockk(relaxed = true)
        keyImportRepository = mockk(relaxed = true)
        checkServerVaultExists = mockk(relaxed = true)
        // Function-type-interface mocks need explicit return-type stubs; relaxed mode auto-stubs
        // to a generic Object that fails the implicit cast at the VM call site (e.g. the
        // String return type on `generateUniqueName` invoked from VM init).
        every { isNameLengthValid(any()) } returns true
        every { isEmailValid(any()) } returns true
        every { generateUniqueName(any(), any()) } returns "TestVault"
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
            isEmailValid = isEmailValid,
            generateUniqueName = generateUniqueName,
            referralCodeSettingsRepository = referralCodeSettingsRepository,
            keyImportRepository = keyImportRepository,
            checkServerVaultExists = checkServerVaultExists,
            context = mockk(relaxed = true),
            savedStateHandle = SavedStateHandle(),
        )

    /** Verifies the initial active step is Name. */
    @Test
    fun `initial active step is Name`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.uiState.value.activeStep shouldBe StepType.Name
        }

    /** Verifies ShowMoreInfo event sets isMoreInfoVisible to true. */
    @Test
    fun `ShowMoreInfo event sets isMoreInfoVisible to true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.ShowMoreInfo)
            vm.uiState.value.isMoreInfoVisible.shouldBeTrue()
        }

    /** Verifies HideMoreInfo event sets isMoreInfoVisible to false. */
    @Test
    fun `HideMoreInfo event sets isMoreInfoVisible to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.ShowMoreInfo)
            vm.onEvent(EnterVaultInfoEvent.HideMoreInfo)
            vm.uiState.value.isMoreInfoVisible.shouldBeFalse()
        }

    /** Verifies TogglePasswordVisibility event toggles isPasswordVisible. */
    @Test
    fun `TogglePasswordVisibility event toggles isPasswordVisible`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.TogglePasswordVisibility)
            vm.uiState.value.isPasswordVisible.shouldBeTrue()
            vm.onEvent(EnterVaultInfoEvent.TogglePasswordVisibility)
            vm.uiState.value.isPasswordVisible.shouldBeFalse()
        }

    /** Verifies dismissServerVaultWarning clears showServerVaultExistsWarning. */
    @Test
    fun `dismissServerVaultWarning clears showServerVaultExistsWarning`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.uiState.update { it.copy(showServerVaultExistsWarning = true) }
            vm.uiState.value.showServerVaultExistsWarning.shouldBeTrue()
            vm.dismissServerVaultWarning()
            vm.uiState.value.showServerVaultExistsWarning.shouldBeFalse()
        }

    /**
     * Verifies continueWithServerVaultWarning clears the warning flag and routes to peer discovery.
     */
    @Test
    fun `continueWithServerVaultWarning navigates to PeerDiscovery and clears warning`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.EnterVaultInfo>() } returns
                Route.EnterVaultInfo(count = 1, tssAction = TssAction.KeyImport)
            val vm = createViewModel()
            vm.uiState.update { it.copy(showServerVaultExistsWarning = true) }
            vm.continueWithServerVaultWarning()
            advanceUntilIdle()
            vm.uiState.value.showServerVaultExistsWarning.shouldBeFalse()
            coVerify { navigator.route(any<Route.Keygen.PeerDiscovery>()) }
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
            vm.uiState.value.activeStep shouldBe StepType.Name
        }

    /** Verifies Next event advances from Name to Email when device count is 1 (3-step flow). */
    @Test
    fun `Next event advances to Email step when device count is 1`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.EnterVaultInfo>() } returns
                Route.EnterVaultInfo(count = 1, tssAction = TssAction.KEYGEN)
            val vm = createViewModel()
            vm.onEvent(EnterVaultInfoEvent.Next)
            vm.uiState.value.activeStep shouldBe StepType.Email
        }

    /** Verifies a secure vault (count=2) only exposes the Name step with no email or password. */
    @Test
    fun `secure vault with count 2 exposes only the Name step`() =
        runTest(testDispatcher) {
            val vm = createViewModel() // count=2 from setUp
            vm.uiState.value.stepAndStates.size shouldBe 1
            vm.uiState.value.stepAndStates.containsKey(StepType.Name).shouldBeTrue()
            vm.uiState.value.stepAndStates.containsKey(StepType.Email).shouldBeFalse()
        }

    /** Verifies a non-secure vault (count=1) exposes Name, Email, and Password steps. */
    @Test
    fun `non-secure vault with count 1 exposes Name Email and Password steps`() =
        runTest(testDispatcher) {
            every { any<SavedStateHandle>().toRoute<Route.EnterVaultInfo>() } returns
                Route.EnterVaultInfo(count = 1, tssAction = TssAction.KEYGEN)
            val vm = createViewModel()
            vm.uiState.value.stepAndStates.size shouldBe 3
            vm.uiState.value.stepAndStates.containsKey(StepType.Email).shouldBeTrue()
            vm.uiState.value.stepAndStates.containsKey(StepType.Password).shouldBeTrue()
        }
}
