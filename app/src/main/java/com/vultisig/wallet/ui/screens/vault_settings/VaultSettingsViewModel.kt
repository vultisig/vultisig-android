package com.vultisig.wallet.ui.screens.vault_settings

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class VaultSettingsGroupUiModel(
    val id: String? = null,
    val title: UiText?,
    val items: List<VaultSettingsItem>,
    val isVisible: Boolean = true
)

internal data class BiometricsEnableUiModel(
    val isSaveEnabled: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val passwordErrorMessage: UiText? = null,
    val passwordHint: UiText? = null
)

internal data class VaultSettingsState(
    val settingGroups: List<VaultSettingsGroupUiModel> = emptyList(),
    val isAdvanceSetting: Boolean = false,
    val isBackupVaultBottomSheetVisible: Boolean = false,
    val isBiometricFastSignBottomSheetVisible: Boolean = false,
    val biometricsEnableUiModel: BiometricsEnableUiModel = BiometricsEnableUiModel(),
)

internal sealed class VaultSettingsItem(
    val value: SettingsItemUiModel,
    val enabled: Boolean = true,
) {
    data object Details : VaultSettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_details_title),
            subTitle = UiText.StringResource(R.string.vault_settings_view_vault),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.details
        ),
    )

    data object Rename : VaultSettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_rename_title),
            subTitle =UiText.StringResource(R.string.vault_settings_rename_subtitle),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.reame
        )
    )

    data class BiometricFastSign(val isEnabled: Boolean, val isBiometricEnabled: Boolean) :
        VaultSettingsItem(
            SettingsItemUiModel(
                title = UiText.StringResource(R.string.vault_settings_biometric_fast),
                trailingSwitch = isBiometricEnabled,
                leadingIcon = R.drawable.biomatrics_fast
            ),
            enabled = isEnabled,
        )
    data object Security : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_security_screen_title),
            subTitle = UiText.StringResource(R.string.vault_settings_enable_biometric),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.security,
        ),
        enabled = false
    )


    data object PasswordHint : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_password_hint),
            subTitle = UiText.StringResource(R.string.vault_settings_set_a_password),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.pass_hint
        ),
        enabled = false,
    )

    data object BackupVaultShare : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_backup_vault),
            subTitle = UiText.StringResource(R.string.vault_settings_back_up_your),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.backup_vault
        )
    )

    data class Migrate(val isEnabled: Boolean) : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_migration_title),
            subTitle = UiText.StringResource(R.string.vault_settings_migrate_gg20),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.pass_hint,
        ),
        enabled = isEnabled
    )

    data object Advanced : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_advanced_title),
            subTitle = UiText.StringResource(R.string.vault_settings_reshare_change),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.advanced
        )
    )
    data class Reshare(val isEnabled: Boolean) : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_reshare_title),
            subTitle =  UiText.StringResource(R.string.vault_settings_reshare_subtitle),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.reshare
        ),
        enabled = isEnabled
    )

    data object Sign : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.verify_swap_sign_button),
            subTitle = UiText.StringResource(R.string.vault_settings_sign_message_description),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.sign
        )
    )

    data object OnChainSecurity : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_security_title),
            subTitle = UiText.StringResource(R.string.vault_settings_security_subtitle),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.onchain_security
        )
    )

    data object Delete : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_delete_title),
            subTitle = UiText.StringResource(R.string.vault_settings_delete_subtitle),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.delete
        )
    )
}

