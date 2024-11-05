package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.models.keygen.GeneratingKeyViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = GeneratingKeyWrapperViewModel.Factory::class)
internal class GeneratingKeyWrapperViewModel @AssistedInject constructor(
    @Assisted val viewModel: GeneratingKeyViewModel,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(viewModel: GeneratingKeyViewModel): GeneratingKeyWrapperViewModel
    }

    init {
        viewModelScope.launch {
            viewModel.generateKey()
        }
    }

    override fun onCleared() {
        viewModel.stopService()
        super.onCleared()
    }
}