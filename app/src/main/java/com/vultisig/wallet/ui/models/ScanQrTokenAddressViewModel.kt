package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.common.DeepLinkHelper
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.ui.models.TokenSelectionViewModel.Companion.REQUEST_SEARCHED_TOKEN_ID
import com.vultisig.wallet.ui.models.transaction.AddAddressEntryViewModel.Companion.REQUEST_QR_RESULT_ADDRESS
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_COIN_ADDRESS
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.scan.ARG_QR_CODE
import com.vultisig.wallet.ui.utils.getAddressFromQrCode
import com.vultisig.wallet.ui.utils.isReshare
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@HiltViewModel
internal class ScanQrTokenAddressViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val requestResultRepository: RequestResultRepository,
    ) : ViewModel() {

    fun navigateToBack(address: String) {
        viewModelScope.launch {
            requestResultRepository.respond(ARG_COIN_ADDRESS, address)
            navigator.navigate(Destination.Back)
        }
    }
}