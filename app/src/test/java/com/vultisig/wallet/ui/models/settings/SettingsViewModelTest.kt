@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.settings

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
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

/** Unit tests for [SettingsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var appCurrencyRepository: AppCurrencyRepository
    private lateinit var appLocaleRepository: AppLocaleRepository
    private lateinit var referralRepository: ReferralCodeSettingsRepositoryContract
    private lateinit var preventScreenshotsRepository: PreventScreenshotsRepository

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

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
