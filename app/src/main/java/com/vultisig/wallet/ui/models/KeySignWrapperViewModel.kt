package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.models.keysign.KeysignViewModel
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel(assistedFactory = KeySignWrapperViewModel.Factory::class)
internal class KeySignWrapperViewModel @AssistedInject constructor(
    @Assisted val viewModel: KeysignViewModel,
) : ViewModel() {

    val transactionUiModel = MutableStateFlow<TransactionTypeUiModel?>(null)

    @AssistedFactory
    interface Factory {
        fun create(viewModel: KeysignViewModel): KeySignWrapperViewModel
    }

    init {
        viewModel.startKeysign()
    }

    fun loadTransaction() {
        viewModel.transactionTypeUiModel?.let { transactionUiType ->
            transactionUiModel.update { transactionUiType }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModel.viewModelScope.cancel()
    }
}