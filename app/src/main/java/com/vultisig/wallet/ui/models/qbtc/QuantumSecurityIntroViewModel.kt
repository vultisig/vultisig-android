package com.vultisig.wallet.ui.models.qbtc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class QuantumSecurityIntroViewModel
@Inject
constructor(savedStateHandle: SavedStateHandle, private val navigator: Navigator<Destination>) :
    ViewModel() {

    private val vaultId = savedStateHandle.toRoute<Route.QuantumSecurityIntro>().vaultId

    fun getStarted() {
        viewModelScope.safeLaunch { navigator.route(Route.QbtcClaim(vaultId = vaultId)) }
    }

    fun back() {
        viewModelScope.safeLaunch { navigator.back() }
    }
}
