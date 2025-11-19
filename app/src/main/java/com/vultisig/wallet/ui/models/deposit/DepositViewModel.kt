package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class DepositViewModel @Inject constructor(
    val addressProvider: AddressProvider,
    private val sendNavigator: Navigator<SendDst>,
    private val mainNavigator: Navigator<Destination>,
    ) : ViewModel() {
    val dst = sendNavigator.destination

    val isKeysignFinished = MutableStateFlow(false)

    fun finishKeysign() {
        isKeysignFinished.value = true
    }

    fun navigateToHome(useMainNavigator: Boolean) {
        viewModelScope.launch {
            if (isKeysignFinished.value) {
                mainNavigator.route(
                    Route.Home(),
                    NavigationOptions(
                        clearBackStack = true
                    )
                )
            } else {
                if (useMainNavigator) {
                    mainNavigator.navigate(Destination.Back)
                } else {
                    sendNavigator.navigate(SendDst.Back)
                }
            }
        }
    }
}