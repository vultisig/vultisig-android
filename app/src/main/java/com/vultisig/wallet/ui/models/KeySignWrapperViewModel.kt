package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.models.keysign.KeysignViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.cancel

@HiltViewModel(assistedFactory = KeySignWrapperViewModel.Factory::class)
internal class KeySignWrapperViewModel
@AssistedInject
constructor(@Assisted val viewModel: KeysignViewModel) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(viewModel: KeysignViewModel): KeySignWrapperViewModel
    }

    init {
        viewModel.startKeysign()
    }

    override fun onCleared() {
        super.onCleared()
        // The wrapped KeysignViewModel is not registered with any ViewModelStore, so its own
        // `onCleared` never runs. Cancel its polling explicitly so the foreground status service
        // stops as soon as the user navigates away from the keysign flow, instead of polling
        // until the next terminal status or until the chain-specific timeout fires.
        viewModel.stopPolling()
        viewModel.viewModelScope.cancel()
    }
}