@HiltViewModel
internal open class VaultSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    private val vaultRepository: VaultRepository,
    @ApplicationContext private val context: Context,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val vultiSignerRepository: VultiSignerRepository,
    private val snackbarFlow: SnackbarFlow,
) : ViewModel() {

    val settingGroups = listOf(
        VaultSettingsGroupUiModel(
            title = UiText.StringResource(R.string.vault_management),
            items = listOf(
                VaultSettingsItem.Details,
                VaultSettingsItem.Rename,
                VaultSettingsItem.BiometricFastSign(isEnabled = false, isBiometricEnabled = false)
            )
        ),
        VaultSettingsGroupUiModel(
            title = UiText.StringResource(R.string.vault_settings_security_screen_title),
            items = listOf(
                VaultSettingsItem.Security,
                VaultSettingsItem.PasswordHint,
                VaultSettingsItem.BackupVaultShare
            )
        ),
        VaultSettingsGroupUiModel(
            title = UiText.StringResource(R.string.other),
            items = listOf(
                VaultSettingsItem.Migrate(false),
                VaultSettingsItem.Advanced,
            )
        ),

        VaultSettingsGroupUiModel(
            title = null,
            items = listOf(
                VaultSettingsItem.Delete
            )
        ),

        VaultSettingsGroupUiModel(
            id = "ADVANCED",
            title = null,
            items = listOf(
                VaultSettingsItem.Reshare(false),
                VaultSettingsItem.Sign,
                VaultSettingsItem.OnChainSecurity
            ),
            isVisible = false
        ),
    )


    val uiModel = MutableStateFlow(
        VaultSettingsState(
            settingGroups = settingGroups
        )
    )

    var isBiometricFastSignEnabled: Boolean
        get() {
            val bool = uiModel.value.settingGroups
                .flatMap { group -> group.items }
                .filterIsInstance<VaultSettingsItem.BiometricFastSign>()
                .firstOrNull()?.isBiometricEnabled ?: false
            return bool
        }
        set(value) {
            val newItem = updateBiometricFastSignUiModel(value)
            uiModel.update {
                it.copy(settingGroups = newItem)
            }
        }


    val passwordTextFieldState = TextFieldState()


    private val vaultId: String =
        savedStateHandle.get<String>(ARG_VAULT_ID)!!

    init {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
            val hasMigration = vault?.libType == SigningLibType.GG20
            val hasFastSign = isVaultHasFastSignById(vaultId) && vault?.signers?.count() == 2
            val hasPassword = vaultPasswordRepository.getPassword(vaultId) != null

            val newItems = uiModel.value.settingGroups.map { group ->
                group.copy(
                    items = group.items.map {
                        when (it) {
                            is VaultSettingsItem.BiometricFastSign -> it.copy(
                                isEnabled = hasFastSign,
                                isBiometricEnabled = hasPassword
                            )

                            is VaultSettingsItem.Migrate -> it.copy(isEnabled = hasMigration)
                            is VaultSettingsItem.Reshare -> it.copy(isEnabled = !hasFastSign)
                            else -> it
                        }
                    }
                )
            }

            uiModel.update {
                it.copy(
                    settingGroups = newItems,
                )
            }
        }

        viewModelScope.launch {
            uiModel.map { it.isAdvanceSetting }.distinctUntilChanged()
                .collect { isAdvanceSettings ->

                    if (isAdvanceSettings) {
                        uiModel.update {
                            it.copy(
                                settingGroups = it.settingGroups.map { group ->
                                    group.copy(isVisible = group.id == "ADVANCED")
                                }
                            )
                        }
                    } else {
                        uiModel.update {
                            it.copy(
                                settingGroups = it.settingGroups.map { group ->
                                    group.copy(isVisible = group.id != "ADVANCED")
                                }
                            )
                        }
                    }

                }
        }

        validateEachTextChange()
    }

    fun onBackClick() {
        if (uiModel.value.isAdvanceSetting) {
            uiModel.update {
                it.copy(
                    isAdvanceSetting = false
                )
            }
        } else {
            viewModelScope.launch {
                navigator.back()
            }
        }
    }

    fun onSettingsItemClick(item: VaultSettingsItem) {
        when (item) {
            VaultSettingsItem.Advanced -> {
                uiModel.update {
                    it.copy(
                        isAdvanceSetting = true
                    )
                }
            }

            is VaultSettingsItem.BackupVaultShare -> {
//                uiModel.update {
//                    it.copy(
//                        isBackupVaultBottomSheetVisible = true
//                    )
//                }

                navigateToBackupPasswordScreen()
            }

            is VaultSettingsItem.BiometricFastSign -> {
                viewModelScope.launch {
                    if (!item.isBiometricEnabled) {
                        val newItem = uiModel.value.copy(
                            isBiometricFastSignBottomSheetVisible = true,
                        )
                        uiModel.update { newItem }
                    } else {
                        vaultPasswordRepository.clearPassword(vaultId)
                        isBiometricFastSignEnabled = false
                        showSnackbarMessage()
                    }
                }
            }

            VaultSettingsItem.Delete -> navigateToConfirmDeleteScreen()
            VaultSettingsItem.Details -> openDetails()
            is VaultSettingsItem.Migrate -> migrate()
            VaultSettingsItem.PasswordHint -> Unit
            VaultSettingsItem.Rename -> openRename()
            VaultSettingsItem.Security -> Unit
            VaultSettingsItem.OnChainSecurity -> navigateToOnChainSecurityScreen()
            is VaultSettingsItem.Reshare -> navigateToReshareStartScreen()
            VaultSettingsItem.Sign -> signMessage()
        }
    }

    private fun updateBiometricFastSignUiModel(isBiometricEnabled: Boolean): List<VaultSettingsGroupUiModel> =
        uiModel.value.settingGroups.map {
            it.copy(
                items = it.items.map { item ->
                    if (item is VaultSettingsItem.BiometricFastSign) {
                        item.copy(isBiometricEnabled = isBiometricEnabled)
                    } else {
                        item
                    }
                }
            )
        }

    fun openDetails() {
        viewModelScope.launch {
            navigator.navigate(Destination.Details(vaultId))
        }
    }

    fun openRename() {
        viewModelScope.launch {
            navigator.route(Route.Rename(vaultId))
        }
    }

    fun navigateToBackupPasswordScreen() {
        viewModelScope.launch {
            navigator.route(Route.VaultsToBackup(vaultId))
        }
    }

    fun navigateToConfirmDeleteScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.ConfirmDelete(vaultId))
        }
    }

    fun navigateToReshareStartScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.ReshareStartScreen(vaultId))
        }
    }

    fun navigateToBiometricsScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.BiometricsEnable(vaultId))
        }
    }

    fun navigateToOnChainSecurityScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.OnChainSecurity)
        }
    }

    fun signMessage() {
        viewModelScope.launch {
            navigator.navigate(Destination.SignMessage(vaultId))
        }
    }

    fun migrate() {
        viewModelScope.launch {
            navigator.route(Route.Migration.Onboarding(vaultId = vaultId))
        }
    }

    fun onLocalBackupClick() {
        onDismissBackupVaultBottomSheet()
        navigateToBackupPasswordScreen()
    }

    fun onServerBackupClick() {
        //TODO: add server backup
    }

    fun onDismissBackupVaultBottomSheet() {
        uiModel.update {
            it.copy(
                isBackupVaultBottomSheetVisible = false
            )
        }
    }

    fun onDismissBiometricFastSignBottomSheet() {
        uiModel.update {
            it.copy(
                isBiometricFastSignBottomSheetVisible = false,
                biometricsEnableUiModel = it.biometricsEnableUiModel.copy(
                    passwordErrorMessage = null,
                    passwordHint = null,
                )
            )
        }
        passwordTextFieldState.clearText()
    }

    fun togglePasswordVisibility() = viewModelScope.launch {
        uiModel.update {
            it.copy(
                biometricsEnableUiModel = it.biometricsEnableUiModel.copy(
                    isPasswordVisible = !it.biometricsEnableUiModel.isPasswordVisible
                )
            )
        }
    }

    private fun validateEachTextChange() = viewModelScope.launch {
        passwordTextFieldState.textAsFlow()
            .combine(uiModel.map { it.isBiometricFastSignBottomSheetVisible }
                .distinctUntilChanged()) { text, isVisible ->
                if (isVisible.not())
                    return@combine
                uiModel.update {
                    it.copy(
                        biometricsEnableUiModel = it.biometricsEnableUiModel.copy(
                            isSaveEnabled = text.isNotEmpty()
                        )
                    )
                }

            }
            .collect()
    }

    fun onSaveEnableBiometricFastSign() = viewModelScope.launch {
        val vault = vaultRepository.get(vaultId)
            ?: error("No vault with id $vaultId exists")
        val isPasswordValid = vultiSignerRepository.isPasswordValid(
            publicKeyEcdsa = vault.pubKeyECDSA,
            password = passwordTextFieldState.text.toString(),
        )

        if (!isPasswordValid) {
            val hint = getPasswordHint()
            passwordTextFieldState.clearText()
            uiModel.update {
                it.copy(
                    biometricsEnableUiModel = it.biometricsEnableUiModel.copy(
                        passwordErrorMessage = UiText.StringResource(
                            R.string.keysign_password_incorrect_password
                        ),
                        passwordHint = hint,
                        isSaveEnabled = false,
                    )
                )
            }
            return@launch
        }
        if (!isBiometricFastSignEnabled) {
            vaultPasswordRepository.savePassword(
                vaultId = vaultId,
                password = passwordTextFieldState.text.toString()
            )
            isBiometricFastSignEnabled = true
            passwordTextFieldState.clearText()
            hideBiometricFastVaultBottomSheet()
            showSnackbarMessage()
        }
    }

    private fun hideBiometricFastVaultBottomSheet() {
        uiModel.update {
            it.copy(
                isBiometricFastSignBottomSheetVisible = false
            )
        }
    }

    private suspend fun getPasswordHint(): UiText? {

        val passwordHintString =
            vaultDataStoreRepository.readFastSignHint(vaultId = vaultId).first()

        if (passwordHintString.isEmpty()) return null

        return UiText.FormattedText(
            R.string.import_file_password_hint_text,
            listOf(passwordHintString)
        )
    }

    private suspend fun showSnackbarMessage() {
        val messageRes = if (isBiometricFastSignEnabled) {
            R.string.vault_settings_biometrics_screen_snackbar_enabled
        } else {
            R.string.vault_settings_biometrics_screen_snackbar_disabled
        }
        snackbarFlow.showMessage(context.getString(messageRes))
    }


}