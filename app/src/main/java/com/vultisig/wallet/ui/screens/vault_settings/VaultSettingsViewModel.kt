package com.vultisig.wallet.ui.screens.vault_settings

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.keygen.VULTISERVER_SUPPORTS_MLDSA_LEVEL
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.hasValidMldsaKey
import com.vultisig.wallet.data.passcode.PasscodeConfig
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.isConfigured
import com.vultisig.wallet.data.repositories.CustomRpcConfig
import com.vultisig.wallet.data.repositories.PreventScreenshotsRepository
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepositoryContract
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCase
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_TIER_THRESHOLD
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class VaultSettingsGroupUiModel(
    val id: String? = null,
    val title: UiText?,
    val items: List<VaultSettingsItem>,
    val isVisible: Boolean = true,
)

internal data class BiometricsEnableUiModel(
    val isSaveEnabled: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val passwordErrorMessage: UiText? = null,
    val passwordHint: UiText? = null,
    val isLoading: Boolean = false,
)

internal data class VaultSettingsState(
    val settingGroups: List<VaultSettingsGroupUiModel> = emptyList(),
    val isAdvanceSetting: Boolean = false,
    val isBackupVaultBottomSheetVisible: Boolean = false,
    val isBiometricFastSignBottomSheetVisible: Boolean = false,
    val biometricsEnableUiModel: BiometricsEnableUiModel = BiometricsEnableUiModel(),
    val showCustomRpcUpsell: Boolean = false,
    val customRpcVultBalance: String = "",
    val customRpcVultThreshold: String = "",
    val customRpcIsBelowThreshold: Boolean = false,
    val hasToShowReferralCodeSheet: Boolean = false,
)

internal sealed class VaultSettingsItem(
    val value: SettingsItemUiModel,
    val enabled: Boolean = true,
) {
    data object Details :
        VaultSettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vault_settings_details_title),
                subTitle = UiText.StringResource(R.string.vault_settings_view_vault),
                trailingIcon = R.drawable.icon_chevron_right_small,
                leadingIcon = R.drawable.circle_info,
            )
        )

    data object Rename :
        VaultSettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vault_settings_rename_title),
                subTitle = UiText.StringResource(R.string.vault_settings_rename_subtitle),
                trailingIcon = R.drawable.icon_chevron_right_small,
                leadingIcon = R.drawable.pen_writing,
            )
        )

    data object ReferralCode :
        VaultSettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.referral_code_settings_title),
                trailingIcon = R.drawable.icon_chevron_right_small,
                leadingIcon = R.drawable.megaphone,
            )
        )

    data class BiometricFastSign(val isEnabled: Boolean, val isBiometricEnabled: Boolean) :
        VaultSettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vault_settings_biometric_fast),
                trailingSwitch = isBiometricEnabled,
                leadingIcon = R.drawable.bolt_filled,
            ),
            enabled = isEnabled,
        )

    data object BackupVaultShare :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_backup_vault),
                    subTitle = UiText.StringResource(R.string.vault_settings_back_up_your),
                    trailingIcon = R.drawable.icon_chevron_right_small,
                    leadingIcon = R.drawable.cloud_download,
                )
        )

    /** Opens the app-lock passcode settings; [isEnabled] follows the passcode entry rule. */
    data class Security(val isEnabled: Boolean) :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_security_screen_title),
                    subTitle = UiText.StringResource(R.string.vault_settings_security_row_subtitle),
                    trailingIcon = R.drawable.icon_chevron_right_small,
                    leadingIcon = R.drawable.shield_filled,
                ),
            enabled = isEnabled,
        )

    /** Opens the auto-lock picker, which only applies once a passcode is configured. */
    data class LockTime(val isEnabled: Boolean) :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_lock_time_title),
                    subTitle = UiText.StringResource(R.string.vault_settings_lock_time_subtitle),
                    trailingIcon = R.drawable.icon_chevron_right_small,
                    leadingIcon = R.drawable.lock_filled,
                ),
            enabled = isEnabled,
        )

    data class PreventScreenshots(val isEnabled: Boolean = false) :
        VaultSettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.settings_screen_prevent_screenshots),
                leadingIcon = R.drawable.eye_closed,
                trailingSwitch = isEnabled,
            )
        )

    data class Migrate(val isEnabled: Boolean) :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_migration_title),
                    subTitle = UiText.StringResource(R.string.vault_settings_migrate_gg20),
                    trailingIcon = R.drawable.arrows_chevron_right,
                    leadingIcon = R.drawable.cloud_upload,
                ),
            enabled = isEnabled,
        )

    data object Advanced :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_advanced_title),
                    subTitle = UiText.StringResource(R.string.vault_settings_reshare_change),
                    trailingIcon = R.drawable.arrows_chevron_right,
                    leadingIcon = R.drawable.file_search,
                )
        )

    data class Reshare(val isEnabled: Boolean) :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_reshare_title),
                    subTitle = UiText.StringResource(R.string.vault_settings_reshare_subtitle),
                    trailingIcon = R.drawable.icon_chevron_right_small,
                    leadingIcon = R.drawable.share_up_right_2,
                ),
            enabled = isEnabled,
        )

    data object Sign :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_sign_title),
                    subTitle =
                        UiText.StringResource(R.string.vault_settings_sign_message_description),
                    trailingIcon = R.drawable.icon_chevron_right_small,
                    leadingIcon = R.drawable.pen_writing,
                )
        )

    data object OnChainSecurity :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_security_title),
                    subTitle = UiText.StringResource(R.string.vault_settings_security_subtitle),
                    trailingIcon = R.drawable.icon_chevron_right_small,
                    leadingIcon = R.drawable.file_lock,
                )
        )

    data class DilithiumKeygen(val isEnabled: Boolean) :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_dilithium_keygen_title),
                    subTitle =
                        UiText.StringResource(R.string.vault_settings_dilithium_keygen_subtitle),
                    trailingIcon = R.drawable.icon_chevron_right_small,
                    leadingIcon = R.drawable.file_pen,
                ),
            enabled = isEnabled,
        )

    data object Delete :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.vault_settings_delete_title),
                    subTitle = UiText.StringResource(R.string.vault_settings_delete_subtitle),
                    trailingIcon = R.drawable.arrows_chevron_right,
                    leadingIcon = R.drawable.trash_filled,
                )
        )

    data class CustomRpc(val isEnabled: Boolean) :
        VaultSettingsItem(
            value =
                SettingsItemUiModel(
                    title = UiText.StringResource(R.string.custom_rpc_title),
                    titleBadge = UiText.StringResource(R.string.custom_rpc_tier_badge),
                    subTitle = UiText.StringResource(R.string.custom_rpc_advanced_subtitle),
                    trailingIcon = R.drawable.icon_chevron_right_small,
                    leadingIcon = R.drawable.signature,
                ),
            enabled = isEnabled,
        )
}

