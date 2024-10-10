package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignViewModel
import com.vultisig.wallet.ui.models.keysign.TransitionTypeUiModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel(assistedFactory = KeySignWrapperViewModel.Factory::class)
internal class KeySignWrapperViewModel @AssistedInject constructor(
    @Assisted val viewModel: KeysignViewModel,
) : ViewModel() {

    val transactionUiModel = MutableStateFlow<TransitionTypeUiModel?>(null)

    @AssistedFactory
    interface Factory {
        fun create(viewModel: KeysignViewModel): KeySignWrapperViewModel
    }

    init {
        viewModel.startKeysign()
    }

    fun loadTransaction() {
        viewModel.transitionTypeUiModel?.let { transactionUiType ->
            transactionUiModel.update { transactionUiType }
        }
    }
}