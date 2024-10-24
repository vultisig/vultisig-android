package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class DepositViewModel @Inject constructor(
    private val sendNavigator: Navigator<SendDst>,
    private val mainNavigator: Navigator<Destination>,
    ) : ViewModel() {
    val dst = sendNavigator.destination
    private var isNavigateToHome: Boolean = false
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