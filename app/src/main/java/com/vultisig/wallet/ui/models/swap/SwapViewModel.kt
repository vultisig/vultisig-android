package com.vultisig.wallet.ui.models.swap

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.components.generateQrBitmap
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SwapViewModel @Inject constructor(
    sendNavigator: Navigator<SendDst>,
    private val mainNavigator: Navigator<Destination>,
    val addressProvider: AddressProvider,
    private val vaultRepository: VaultRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val dst = sendNavigator.destination
    val currentVault: MutableState<Vault?> = mutableStateOf(null)

    init {
        viewModelScope.launch {
            vaultRepository.get(requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID)))?.let {
                currentVault.value = it
            }
        }
    }


    private var isNavigateToHome: Boolean = false
    fun enableNavigationToHome() {
        isNavigateToHome = true
    }

    fun isNavigateToHome() = isNavigateToHome

    fun navigateToHome() {
        viewModelScope.launch {
            mainNavigator.navigate(
                Destination.Home(),
                NavigationOptions(
                    clearBackStack = true
                )
            )
        }
    }

    internal fun shareQRCode(activity: Context): Unit {
        val qrBitmap = generateQrBitmap(addressProvider.address.value)
        activity.share(
            qrBitmap,
            shareFileName(
                requireNotNull(currentVault.value),
                ShareType.SWAP
            )
        )
    }

}