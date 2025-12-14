package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal data class DepositTransactionUiModel(
    val token: ValuedToken = ValuedToken.Empty,

    val networkFeeFiatValue: String = "",
    val networkFeeTokenValue: String = "",

    val srcAddress: String = "",
    val dstAddress: String = "",

    val memo: String = "",

    val operation: String = "",
    val thorAddress: String = "",
)
internal data class VerifyDepositUiModel(
    val depositTransactionUiModel: DepositTransactionUiModel = DepositTransactionUiModel(),
    val errorText: UiText? = null,
    val hasFastSign: Boolean = false,
    val isLoading: Boolean = false,
)

@HiltViewModel
internal class VerifyDepositViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mapTransactionToUiModel: DepositTransactionToUiModelMapper,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val launchKeysign: LaunchKeysignUseCase,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
) : ViewModel() {

    val state = MutableStateFlow(VerifyDepositUiModel())
    private val password = MutableStateFlow<String?>(null)
    private val args = runCatching { savedStateHandle.toRoute<Route.VerifyDeposit>() }.getOrNull()
    private var transactionId: String? = savedStateHandle[SendDst.ARG_TRANSACTION_ID]
    private var vaultId: String? = savedStateHandle["vault_id"]

    init {
        transactionId = transactionId ?: args?.transactionId
        vaultId = vaultId ?: args?.vaultId

        requireNotNull(transactionId) { "transactionId is null" }
        requireNotNull(vaultId) { "vaultId is null" }

        viewModelScope.launch {
            try {
                val transaction = depositTransactionRepository.getTransaction(transactionId!!)
                var initialTransaction = DepositTransactionUiModel(
                    srcAddress = transaction.srcAddress,
                    dstAddress = transaction.dstAddress,
                    token = ValuedToken(
                        token = transaction.srcToken,
                        value = "",
                        fiatValue = "",
                    ),
                    networkFeeFiatValue = transaction.estimateFeesFiat,
                    networkFeeTokenValue = "",
                    memo = transaction.memo,
                    operation = transaction.operation,
                    thorAddress = transaction.thorAddress,
                )

                state.update {
                    it.copy(
                        isLoading = true,
                        depositTransactionUiModel = initialTransaction
                    )
                }

                val depositTransactionUiModel = mapTransactionToUiModel(transaction)
                state.update {
                    it.copy(
                        depositTransactionUiModel = depositTransactionUiModel,
                        isLoading = false,
                    )
                }
            } catch (t: Throwable) {
                Timber.e(t)
                state.update {
                    it.copy(
                        errorText = UiText.StringResource(R.string.try_again),
                        isLoading = false
                    )
                }
            }
        }

        loadFastSign()
        loadPassword()
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    fun confirm() {
        keysign(KeysignInitType.QR_CODE)
    }

    fun authFastSign() {
        keysign(KeysignInitType.BIOMETRY)
    }

    fun tryToFastSignWithPassword(): Boolean {
        if (password.value != null) {
            return false
        } else {
            keysign(KeysignInitType.PASSWORD)
            return true
        }
    }

    private fun keysign(
        keysignInitType: KeysignInitType,
    ) {
        viewModelScope.launch {
            launchKeysign(keysignInitType, transactionId!!, password.value,
                Route.Keysign.Keysign.TxType.Deposit, vaultId!!)
        }
    }

    private fun loadPassword() {
        viewModelScope.launch {
            password.value = withContext(Dispatchers.IO) {
                vaultPasswordRepository.getPassword(vaultId!!)
            }
        }
    }

    private fun loadFastSign() {
        viewModelScope.launch {
            val hasFastSign = withContext(Dispatchers.IO){
                isVaultHasFastSignById(vaultId!!)
            }
            state.update {
                it.copy(
                    hasFastSign = hasFastSign
                )
            }
        }
    }

}