/** Rows before the vault and flags load; rows that depend on them start disabled. */
internal val defaultVaultSettingGroups =
    listOf(
        VaultSettingsGroupUiModel(
            title = UiText.StringResource(R.string.vault_management),
            items =
                listOf(
                    VaultSettingsItem.Details,
                    VaultSettingsItem.Rename,
                    VaultSettingsItem.ReferralCode,
                    VaultSettingsItem.BiometricFastSign(
                        isEnabled = false,
                        isBiometricEnabled = false,
                    ),
                ),
        ),
        VaultSettingsGroupUiModel(
            title = UiText.StringResource(R.string.vault_settings_security_screen_title),
            items =
                listOf(
                    VaultSettingsItem.Security(isEnabled = false),
                    VaultSettingsItem.LockTime(isEnabled = false),
                    VaultSettingsItem.BackupVaultShare,
                    VaultSettingsItem.PreventScreenshots(),
                ),
        ),
        VaultSettingsGroupUiModel(
            title = UiText.StringResource(R.string.other),
            items = listOf(VaultSettingsItem.Migrate(false), VaultSettingsItem.Advanced),
        ),
        VaultSettingsGroupUiModel(title = null, items = listOf(VaultSettingsItem.Delete)),
        VaultSettingsGroupUiModel(
            id = "ADVANCED",
            title = null,
            items =
                listOf(
                    VaultSettingsItem.Reshare(false),
                    VaultSettingsItem.DilithiumKeygen(false),
                    VaultSettingsItem.Sign,
                    VaultSettingsItem.OnChainSecurity,
                    VaultSettingsItem.CustomRpc(false),
                ),
            isVisible = false,
        ),
    )

