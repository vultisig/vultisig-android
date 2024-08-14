package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class KeygenRoleViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    fun initiate() {
        viewModelScope.launch {
            navigator.navigate(Destination.Setup())
        }
    }

    fun pair() {
        viewModelScope.launch {
            navigator.navigate(Destination.JoinThroughQr(null))
        }
    }

}