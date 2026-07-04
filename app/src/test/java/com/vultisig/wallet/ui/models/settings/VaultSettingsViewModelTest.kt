@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.settings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.CustomRpcConfig
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsItem
import com.vultisig.wallet.ui.screens.vault_settings.VaultSettingsViewModel
import com.vultisig.wallet.ui.utils.SnackbarFlow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.math.BigInteger
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
    private lateinit var customRpcConfig: CustomRpcConfig
    private lateinit var getDiscountBps: GetDiscountBpsUseCase

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
        customRpcConfig = mockk(relaxed = true) { every { isFeatureEnabled } returns flowOf(false) }
        getDiscountBps = mockk(relaxed = true)
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
            customRpcConfig = customRpcConfig,
            getDiscountBps = getDiscountBps,
        )

    /** Verifies clicking Advanced sets isAdvanceSetting to true. */
    @Test
    fun `clicking Advanced sets isAdvanceSetting to true`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Advanced)
            vm.uiModel.value.isAdvanceSetting.shouldBeTrue()
        }

    /** Verifies onBackClick when isAdvanceSetting is true resets it to false. */
    @Test
    fun `onBackClick when isAdvanceSetting is true resets it to false`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Advanced)
            vm.onBackClick()
            vm.uiModel.value.isAdvanceSetting.shouldBeFalse()
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
            vm.uiModel.value.isBackupVaultBottomSheetVisible.shouldBeTrue()

            vm.onDismissBackupVaultBottomSheet()
            vm.uiModel.value.isBackupVaultBottomSheetVisible.shouldBeFalse()
        }

    /** Verifies onDismissBiometricFastSignBottomSheet hides biometric sheet after it was opened. */
    @Test
    fun `onDismissBiometricFastSignBottomSheet hides biometric sheet after it was opened`() =
        runTest(testDispatcher) {
            val vm = createViewModel()

            vm.onSettingsItemClick(
                VaultSettingsItem.BiometricFastSign(isEnabled = false, isBiometricEnabled = false)
            )
            vm.uiModel.value.isBiometricFastSignBottomSheetVisible.shouldBeTrue()

            vm.onDismissBiometricFastSignBottomSheet()
            vm.uiModel.value.isBiometricFastSignBottomSheetVisible.shouldBeFalse()
        }

    /** Verifies togglePasswordVisibility flips isPasswordVisible by capturing the prior value. */
    @Test
    fun `togglePasswordVisibility flips isPasswordVisible`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            val initial = vm.uiModel.value.biometricsEnableUiModel.isPasswordVisible

            vm.togglePasswordVisibility()
            vm.uiModel.value.biometricsEnableUiModel.isPasswordVisible shouldBe !initial

            vm.togglePasswordVisibility()
            vm.uiModel.value.biometricsEnableUiModel.isPasswordVisible shouldBe initial
        }

    /** Verifies clicking Rename routes to Route.Rename with the current vault id. */
    @Test
    fun `clicking Rename routes to Rename screen with vault id`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Rename)
            coVerify { navigator.route(Route.Rename(VAULT_ID)) }
        }

    /** Verifies clicking Reshare routes to ReshareStartScreen with the current vault id. */
    @Test
    fun `clicking Reshare routes to ReshareStartScreen with vault id`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Reshare(false))
            coVerify { navigator.route(Route.ReshareStartScreen(VAULT_ID)) }
        }

    /** Verifies clicking Delete routes to ConfirmDelete with the current vault id. */
    @Test
    fun `clicking Delete routes to ConfirmDelete with vault id`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Delete)
            coVerify { navigator.route(Route.ConfirmDelete(VAULT_ID)) }
        }

    /** Verifies clicking Details routes to Details with the current vault id. */
    @Test
    fun `clicking Details routes to Details with vault id`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Details)
            coVerify { navigator.route(Route.Details(VAULT_ID)) }
        }

    /** Verifies clicking Sign routes to SignMessage with the current vault id. */
    @Test
    fun `clicking Sign routes to SignMessage with vault id`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.Sign)
            coVerify { navigator.route(Route.SignMessage(VAULT_ID)) }
        }

    /** Silver-tier users (balance at/above threshold) reach the Custom RPC picker directly. */
    @Test
    fun `clicking CustomRpc as Silver navigates to CustomRpcList`() =
        runTest(testDispatcher) {
            coEvery { getDiscountBps.getVultBalance(VAULT_ID) } returns SILVER_TIER_THRESHOLD
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.CustomRpc(true))
            vm.uiModel.value.showCustomRpcUpsell.shouldBeFalse()
            coVerify { navigator.route(Route.CustomRpcList(VAULT_ID)) }
        }

    /** Below Silver, the Custom RPC entry shows the Silver upsell sheet. */
    @Test
    fun `clicking CustomRpc below Silver shows the upsell sheet`() =
        runTest(testDispatcher) {
            coEvery { getDiscountBps.getVultBalance(VAULT_ID) } returns
                SILVER_TIER_THRESHOLD - BigInteger.ONE
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.CustomRpc(true))
            vm.uiModel.value.showCustomRpcUpsell.shouldBeTrue()
            vm.uiModel.value.customRpcIsBelowThreshold.shouldBeTrue()
            coVerify(exactly = 0) { navigator.route(Route.CustomRpcList(VAULT_ID)) }
        }

    /** A failed/unknown balance lookup falls back to the upsell rather than opening the picker. */
    @Test
    fun `clicking CustomRpc with failing balance lookup falls back to the upsell sheet`() =
        runTest(testDispatcher) {
            coEvery { getDiscountBps.getVultBalance(VAULT_ID) } throws RuntimeException("boom")
            val vm = createViewModel()
            vm.onSettingsItemClick(VaultSettingsItem.CustomRpc(true))
            vm.uiModel.value.showCustomRpcUpsell.shouldBeTrue()
            coVerify(exactly = 0) { navigator.route(Route.CustomRpcList(VAULT_ID)) }
        }

    /**
     * The upsell exposes balance and threshold as grouped whole-token strings (US locale pinned).
     */
    @Test
    fun `custom rpc upsell formats balance and threshold as whole VULT tokens`() =
        runTest(testDispatcher) {
            val previousLocale = Locale.getDefault()
            Locale.setDefault(Locale.US)
            try {
                val balance = BigInteger("2340") * BigInteger.TEN.pow(18)
                coEvery { getDiscountBps.getVultBalance(VAULT_ID) } returns balance
                val vm = createViewModel()
                vm.onSettingsItemClick(VaultSettingsItem.CustomRpc(true))
                vm.uiModel.value.customRpcVultBalance shouldBe "2,340 VULT"
                vm.uiModel.value.customRpcVultThreshold shouldBe "3,000 VULT"
            } finally {
                Locale.setDefault(previousLocale)
            }
        }

    /** Tapping Get $VULT dismisses the upsell and routes to the DiscountTiers screen. */
    @Test
    fun `onUnlockCustomRpcTier dismisses upsell and routes to DiscountTiers`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.onUnlockCustomRpcTier()
            vm.uiModel.value.showCustomRpcUpsell.shouldBeFalse()
            coVerify { navigator.route(Route.DiscountTiers(VAULT_ID)) }
        }

    /** The Custom RPC row is enabled in the Advanced group when its feature flag is on. */
    @Test
    fun `custom rpc row is enabled when feature flag is enabled`() =
        runTest(testDispatcher) {
            every { customRpcConfig.isFeatureEnabled } returns flowOf(true)
            val vm = createViewModel()
            val customRpc =
                vm.uiModel.value.settingGroups
                    .flatMap { it.items }
                    .filterIsInstance<VaultSettingsItem.CustomRpc>()
                    .single()
            customRpc.enabled.shouldBeTrue()
        }

    /** The Custom RPC row is disabled (filtered out of the list) when its feature flag is off. */
    @Test
    fun `custom rpc row is disabled when feature flag is disabled`() =
        runTest(testDispatcher) {
            every { customRpcConfig.isFeatureEnabled } returns flowOf(false)
            val vm = createViewModel()
            val customRpc =
                vm.uiModel.value.settingGroups
                    .flatMap { it.items }
                    .filterIsInstance<VaultSettingsItem.CustomRpc>()
                    .single()
            customRpc.enabled.shouldBeFalse()
        }

    /** The post-quantum keygen row shows for a DKLS vault without an MLDSA key yet. */
    @Test
    fun `dilithium keygen row is enabled for a DKLS vault`() =
        runTest(testDispatcher) {
            coEvery { vaultRepository.get(VAULT_ID) } returns
                Vault(id = VAULT_ID, name = "Test", libType = SigningLibType.DKLS)
            val vm = createViewModel()
            val dilithium =
                vm.uiModel.value.settingGroups
                    .flatMap { it.items }
                    .filterIsInstance<VaultSettingsItem.DilithiumKeygen>()
                    .single()
            dilithium.enabled.shouldBeTrue()
        }

    /** GG20 vaults must migrate to DKLS first, so the post-quantum keygen row is hidden. */
    @Test
    fun `dilithium keygen row is hidden for a GG20 vault`() =
        runTest(testDispatcher) {
            coEvery { vaultRepository.get(VAULT_ID) } returns
                Vault(id = VAULT_ID, name = "Test", libType = SigningLibType.GG20)
            val vm = createViewModel()
            val dilithium =
                vm.uiModel.value.settingGroups
                    .flatMap { it.items }
                    .filterIsInstance<VaultSettingsItem.DilithiumKeygen>()
                    .single()
            dilithium.enabled.shouldBeFalse()
        }

    private companion object {
        const val VAULT_ID = "vault-1"
    }
}
