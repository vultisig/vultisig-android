package com.vultisig.wallet.ui.models


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.ui.models.TokenSelectionViewModel.Companion.REQUEST_SEARCHED_TOKEN_ID
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

internal data class CustomTokenUiModel(
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val token: Coin? = null,
    val price: String = "",
)


@HiltViewModel
@OptIn(ExperimentalFoundationApi::class)
internal class CustomTokenViewModel @Inject constructor(
    private val tokenRepository: TokenRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val requestResultRepository: RequestResultRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val uiModel = MutableStateFlow(CustomTokenUiModel())
    val searchFieldState: TextFieldState = TextFieldState()
    private val chainId =
        requireNotNull(savedStateHandle.get<String>(Destination.CustomToken.ARG_CHAIN_ID))

    fun searchCustomToken() {
        viewModelScope.launch {
            showLoading()

            val searchedToken =
                tokenRepository.getTokenByContract(
                    chainId,
                    searchFieldState.text.toString()
                )

            if (searchedToken == null) {
                showError()
            } else {
                val rawPrice = calculatePrice(searchedToken)
                val currency = appCurrencyRepository.currency.first()
                val tokenFiatValue = FiatValue(
                    rawPrice,
                    currency.ticker
                )
                val price = fiatValueToStringMapper.map(tokenFiatValue)
                uiModel.update {
                    it.copy(
                        isLoading = false,
                        hasError = false,
                        token = searchedToken,
                        price = price
                    )
                }
            }
        }
    }

    private suspend fun calculatePrice(result: Coin): BigDecimal =
        tokenPriceRepository.getPriceByContactAddress(
            chainId,
            result.contractAddress
        )

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

    fun addCoinToTempRepo(onAddCompleted: () -> Unit) {
        viewModelScope.launch {
            val foundCoin = uiModel.value.token ?: return@launch
            requestResultRepository.respond(REQUEST_SEARCHED_TOKEN_ID, foundCoin)
            onAddCompleted()
        }
    }
}