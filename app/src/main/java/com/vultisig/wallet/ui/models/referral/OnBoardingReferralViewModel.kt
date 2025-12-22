package com.vultisig.wallet.ui.models.referral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OnBoardingReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {
    private val vaultId: String = savedStateHandle.toRoute<Route.ReferralOnboarding>().vaultId

    fun onClickGetStarted() {
        viewModelScope.launch {
            navigator.navigate(
                dst = Destination.ReferralCode(vaultId),
                opts = NavigationOptions(
                    popUpToRoute = Route.ReferralOnboarding::class,
                    inclusive = true,
                )
            )
        }
    }

    fun back(){
        viewModelScope.launch {
            navigator.back()
        }
    }
}