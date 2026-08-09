@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.settings

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.passcode.PasscodeConfig
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.AppLocaleRepository
import com.vultisig.wallet.data.repositories.PreventScreenshotsRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit tests for [SettingsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var appLocaleRepository: AppLocaleRepository
    private lateinit var referralRepository: ReferralCodeSettingsRepositoryContract
    private lateinit var preventScreenshotsRepository: PreventScreenshotsRepository
    private lateinit var passcodeConfig: PasscodeConfig
    private lateinit var passcodeRepository: PasscodeRepository

    /** Sets up mocks and test dispatcher before each test. */
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.Settings>() } returns
            Route.Settings(vaultId = VAULT_ID)
        navigator = mockk(relaxed = true)
        appCurrencyRepository = mockk(relaxed = true)
        appLocaleRepository = mockk(relaxed = true)
        referralRepository = mockk(relaxed = true)
        preventScreenshotsRepository = mockk(relaxed = true)
        passcodeConfig = mockk(relaxed = true) { every { isFeatureEnabled } returns flowOf(false) }
        passcodeRepository =
            mockk(relaxed = true) {
                every { state } returns MutableStateFlow(PasscodeState.Disabled)
            }
    }

    /** Cleans up mocks and resets test dispatcher after each test. */
    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SettingsViewModel(
            navigator = navigator,
            appCurrencyRepository = appCurrencyRepository,
            appLocaleRepository = appLocaleRepository,
            referralRepository = referralRepository,
            preventScreenshotsRepository = preventScreenshotsRepository,
            passcodeRepository = passcodeRepository,
            passcodeConfig = passcodeConfig,
            savedStateHandle = SavedStateHandle(),
        )

    /** Verifies clicking AddressBook navigates to AddressBookScreen. */
    @Test
    fun `clicking AddressBook navigates to AddressBookScreen`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.AddressBook)
            coVerify { navigator.route(Route.AddressBookScreen(vaultId = VAULT_ID)) }
        }

    /** Verifies clicking ShareTheApp opens share bottom sheet. */
    @Test
    fun `clicking ShareTheApp opens share bottom sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.ShareTheApp)
            vm.state.value.showShareBottomSheet.shouldBeTrue()
        }

    /** Verifies onDismissShareLinkBottomSheet hides share bottom sheet. */
    @Test
    fun `onDismissShareLinkBottomSheet hides share bottom sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.ShareTheApp)
            vm.onDismissShareLinkBottomSheet()
            vm.state.value.showShareBottomSheet.shouldBeFalse()
        }

    /** Verifies onDismissReferralBottomSheet hides referral sheet after it was opened. */
    @Test
    fun `onDismissReferralBottomSheet hides referral sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onClickReferralCode()
            vm.state.value.hasToShowReferralCodeSheet.shouldBeTrue()
            vm.onDismissReferralBottomSheet()
            vm.state.value.hasToShowReferralCodeSheet.shouldBeFalse()
        }

    /** Verifies clicking PreventScreenshots calls setEnabled with toggled value. */
    @Test
    fun `clicking PreventScreenshots calls setEnabled with toggled value`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.PreventScreenshots(isEnabled = false))
            coVerify { preventScreenshotsRepository.setEnabled(true) }
        }

    /** Verifies clicking VaultSetting navigates to VaultSettings. */
    @Test
    fun `clicking VaultSetting navigates to VaultSettings`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.VaultSetting)
            coVerify { navigator.route(Route.VaultSettings(VAULT_ID)) }
        }

    /** Verifies clicking PreventScreenshots when enabled calls setEnabled with false. */
    @Test
    fun `clicking PreventScreenshots when enabled calls setEnabled false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.PreventScreenshots(isEnabled = true))
            coVerify { preventScreenshotsRepository.setEnabled(false) }
        }

    /** Verifies clicking Currency navigates to CurrencyUnitSetting. */
    @Test
    fun `clicking Currency navigates to CurrencyUnitSetting`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.Currency(curr = "USD"))
            coVerify { navigator.route(Route.CurrencyUnitSetting) }
        }

    /** Verifies clicking Language navigates to LanguageSetting. */
    @Test
    fun `clicking Language navigates to LanguageSetting`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.Language(lang = "en"))
            coVerify { navigator.route(Route.LanguageSetting) }
        }

    /** Verifies clicking CheckForUpdates navigates to CheckForUpdateSetting. */
    @Test
    fun `clicking CheckForUpdates navigates to CheckForUpdateSetting`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.CheckForUpdates)
            coVerify { navigator.route(Route.CheckForUpdateSetting) }
        }

    /** Verifies clicking Notifications navigates to NotificationSettings. */
    @Test
    fun `clicking Notifications navigates to NotificationSettings`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.Notifications)
            coVerify { navigator.route(Route.NotificationSettings) }
        }

    /** Verifies clicking DiscountTiers navigates to DiscountTiers with vault id. */
    @Test
    fun `clicking DiscountTiers navigates to DiscountTiers with vault id`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.DiscountTiers)
            coVerify { navigator.route(Route.DiscountTiers(VAULT_ID)) }
        }

    /** Verifies clicking Discord emits an OpenLink event with the Discord link. */
    @Test
    fun `clicking Discord emits OpenLink event with Discord link`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.Discord)
            val event = vm.uiEvent.first()
            event shouldBe SettingsUiEvent.OpenLink(VsAuxiliaryLinks.DISCORD)
        }

    /** Verifies three rapid version taps route to the hidden Secret screen. */
    @Test
    fun `three onVersionClick taps route to Secret`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onVersionClick()
            vm.onVersionClick()
            vm.onVersionClick()
            coVerify { navigator.route(Route.Secret) }
        }

    /** Verifies fewer than three version taps do not route to the Secret screen. */
    @Test
    fun `two onVersionClick taps do not route to Secret`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onVersionClick()
            vm.onVersionClick()
            coVerify(exactly = 0) { navigator.route(Route.Secret) }
        }

    private companion object {
        const val VAULT_ID = "vault-1"
    }

    /** Verifies the passcode entry stays hidden until the Advanced Settings flag is switched on. */
    @Test
    fun `the passcode entry is hidden while the feature flag is off`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            assertFalse(
                vm.state.value.items.any { group ->
                    group.items.contains(SettingsItem.PasscodeEncryption)
                }
            )
        }

    /** Verifies switching the Advanced Settings flag on reveals the passcode entry. */
    @Test
    fun `the passcode entry appears once the feature flag is on`() =
        runTest(testDispatcher) {
            every { passcodeConfig.isFeatureEnabled } returns flowOf(true)
            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(
                vm.state.value.items.any { group ->
                    group.items.contains(SettingsItem.PasscodeEncryption)
                }
            )
        }

    /**
     * Verifies a configured passcode keeps its Settings entry even with the flag off. Otherwise
     * turning the flag back off would strand a tester with an encrypted vault and no off switch.
     */
    @Test
    fun `the passcode entry stays visible while a passcode is configured`() =
        runTest(testDispatcher) {
            every { passcodeConfig.isFeatureEnabled } returns flowOf(false)
            every { passcodeRepository.state } returns MutableStateFlow(PasscodeState.Locked)
            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(
                vm.state.value.items.any { group ->
                    group.items.contains(SettingsItem.PasscodeEncryption)
                }
            )
        }
}
