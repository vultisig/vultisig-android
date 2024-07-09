package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.ViewModel
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class DepositViewModel @Inject constructor(
    sendNavigator: Navigator<SendDst>,
) : ViewModel() {

    val dst = sendNavigator.destination

}