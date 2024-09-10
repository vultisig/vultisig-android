package com.vultisig.wallet.presenter.qr_address

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.presenter.common.ShareType
import com.vultisig.wallet.presenter.common.generateQrBitmap
import com.vultisig.wallet.presenter.common.share
import com.vultisig.wallet.presenter.common.shareFileName
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class QrAddressViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
) : ViewModel() {
    val address = savedStateHandle.get<String>(Destination.ARG_COIN_ADDRESS)
    val currentVault: MutableState<Vault?> = mutableStateOf(null)

    init {
        viewModelScope.launch {
            vaultRepository.get(requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID)))
                ?.let {
                    currentVault.value = it
                }
        }
    }

    internal fun shareQRCode(activity: Context): Unit {
        val qrBitmap = generateQrBitmap(address ?: "")
        activity.share(
            qrBitmap,
            shareFileName(
                requireNotNull(currentVault.value),
                ShareType.TOKENADDRESS
            )
        )
    }
}