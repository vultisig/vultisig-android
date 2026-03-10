package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class KeyImportFeatureSpotlightViewModel
@Inject
constructor(private val navigator: Navigator<Destination>) : ViewModel() {

    fun getStarted() {
        viewModelScope.safeLaunch { navigator.route(Route.KeyImport.ImportSeedphrase) }
    }

    fun back() {
        viewModelScope.safeLaunch { navigator.back() }
    }
}
