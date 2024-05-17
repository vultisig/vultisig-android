package com.vultisig.wallet.presenter.qr_address

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
internal class QrAddressViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    val address = savedStateHandle.get<String>(Destination.QrAddressScreen.ARG_COIN_ADDRESS)
}