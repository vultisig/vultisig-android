package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.presenter.keygen.GeneratingKeyViewModel
import com.vultisig.wallet.presenter.keygen.KeygenState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = GeneratingKeyWrapperViewModel.Factory::class)
internal class GeneratingKeyWrapperViewModel @AssistedInject constructor(
    @Assisted val viewModel: GeneratingKeyViewModel,
    @ApplicationContext private val context: Context
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
        viewModel.stopService(context)
        super.onCleared()
    }
}