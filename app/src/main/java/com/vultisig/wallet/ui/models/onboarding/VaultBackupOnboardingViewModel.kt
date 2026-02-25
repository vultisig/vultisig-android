package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.BackupPasswordTypeNavType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.BackupVault.BackupPasswordType
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.reflect.typeOf

internal data class VaultBackupOnboardingUiModel(
    val tips: List<VaultBackupOnboardingTip> = emptyList(),
    val rive: Int = 0,
)

sealed interface VaultBackupOnboardingEvent {
    object Next : VaultBackupOnboardingEvent
    object Back : VaultBackupOnboardingEvent
}


data class VaultBackupOnboardingTip(
    val title: UiText,
    val description: UiText,
    val logo: Int,
)


@HiltViewModel
internal class VaultBackupOnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Onboarding.VaultBackup>(
        typeMap = mapOf(
            typeOf<BackupPasswordType>() to BackupPasswordTypeNavType
        )
    )
    private val vaultId = args.vaultId


    val state = MutableStateFlow(
        VaultBackupOnboardingUiModel()
    )

    init {
        when(args.vaultType){
            Route.VaultInfo.VaultType.Fast -> state.update {
                VaultBackupOnboardingUiModel(
                    tips = FastVaultBackupOnboardingTips,
                    rive = R.raw.riv_keygen,
                )
            }
            Route.VaultInfo.VaultType.Secure -> state.update {
                VaultBackupOnboardingUiModel(
                    tips = SecureVaultBackupOnboardingTips,
                    rive = R.raw.riv_keygen,
                )
            }
        }
    }

    fun onEvent(
        event: VaultBackupOnboardingEvent
    ){
        when(event){
            VaultBackupOnboardingEvent.Back -> back()
            VaultBackupOnboardingEvent.Next -> next()
        }
    }

    fun next() {
        viewModelScope.launch {

        when (args.vaultType) {
                    Route.VaultInfo.VaultType.Fast -> {
                        navigator.route(
                            Route.FastVaultVerification(
                                vaultId = vaultId,
                                pubKeyEcdsa = args.pubKeyEcdsa,
                                email = requireNotNull(args.email),
                                tssAction = args.action,
                                vaultName = args.vaultName,
                                password = args.password,
                            )
                        )
                    }

                    Route.VaultInfo.VaultType.Secure -> {
                        navigator.route(
                            Route.BackupVault(
                                vaultId = vaultId,
                                vaultType = args.vaultType,
                                action = args.action,
                                passwordType = BackupPasswordType.UserSelectionPassword
                            )
                        )
                    }
                }
        }
    }


    private fun back(){
        viewModelScope.launch {
            navigator.back()
        }
    }

    companion object {
        val FastVaultBackupOnboardingTips = listOf(
            VaultBackupOnboardingTip(
                title = "Your Device is the driver".asUiText(),
                description = "The Device backup and password are the key. The server only co-signs and backup can be requested.".asUiText(),
                logo = R.drawable.wallet,
            ),
            VaultBackupOnboardingTip(
                title = "Store backups separately".asUiText(),
                description = "Keep each backup in a different place. If one is compromised, your funds stay safe.".asUiText(),
                logo = R.drawable.iconwarning
            ),
        )

        val SecureVaultBackupOnboardingTips = listOf(
            VaultBackupOnboardingTip(
                title = "Back up each device".asUiText(),
                description = "You’ll create {N} backups in total. You will do this on each device.".asUiText(),
                logo = R.drawable.logo,
            ),
            VaultBackupOnboardingTip(
                title = "Store backups separately".asUiText(),
                description = "Save each backup in a different cloud service or with a different password. If one is exposed, your funds stay safe.".asUiText(),
                logo = R.drawable.copy
            ),
        )

    }
}