@HiltViewModel
internal open class VaultSettingsViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    private val vaultRepository: VaultRepository,
    @ApplicationContext private val context: Context,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val vultiSignerRepository: VultiSignerRepository,
    private val snackbarFlow: SnackbarFlow,
    private val customRpcConfig: CustomRpcConfig,
    private val getDiscountBps: GetDiscountBpsUseCase,
    private val referralRepository: ReferralCodeSettingsRepositoryContract,
    private val preventScreenshotsRepository: PreventScreenshotsRepository,
    private val passcodeRepository: PasscodeRepository,
    private val passcodeConfig: PasscodeConfig,
) : ViewModel() {

    val uiModel = MutableStateFlow(VaultSettingsState(settingGroups = defaultVaultSettingGroups))

    var isBiometricFastSignEnabled: Boolean
        get() {
            val bool =
                uiModel.value.settingGroups
                    .flatMap { group -> group.items }
                    .filterIsInstance<VaultSettingsItem.BiometricFastSign>()
                    .firstOrNull()
                    ?.isBiometricEnabled ?: false
            return bool
        }
        set(value) {
            val newItem = updateBiometricFastSignUiModel(value)
            uiModel.update { it.copy(settingGroups = newItem) }
        }

    val passwordTextFieldState = TextFieldState()

    private val vaultId: String = savedStateHandle.toRoute<Route.VaultSettings>().vaultId

    private var hasFastSign: Boolean = false

    private var hasUsedReferral = false

    init {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
            val hasMigration = vault?.libType == SigningLibType.GG20
            hasFastSign = isVaultHasFastSignById(vaultId) && vault?.signers?.count() == 2
            val hasPassword = vaultPasswordRepository.getPassword(vaultId) != null
            val hasMldsaKey = vault != null && vault.hasValidMldsaKey()
            // Only DKLS vaults can run the post-quantum (Dilithium/MLDSA) keygen: KeyImport vaults
            // receive the key at import, and GG20 vaults must migrate to DKLS first — so the option
            // stays hidden for them (an isEnabled=false item is filtered out of the list).
            val supportsDilithiumKeygen =
                vault != null &&
                    vault.libType == SigningLibType.DKLS &&
                    (!hasFastSign || VULTISERVER_SUPPORTS_MLDSA_LEVEL)

            val newItems =
                uiModel.value.settingGroups.map { group ->
                    group.copy(
                        items =
                            group.items.map {
                                when (it) {
                                    is VaultSettingsItem.BiometricFastSign ->
                                        it.copy(
                                            isEnabled = hasFastSign,
                                            isBiometricEnabled = hasPassword,
                                        )

                                    is VaultSettingsItem.Migrate ->
                                        it.copy(isEnabled = hasMigration)
                                    // Reshare regenerates only ECDSA/EdDSA, so the MLDSA share
                                    // would stay tied to the old committee.
                                    is VaultSettingsItem.Reshare ->
                                        it.copy(isEnabled = !hasFastSign && !hasMldsaKey)
                                    is VaultSettingsItem.DilithiumKeygen ->
                                        it.copy(isEnabled = supportsDilithiumKeygen && !hasMldsaKey)
                                    else -> it
                                }
                            }
                    )
                }

            uiModel.update { it.copy(settingGroups = newItems) }
        }

        viewModelScope.launch {
            uiModel
                .map { it.isAdvanceSetting }
                .distinctUntilChanged()
                .collect { isAdvanceSettings ->
                    if (isAdvanceSettings) {
                        uiModel.update {
                            it.copy(
                                settingGroups =
                                    it.settingGroups.map { group ->
                                        group.copy(isVisible = group.id == "ADVANCED")
                                    }
                            )
                        }
                    } else {
                        uiModel.update {
                            it.copy(
                                settingGroups =
                                    it.settingGroups.map { group ->
                                        group.copy(isVisible = group.id != "ADVANCED")
                                    }
                            )
                        }
                    }
                }
        }

        // The Custom RPC entry (#4997) stays behind the Advanced Settings → Custom RPC feature
        // flag (#4787 kill-switch). When on, the row appears in this Advanced group, tier-gated.
        viewModelScope.launch {
            customRpcConfig.isFeatureEnabled.collect { enabled ->
                updateItems { item ->
                    when (item) {
                        is VaultSettingsItem.CustomRpc -> item.copy(isEnabled = enabled)
                        else -> item
                    }
                }
            }
        }

        loadPasscodeVisibility()
        loadPreventScreenshots()
        viewModelScope.launch { hasUsedReferral = referralRepository.hasVisitReferralCode() }

        validateEachTextChange()
    }

    // Security stays reachable once a passcode exists, so turning the flag off cannot strand an encrypted vault.
    private fun loadPasscodeVisibility() {
        // initialize() reads a keystore that can be wiped; a throw must not end this coroutine.
        viewModelScope.safeLaunch {
            passcodeRepository.initialize()
            combine(passcodeConfig.isFeatureEnabled, passcodeRepository.state) {
                    isFeatureEnabled,
                    passcodeState ->
                    isFeatureEnabled to passcodeState.isConfigured
                }
                .distinctUntilChanged()
                .collect { (isFeatureEnabled, isConfigured) ->
                    updateItems { item ->
                        when (item) {
                            is VaultSettingsItem.Security ->
                                item.copy(isEnabled = isFeatureEnabled || isConfigured)
                            is VaultSettingsItem.LockTime -> item.copy(isEnabled = isConfigured)
                            else -> item
                        }
                    }
                }
        }
    }

    private fun loadPreventScreenshots() {
        viewModelScope.launch {
            preventScreenshotsRepository.isEnabled.collect { isEnabled ->
                updateItems { item ->
                    when (item) {
                        is VaultSettingsItem.PreventScreenshots -> item.copy(isEnabled = isEnabled)
                        else -> item
                    }
                }
            }
        }
    }

    /** Maps every row inside [uiModel]'s update, so concurrent collectors don't drop each other. */
    private fun updateItems(transform: (VaultSettingsItem) -> VaultSettingsItem) {
        uiModel.update {
            it.copy(
                settingGroups =
                    it.settingGroups.map { group -> group.copy(items = group.items.map(transform)) }
            )
        }
    }

    fun onBackClick() {
        if (uiModel.value.isAdvanceSetting) {
            uiModel.update { it.copy(isAdvanceSetting = false) }
        } else {
            viewModelScope.launch { navigator.back() }
        }
    }

    fun onSettingsItemClick(item: VaultSettingsItem) {
        when (item) {
            VaultSettingsItem.Advanced -> {
                uiModel.update { it.copy(isAdvanceSetting = true) }
            }

            is VaultSettingsItem.BackupVaultShare if hasFastSign ->
                uiModel.update { it.copy(isBackupVaultBottomSheetVisible = true) }
            is VaultSettingsItem.BackupVaultShare -> navigateToBackupPasswordScreen()

            is VaultSettingsItem.BiometricFastSign -> {
                viewModelScope.launch {
                    if (!item.isBiometricEnabled) {
                        val newItem =
                            uiModel.value.copy(isBiometricFastSignBottomSheetVisible = true)
                        uiModel.update { newItem }
                    } else {
                        vaultPasswordRepository.clearPassword(vaultId)
                        isBiometricFastSignEnabled = false
                        showSnackbarMessage()
                    }
                }
            }

            VaultSettingsItem.ReferralCode -> onClickReferralCode()
            is VaultSettingsItem.Security -> {
                viewModelScope.launch { navigator.route(Route.PasscodeSettings) }
            }
            is VaultSettingsItem.LockTime -> {
                viewModelScope.launch { navigator.route(Route.AutoLockSetting) }
            }
            is VaultSettingsItem.PreventScreenshots -> {
                viewModelScope.launch { preventScreenshotsRepository.setEnabled(!item.isEnabled) }
            }
            VaultSettingsItem.Delete -> navigateToConfirmDeleteScreen()
            VaultSettingsItem.Details -> openDetails()
            is VaultSettingsItem.Migrate -> migrate()
            VaultSettingsItem.Rename -> openRename()
            VaultSettingsItem.OnChainSecurity -> navigateToOnChainSecurityScreen()
            is VaultSettingsItem.Reshare -> navigateToReshareStartScreen()
            is VaultSettingsItem.DilithiumKeygen -> navigateToDilithiumKeygen()
            VaultSettingsItem.Sign -> signMessage()
            is VaultSettingsItem.CustomRpc -> onCustomRpcClick()
        }
    }

    /**
     * Tier gate at the entry point (parity with iOS): below Silver, show the Silver upsell sheet
     * instead of the editor. Once inside the picker the user edits freely. A failed/unknown balance
     * lookup falls back to the upsell rather than granting access. The balance is fetched once and
     * the tier derived locally so we don't hit the repository twice per tap.
     */
    private fun onCustomRpcClick() {
        viewModelScope.safeLaunch {
            val balanceRaw =
                runCatching { getDiscountBps.getVultBalance(vaultId) }.getOrNull()
                    ?: BigInteger.ZERO
            if (balanceRaw >= SILVER_TIER_THRESHOLD) {
                navigator.route(Route.CustomRpcList(vaultId))
            } else {
                uiModel.update {
                    it.copy(
                        showCustomRpcUpsell = true,
                        customRpcVultBalance = formatVultBalance(balanceRaw),
                        customRpcVultThreshold = formatVultBalance(SILVER_TIER_THRESHOLD),
                        customRpcIsBelowThreshold = true,
                    )
                }
            }
        }
    }

    fun onClickReferralCode() {
        viewModelScope.launch {
            if (hasUsedReferral || referralRepository.isShown()) {
                navigator.navigate(Destination.ReferralCode(vaultId))
            } else {
                uiModel.update { it.copy(hasToShowReferralCodeSheet = true) }
            }
        }
    }

    fun onContinueReferralBottomSheet() {
        viewModelScope.launch {
            referralRepository.visitReferralCode()
            referralRepository.setAsShown()
            uiModel.update { it.copy(hasToShowReferralCodeSheet = false) }
            navigator.route(Route.ReferralOnboarding(vaultId))
        }
    }

    fun onDismissReferralBottomSheet() {
        viewModelScope.launch {
            referralRepository.setAsShown()
            uiModel.update { it.copy(hasToShowReferralCodeSheet = false) }
        }
    }

    fun onDismissCustomRpcUpsell() {
        uiModel.update { it.copy(showCustomRpcUpsell = false) }
    }

    fun onUnlockCustomRpcTier() {
        uiModel.update { it.copy(showCustomRpcUpsell = false) }
        viewModelScope.launch { navigator.route(Route.DiscountTiers(vaultId)) }
    }

    private fun updateBiometricFastSignUiModel(
        isBiometricEnabled: Boolean
    ): List<VaultSettingsGroupUiModel> =
        uiModel.value.settingGroups.map {
            it.copy(
                items =
                    it.items.map { item ->
                        if (item is VaultSettingsItem.BiometricFastSign) {
                            item.copy(isBiometricEnabled = isBiometricEnabled)
                        } else {
                            item
                        }
                    }
            )
        }

    fun openDetails() {
        viewModelScope.launch { navigator.route(Route.Details(vaultId)) }
    }

    fun openRename() {
        viewModelScope.launch { navigator.route(Route.Rename(vaultId)) }
    }

    fun navigateToBackupPasswordScreen() {
        viewModelScope.launch { navigator.route(Route.VaultsToBackup(vaultId)) }
    }

    fun navigateToConfirmDeleteScreen() {
        viewModelScope.launch { navigator.route(Route.ConfirmDelete(vaultId)) }
    }

    fun navigateToReshareStartScreen() {
        viewModelScope.launch { navigator.route(Route.ReshareStartScreen(vaultId)) }
    }

    fun navigateToOnChainSecurityScreen() {
        viewModelScope.launch { navigator.route(Route.OnChainSecurity) }
    }

    fun navigateToDilithiumKeygen() {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: error("No vault with id $vaultId exists")
            if (vault.hasValidMldsaKey() || vault.libType == SigningLibType.KeyImport) {
                return@launch
            }
            // The intro carries the new-QBTC-address warning; its "Get started" starts keygen.
            navigator.route(Route.QuantumSecurityIntro(vaultId = vaultId))
        }
    }

    fun signMessage() {
        viewModelScope.launch { navigator.route(Route.SignMessage(vaultId)) }
    }

    fun migrate() {
        viewModelScope.launch { navigator.route(Route.Migration.Onboarding(vaultId = vaultId)) }
    }

    fun onLocalBackupClick() {
        onDismissBackupVaultBottomSheet()
        navigateToBackupPasswordScreen()
    }

    fun onServerBackupClick() {
        onDismissBackupVaultBottomSheet()
        viewModelScope.launch { navigator.route(Route.ServerBackup(vaultId = vaultId)) }
    }

    fun onDismissBackupVaultBottomSheet() {
        uiModel.update { it.copy(isBackupVaultBottomSheetVisible = false) }
    }

    fun onDismissBiometricFastSignBottomSheet() {
        uiModel.update {
            it.copy(
                isBiometricFastSignBottomSheetVisible = false,
                biometricsEnableUiModel =
                    it.biometricsEnableUiModel.copy(
                        passwordErrorMessage = null,
                        passwordHint = null,
                    ),
            )
        }
        passwordTextFieldState.clearText()
    }

    fun togglePasswordVisibility() =
        viewModelScope.launch {
            uiModel.update {
                it.copy(
                    biometricsEnableUiModel =
                        it.biometricsEnableUiModel.copy(
                            isPasswordVisible = !it.biometricsEnableUiModel.isPasswordVisible
                        )
                )
            }
        }

    private fun validateEachTextChange() =
        viewModelScope.launch {
            passwordTextFieldState
                .textAsFlow()
                .combine(
                    uiModel.map { it.isBiometricFastSignBottomSheetVisible }.distinctUntilChanged()
                ) { text, isVisible ->
                    if (isVisible.not()) return@combine
                    uiModel.update {
                        it.copy(
                            biometricsEnableUiModel =
                                it.biometricsEnableUiModel.copy(isSaveEnabled = text.isNotEmpty())
                        )
                    }
                }
                .collect()
        }

    fun onSaveEnableBiometricFastSign() =
        viewModelScope.safeLaunch {
            val vault = vaultRepository.get(vaultId) ?: error("No vault with id $vaultId exists")
            setBiometricLoading(isLoading = true)
            try {
                val isPasswordValid =
                    vultiSignerRepository.isPasswordValid(
                        publicKeyEcdsa = vault.pubKeyECDSA,
                        password = passwordTextFieldState.text.toString(),
                    )

                if (!isPasswordValid) {
                    val hint = getPasswordHint()
                    passwordTextFieldState.clearText()
                    uiModel.update {
                        it.copy(
                            biometricsEnableUiModel =
                                it.biometricsEnableUiModel.copy(
                                    passwordErrorMessage =
                                        UiText.StringResource(
                                            R.string.keysign_password_incorrect_password
                                        ),
                                    passwordHint = hint,
                                    isSaveEnabled = false,
                                )
                        )
                    }
                    return@safeLaunch
                }
                if (!isBiometricFastSignEnabled) {
                    vaultPasswordRepository.savePassword(
                        vaultId = vaultId,
                        password = passwordTextFieldState.text.toString(),
                    )
                    isBiometricFastSignEnabled = true
                    passwordTextFieldState.clearText()
                    hideBiometricFastVaultBottomSheet()
                    showSnackbarMessage()
                }
            } finally {
                setBiometricLoading(isLoading = false)
            }
        }

    private fun setBiometricLoading(isLoading: Boolean) {
        uiModel.update {
            it.copy(
                biometricsEnableUiModel = it.biometricsEnableUiModel.copy(isLoading = isLoading)
            )
        }
    }

    private fun hideBiometricFastVaultBottomSheet() {
        uiModel.update { it.copy(isBiometricFastSignBottomSheetVisible = false) }
    }

    private suspend fun getPasswordHint(): UiText? {

        val passwordHintString =
            vaultDataStoreRepository.readFastSignHint(vaultId = vaultId).first()

        if (passwordHintString.isEmpty()) return null

        return UiText.FormattedText(
            R.string.import_file_password_hint_text,
            listOf(passwordHintString),
        )
    }

    private suspend fun showSnackbarMessage() {
        val messageRes =
            if (isBiometricFastSignEnabled) {
                R.string.vault_settings_biometrics_screen_snackbar_enabled
            } else {
                R.string.vault_settings_biometrics_screen_snackbar_disabled
            }
        snackbarFlow.showMessage(context.getString(messageRes))
    }
}

/** Formats a raw VULT balance (18 decimals) as a grouped, whole-token string e.g. "2,340 VULT". */
private fun formatVultBalance(raw: BigInteger): String {
    val whole = raw.toBigDecimal().movePointLeft(Coins.Ethereum.VULT.decimal)
    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
    numberFormat.maximumFractionDigits = 0
    return "${numberFormat.format(whole)} VULT"
}
