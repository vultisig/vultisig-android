package com.vultisig.wallet.ui.models.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SendViewModel @Inject constructor(
    sendNavigator: Navigator<SendDst>,
    private val mainNavigator: Navigator<Destination>,
    val addressProvider: AddressProvider
) : ViewModel() {

    val dst = sendNavigator.destination
    val home= mainNavigator.destination
    fun navigateToHome (){
        viewModelScope.launch {
            mainNavigator.navigate(Destination.Home())

        }
    }

}