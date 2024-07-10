package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import com.vultisig.wallet.presenter.keysign.KeysignViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = KeySignWrapperViewModel.Factory::class)
internal class KeySignWrapperViewModel @AssistedInject constructor(
    @Assisted val viewModel: KeysignViewModel
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(viewModel: KeysignViewModel): KeySignWrapperViewModel
    }

    init {
        viewModel.startKeysign()
    }
}