package com.vultisig.wallet.ui.models


import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.usecases.CoinAndFiatValue
import com.vultisig.wallet.data.usecases.SearchTokenUseCase
import com.vultisig.wallet.ui.models.TokenSelectionViewModel.Companion.REQUEST_SEARCHED_TOKEN_ID
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

internal data class CustomTokenUiModel(
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val token: Coin? = null,
    val price: String = "",
    @DrawableRes val chainLogo: Int,
)


@HiltViewModel
internal class CustomTokenViewModel @Inject constructor(
    private val searchToken: SearchTokenUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val requestResultRepository: RequestResultRepository,
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val searchFieldState: TextFieldState = TextFieldState()
    private val chainId =
        requireNotNull(savedStateHandle.get<String>(Destination.CustomToken.ARG_CHAIN_ID))
    val uiModel = MutableStateFlow(
        CustomTokenUiModel(chainLogo = Chain.fromRaw(chainId).logo)
    )
    fun searchCustomToken() {
        viewModelScope.launch {
            showLoading()
            val searchedToken: CoinAndFiatValue? =
                searchToken(
                    chainId,
                    searchFieldState.text.toString()
                )

            if (searchedToken == null) {
                showError()
            } else {
                val price = fiatValueToStringMapper(searchedToken.fiatValue)
                Timber.d("token url: ${searchedToken.coin.logo}")
                uiModel.update {
                    it.copy(
                        isLoading = false,
                        hasError = false,
                        token = searchedToken.coin,
                        price = price
                    )
                }
            }
        }
    }

    private fun showError() {
        uiModel.update {
            it.copy(
                isLoading = false,
                hasError = true,
                token = null,
                price = "",
            )
        }
    }


    private fun showLoading() {
        uiModel.update {
            it.copy(
                isLoading = true,
                hasError = false,
                token = null,
                price = "",
            )
        }
    }

    fun pasteToSearchField(data: String) {
        searchFieldState.setTextAndPlaceCursorAtEnd(data)
    }

    fun addCoinToTempRepo() {
        viewModelScope.launch {
            val foundCoin = uiModel.value.token ?: return@launch
            requestResultRepository.respond(REQUEST_SEARCHED_TOKEN_ID, foundCoin)
            navigator.navigate(Destination.Back)
        }
    }
}