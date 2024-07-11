package com.vultisig.wallet.ui.models


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.FindCustomTokenRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    private val findCustomTokenRepository: FindCustomTokenRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    appCurrencyRepository: AppCurrencyRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CustomTokenState())
    val uiModel = mutableState.asStateFlow()
    val searchFieldState: TextFieldState = TextFieldState()
    private val chainId =
        requireNotNull(savedStateHandle.get<String>(Destination.CustomToken.ARG_CHAIN_ID))
    private val appCurrency = appCurrencyRepository
        .currency
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            appCurrencyRepository.defaultCurrency,
        )

    fun onSearchClick() {
        viewModelScope.launch {
            enableLoading()

            val result =
                findCustomTokenRepository(
                    Chain.fromRaw(chainId),
                    searchFieldState.text.toString()
                )

            if (result == null) {
                showError()
            } else {
                val rawPrice = calculatePrice(result)
                val tokenFiatValue = FiatValue(
                    rawPrice,
                    appCurrency.value.ticker
                )
                val price = fiatValueToStringMapper.map(tokenFiatValue)
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        hasError = false,
                        searchResult = CustomTokenResult(
                            token = result,
                            price = price
                        ),
                    )
                }


            }
        }
    }

    private suspend fun calculatePrice(result: Coin): BigDecimal {
        tokenPriceRepository.refresh(listOf(result))
        val currency = appCurrency.value
        return tokenPriceRepository.getPrice(
            result,
            currency
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


    private fun enableLoading() {
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