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
            assertTrue(vm.state.value.showShareBottomSheet)
        }

    /** Verifies onDismissShareLinkBottomSheet hides share bottom sheet. */
    @Test
    fun `onDismissShareLinkBottomSheet hides share bottom sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(SettingsItem.ShareTheApp)
            vm.onDismissShareLinkBottomSheet()
            assertFalse(vm.state.value.showShareBottomSheet)
        }

    /** Verifies onDismissReferralBottomSheet hides referral sheet. */
    @Test
    fun `onDismissReferralBottomSheet hides referral sheet`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.state.value.copy(hasToShowReferralCodeSheet = true)
            vm.onDismissReferralBottomSheet()
            assertFalse(vm.state.value.hasToShowReferralCodeSheet)
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

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
