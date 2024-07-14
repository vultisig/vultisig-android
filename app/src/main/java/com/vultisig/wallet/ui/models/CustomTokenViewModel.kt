package com.vultisig.wallet.ui.models


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.AppCurrency
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

internal data class CustomTokenState(
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val searchResult: CustomTokenResult? = null
)

internal data class CustomTokenResult(
    val token: Coin,
    val price: String,
)


@HiltViewModel
@OptIn(ExperimentalFoundationApi::class)
internal class CustomTokenViewModel @Inject constructor(
    private val tokenRepository: TokenRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val appCurrencyRepository: AppCurrencyRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CustomTokenState())
    val uiModel = mutableState.asStateFlow()
    val searchFieldState: TextFieldState = TextFieldState()
    private val chainId =
        requireNotNull(savedStateHandle.get<String>(Destination.CustomToken.ARG_CHAIN_ID))
    private lateinit var appCurrency: AppCurrency

    init {
        viewModelScope.launch {
            appCurrency = appCurrencyRepository.currency.first()
        }
    }

    fun searchCustomToken() {
        viewModelScope.launch {
            showLoading()

            val searchedToken =
                tokenRepository.getTokenByContract(
                    Chain.fromRaw(chainId),
                    searchFieldState.text.toString()
                )

            if (searchedToken == null) {
                showError()
            } else {
                val rawPrice = calculatePrice(searchedToken)
                val tokenFiatValue = FiatValue(
                    rawPrice,
                    appCurrency.ticker
                )
                val price = fiatValueToStringMapper.map(tokenFiatValue)
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        hasError = false,
                        searchResult = CustomTokenResult(
                            token = searchedToken,
                            price = price
                        ),
                    )
                }


            }
        }
    }

    private suspend fun calculatePrice(result: Coin): BigDecimal {
        tokenPriceRepository.refresh(listOf(result))
        return tokenPriceRepository.getPrice(
            result,
            appCurrency
        ).first()
    }

    private fun showError() {
        mutableState.update {
            it.copy(
                isLoading = false,
                hasError = true,
                searchResult = null,
            )
        }
    }


    private fun showLoading() {
        mutableState.update {
            it.copy(
                isLoading = true,
                hasError = false,
                searchResult = null,
            )
        }
    }

    fun pasteToSearchField(data: String) {
        searchFieldState.setTextAndPlaceCursorAtEnd(data)
    }
}