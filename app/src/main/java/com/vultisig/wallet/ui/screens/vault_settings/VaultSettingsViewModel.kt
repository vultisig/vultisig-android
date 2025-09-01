package com.vultisig.wallet.ui.screens.vault_settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.settings.SettingsItemUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

internal data class VaultSettingsState(
    val settingGroups: List<VaultSettingsGroupUiModel> = emptyList(),
    val isAdvanceSetting: Boolean = false,
    val isBackupVaultBottomSheetVisible: Boolean = false,
)

internal sealed class VaultSettingsItem(
    val value: SettingsItemUiModel,
    val enabled: Boolean = true,
) {
    data object Details : VaultSettingsItem(
        SettingsItemUiModel(
            title = UiText.StringResource(R.string.vault_settings_details_title),
            subTitle = "View vault name,part and type".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.details
        ),
    )

    data object Rename : VaultSettingsItem(
        SettingsItemUiModel(
            title = "Rename".asUiText(),
            subTitle = "Edit your vault name".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.reame
        )
    )

    data class BiometricFastSign(val isBiometricEnabled: Boolean) : VaultSettingsItem(
        SettingsItemUiModel(
            title = "Biometrics Fast Signing".asUiText(),
            trailingSwitch = isBiometricEnabled,
            leadingIcon = R.drawable.biomatrics_fast
        ),
    )

    data object Security : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "Security".asUiText(),
            subTitle = "Enable biometric for fast sign".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.security,
        )
    )

    data object LockTime : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "Lock time".asUiText(),
            subTitle = "Set the time until the app locks automatically".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.lock_time
        ),
        enabled = false
    )

    data object PasswordHint : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "Password hint".asUiText(),
            subTitle = "Set a password hint to protect your vault".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.pass_hint
        ),
        enabled = false,
    )

    data object BackupVaultShare : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "Backup Vault Share".asUiText(),
            subTitle = "Back up your Vault Share to device or server.".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.backup_vault
        )
    )

    data class Migrate(val isEnabled: Boolean) : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "Migrate".asUiText(),
            subTitle = "Migrate GG20 vault DKLS".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.pass_hint,
        ),
        enabled = isEnabled
    )

    data object Advanced : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "Advanced".asUiText(),
            subTitle = "Reshare, change TSS, or sign messages.".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.advanced
        )
    )

    data class Reshare(val isEnabled: Boolean) : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "Reshare".asUiText(),
            subTitle = "Reshare vault with a new committee".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.reame
        ),
        enabled = isEnabled
    )

    data object Sign : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "Sign".asUiText(),
            subTitle = "Sign custom message".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.reame
        )
    )

    data object OnChainSecurity : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "On-Chain Security".asUiText(),
            subTitle = "Manage your on-chain security".asUiText(),
            trailingIcon = R.drawable.ic_small_caret_right,
            leadingIcon = R.drawable.reame
        )
    )

    data object Delete : VaultSettingsItem(
        value = SettingsItemUiModel(
            title = "Delete".asUiText(),
            subTitle = "Delete your vault share permanently".asUiText(),
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
) : ViewModel() {

    val settingGroups = listOf(
        VaultSettingsGroupUiModel(
            title = UiText.StringResource(R.string.vault_management),
            items = listOf(
                VaultSettingsItem.Details,
                VaultSettingsItem.Rename,
                VaultSettingsItem.BiometricFastSign(false)
            )
        ),
        VaultSettingsGroupUiModel(
            title = UiText.StringResource(R.string.vault_settings_security_screen_title),
            items = listOf(
                VaultSettingsItem.Security,
                VaultSettingsItem.LockTime,
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



    private val vaultId: String =
        savedStateHandle.get<String>(ARG_VAULT_ID)!!

    init {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
            val hasMigration = vault?.libType == SigningLibType.GG20
            val hasFastSign = isVaultHasFastSignById(vaultId) && vault?.signers?.count() == 2

            val newItems = uiModel.value.settingGroups.map { group ->
                group.copy(
                    items = group.items.map {
                        when (it) {
                            is VaultSettingsItem.BiometricFastSign -> it.copy(isBiometricEnabled = hasFastSign)
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
                val newItem = uiModel.value.copy(
                    settingGroups = uiModel.value.settingGroups.map {
                        it.copy(
                            items = it.items.map { item ->
                                if (item is VaultSettingsItem.BiometricFastSign) {
                                    item.copy(isBiometricEnabled = !item.isBiometricEnabled)
                                } else {
                                    item
                                }
                            }
                        )
                    }
                )
                uiModel.update {
                    newItem
                }
            }

            VaultSettingsItem.Delete -> navigateToConfirmDeleteScreen()
            VaultSettingsItem.Details -> openDetails()
            VaultSettingsItem.LockTime -> Unit
            is VaultSettingsItem.Migrate -> migrate()
            VaultSettingsItem.PasswordHint -> Unit
            VaultSettingsItem.Rename -> openRename()
            VaultSettingsItem.Security -> navigateToBiometricsScreen()
            VaultSettingsItem.OnChainSecurity -> navigateToOnChainSecurityScreen()
            is VaultSettingsItem.Reshare -> navigateToReshareStartScreen()
            VaultSettingsItem.Sign -> signMessage()
        }
    }

    fun openDetails() {
        viewModelScope.launch {
            navigator.navigate(Destination.Details(vaultId))
        }
    }

    fun openRename() {
        viewModelScope.launch {
            navigator.navigate(Destination.Rename(vaultId))
        }
    }

    fun navigateToBackupPasswordScreen() {
        viewModelScope.launch {
            navigator.route(Route.BackupPasswordRequest(vaultId))
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


}