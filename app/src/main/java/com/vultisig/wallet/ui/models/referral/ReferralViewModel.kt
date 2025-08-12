package com.vultisig.wallet.ui.models.referral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class ReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    fun onCreateOrEditReferral() {

    }

    fun onSaveOrEditReferral() {

    }

    fun onPasteIconClick(content: String) {

    }
}