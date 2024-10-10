package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.ui.models.keysign.KeysignViewModel
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = KeySignWrapperViewModel.Factory::class)
internal class KeySignWrapperViewModel @AssistedInject constructor(
    @Assisted val viewModel: KeysignViewModel,
    private val transactionRepository: TransactionRepository,
    private val mapTransactionToUiModel: TransactionToUiModelMapper,
) : ViewModel() {

    val transactionUiModel = MutableStateFlow<TransactionUiModel?>(null)

    @AssistedFactory
    interface Factory {
        fun create(viewModel: KeysignViewModel): KeySignWrapperViewModel
    }

    init {
        viewModel.startKeysign()
    }

    fun loadTransaction() {
        viewModel.transactionId?.let { transactionId ->
            viewModelScope.launch {
                val transaction =
                    transactionRepository.getTransaction(transactionId).first()
                val transactionUiModel = mapTransactionToUiModel(transaction)
                this@KeySignWrapperViewModel.transactionUiModel.value = transactionUiModel
            }
        }
    }
}