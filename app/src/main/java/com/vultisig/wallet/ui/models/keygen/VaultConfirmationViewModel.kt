package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
internal class VaultConfirmationViewModel
@Inject
constructor(private val navigator: Navigator<Destination>) : ViewModel() {

    init {
        // VaultConfirmation is reached only on the Migrate (vault-upgrade) path; show the
        // "Vault upgraded" confirmation briefly, then return Home.
        viewModelScope.launch {
            delay(5.seconds)
            navigator.route(route = Route.Home(), opts = NavigationOptions(clearBackStack = true))
        }
    }
}
