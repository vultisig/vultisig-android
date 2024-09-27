package com.vultisig.wallet.ui.models.send

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.generateQrBitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SendViewModel @Inject constructor(
    private val sendNavigator: Navigator<SendDst>,
    private val mainNavigator: Navigator<Destination>,
    val addressProvider: AddressProvider,
    private val savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
) : ViewModel() {
    val dst = sendNavigator.destination
    private var isNavigateToHome: Boolean = false
    val currentVault: MutableState<Vault?> = mutableStateOf(null)

    init {
        viewModelScope.launch {
            vaultRepository.get(requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID)))
                ?.let {
                    currentVault.value = it
                }
        }
    }

    fun enableNavigationToHome() {
        isNavigateToHome = true
    }

    fun navigateToHome(useMainNavigator: Boolean) {
        viewModelScope.launch {
            if (isNavigateToHome) {
                mainNavigator.navigate(
                    Destination.Home(),
                    NavigationOptions(
                        clearBackStack = true
                    )
                )
            }
            if (useMainNavigator) {
                mainNavigator.navigate(Destination.Back)
            } else {
                sendNavigator.navigate(SendDst.Back)
            }
        }
    }

    internal fun shareQRCode(activity: Context) {
        val qrBitmap = generateQrBitmap(addressProvider.address.value)
        activity.share(
            qrBitmap,
            shareFileName(
                requireNotNull(currentVault.value),
                ShareType.SEND
            )
        )
    }
}