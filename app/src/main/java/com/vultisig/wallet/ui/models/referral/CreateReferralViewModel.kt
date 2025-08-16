package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.cosmos.NativeTxFeeRune
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wallet.core.jni.CoinType
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

internal data class CreateReferralUiState(
    val searchStatus: SearchStatusType = SearchStatusType.DEFAULT,
    val yearExpiration: Int = 1,
    val formattedYearExpiration: String = "",
    val fees: FeesReferral = FeesReferral.Loading,
)

internal interface FeesReferral {
    data object Loading : FeesReferral
    data class Result(
        val registrationFeesToken: String = "",
        val registrationFeesPrice: String = "",
        val costFeesToken: String = "",
        val costFeesPrice: String = "",
    ) : FeesReferral
}

internal enum class SearchStatusType {
    DEFAULT,
    IS_SEARCHING,
    VALIDATION_ERROR,
    SUCCESS,
    ERROR,
}

internal fun SearchStatusType.isError(): Boolean {
    return this == SearchStatusType.VALIDATION_ERROR || this == SearchStatusType.ERROR
}

@HiltViewModel
internal class CreateReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val thorChainApi: ThorChainApi,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl,
) : ViewModel() {
    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])
    private var nativeRuneFees: NativeTxFeeRune? = null

    val searchReferralTexFieldState = TextFieldState()
    val state = MutableStateFlow(CreateReferralUiState())

    init {
        loadYearExpiration()
        loadFees()
        observeReferralTextField()
    }

    private fun loadFees() {
        viewModelScope.launch {
            state.update {
                it.copy(fees = FeesReferral.Loading)
            }

            nativeRuneFees = withContext(Dispatchers.IO) {
                thorChainApi.getTHORChainReferralFees()
            }

            val fees = calculateFees()

            state.update {
                it.copy(
                    fees = FeesReferral.Result(
                        registrationFeesToken = fees.registrationFeesToken,
                        registrationFeesPrice = fees.registrationFeesPrice,
                        costFeesToken = fees.costFeesToken,
                        costFeesPrice = fees.costFeesPrice
                    )
                )
            }
        }
    }

    private suspend fun calculateFees(extraYears: Int = 1): FeesReferral.Result {
        val registrationTokenFees =
            (nativeRuneFees?.registerFeeRune ?: DEFAULT_REGISTRATION_FEES).toBigInteger()
        val feePerBlock = (nativeRuneFees?.feePerBlock ?: DEFAULT_BLOCK_FEES).toBigInteger()
        val extraCost = if (extraYears > 1) {
            feePerBlock * (BLOCKS_PER_YEAR * (extraYears - 1)).toBigInteger()
        } else {
            BigInteger.ZERO
        }

        val totalCost = registrationTokenFees + feePerBlock + extraCost

        val formattedRegistrationTokenFees =
            "${CoinType.THORCHAIN.toValue(registrationTokenFees)} ${CoinType.THORCHAIN.symbol}"
        val formattedCostTokenFees =
            "${CoinType.THORCHAIN.toValue(totalCost)} ${CoinType.THORCHAIN.symbol}"

        val formattedRegistrationFiatFees = registrationTokenFees.convertToFiat()
        val formattedCostFiatFees = totalCost.convertToFiat()

        return FeesReferral.Result(
            registrationFeesToken = formattedRegistrationTokenFees,
            registrationFeesPrice = formattedRegistrationFiatFees,
            costFeesToken = formattedCostTokenFees,
            costFeesPrice = formattedCostFiatFees,
        )
    }

    private fun loadYearExpiration() {
        val formattedDate = getFormattedDateByAdding(1)

        state.update {
            it.copy(
                formattedYearExpiration = formattedDate,
            )
        }
    }

    fun onSearchReferralCode() {
        viewModelScope.launch {
            val referralCode = searchReferralTexFieldState.text.toString().trim()
            validateReferralCode(referralCode)?.let { _ ->
                state.update {
                    it.copy(searchStatus = SearchStatusType.VALIDATION_ERROR)
                }
                return@launch
            }

            state.update {
                it.copy(searchStatus = SearchStatusType.IS_SEARCHING)
            }

            try {
                val exists = thorChainApi.existsReferralCode(referralCode)
                val status = if (exists) {
                    SearchStatusType.ERROR
                } else {
                    SearchStatusType.SUCCESS
                }
                state.update {
                    it.copy(searchStatus = status)
                }
            } catch (t: Throwable) {
                state.update {
                    it.copy(searchStatus = SearchStatusType.DEFAULT)
                }
            }
        }
    }

    private fun observeReferralTextField() {
        viewModelScope.launch {
            searchReferralTexFieldState
                .textAsFlow()
                .onEach {
                    if (state.value.searchStatus != SearchStatusType.DEFAULT) {
                        state.update {
                            it.copy(searchStatus = SearchStatusType.DEFAULT)
                        }
                    }
                }
                .collect {}
        }
    }

    fun onCleanReferralClick() {
        viewModelScope.launch {
            searchReferralTexFieldState.clearText()
            state.update {
                it.copy(searchStatus = SearchStatusType.DEFAULT)
            }
        }
    }

    fun onAddExpirationYear() {
        viewModelScope.launch {
            val totalToAdd = state.value.yearExpiration + 1
            updateFormattedDate(totalToAdd)
        }
    }

    fun onSubtractExpirationYear() {
        viewModelScope.launch {
            val totalToAdd = state.value.yearExpiration - 1
            updateFormattedDate(totalToAdd)
        }
    }

    private suspend fun updateFormattedDate(toAdd: Int) {
        val formattedDate = getFormattedDateByAdding(toAdd.toLong())
        val newFees = calculateFees(toAdd)

        state.update {
            it.copy(
                yearExpiration = toAdd,
                formattedYearExpiration = formattedDate,
                fees = newFees,
            )
        }
    }

    fun onCreateReferralCode() {

    }

    private fun getFormattedDateByAdding(add: Long): String {
        val currentDate = LocalDate.now()
        val nextYearDate = currentDate.plusYears(add)
        val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.getDefault())
        return nextYearDate.format(formatter)
    }

    private suspend fun BigInteger.convertToFiat(): String {
        val gasFeeParams = Coins.coins[Chain.ThorChain]?.first()?.let { selectedCoin ->
            GasFeeParams(
                gasLimit = BigInteger.ONE,
                gasFee = TokenValue(this, "RUNE", 8),
                selectedToken = selectedCoin,
            )
        } ?: error("Can't calculate fees")

        return gasFeeToEstimate.invoke(gasFeeParams).formattedFiatValue
    }

    private companion object {
        const val DEFAULT_REGISTRATION_FEES = "1000000000"
        const val DEFAULT_BLOCK_FEES = "20"

        const val DATE_FORMAT = "d MMMM yyyy"

        const val BLOCKS_PER_SECOND = 6
        const val BLOCKS_PER_DAY = (60 / BLOCKS_PER_SECOND) * 60 * 24
        const val BLOCKS_PER_YEAR = BLOCKS_PER_DAY * 365
    }
}