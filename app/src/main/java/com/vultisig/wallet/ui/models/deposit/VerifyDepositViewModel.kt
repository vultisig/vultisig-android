package com.vultisig.wallet.ui.models.deposit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.BalanceRepository
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
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class DepositTransactionUiModel(
    val token: ValuedToken = ValuedToken.Empty,
    val networkFeeFiatValue: String = "",
    val networkFeeTokenValue: String = "",
    val srcAddress: String = "",
    val dstAddress: String = "",
    // Resolved on the done screen so the Transaction-complete view renders the same From/To labels
    // and "Add to Address Book" affordance for deposits as it does for sends (issue #4939).
    val srcVaultName: String? = null,
    val dstVaultName: String? = null,
    val dstAddressBookTitle: String? = null,
    val memo: String = "",
    val operation: String = "",
    val thorAddress: String = "",
    val nodeAddress: String = "",
    val pairedAddress: String = "",
    val pool: String = "",
    val validatorName: String = "",
)

internal data class VerifyDepositUiModel(
    val depositTransactionUiModel: DepositTransactionUiModel = DepositTransactionUiModel(),
    val errorText: UiText? = null,
    val hasFastSign: Boolean = false,
    val isLoading: Boolean = false,
    /**
     * Whether the source account balance can cover the required network fee (plus any sent amount).
     * When false the Sign button is disabled and an inline fee-balance error is shown so the
     * keysign ceremony cannot start for an account that can never pay the fee (issue #5044).
     */
    val hasEnoughBalance: Boolean = true,
    val insufficientBalanceError: UiText? = null,
)

@HiltViewModel
internal class VerifyDepositViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val mapTransactionToUiModel: DepositTransactionToUiModelMapper,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val balanceRepository: BalanceRepository,
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
                var initialTransaction =
                    DepositTransactionUiModel(
                        srcAddress = transaction.srcAddress,
                        dstAddress = transaction.dstAddress,
                        token =
                            ValuedToken(token = transaction.srcToken, value = "", fiatValue = ""),
                        networkFeeFiatValue = transaction.estimateFeesFiat,
                        networkFeeTokenValue = "",
                        memo = transaction.memo,
                        operation = transaction.operation,
                        thorAddress = transaction.thorAddress,
                        nodeAddress = transaction.nodeAddress,
                        pairedAddress = transaction.pairedAddress,
                        pool = transaction.pool,
                    )

                state.update {
                    it.copy(isLoading = true, depositTransactionUiModel = initialTransaction)
                }

                val depositTransactionUiModel = mapTransactionToUiModel(transaction)
                state.update { it.copy(depositTransactionUiModel = depositTransactionUiModel) }

                // Keep isLoading true (Sign button disabled) until the balance lookup resolves, so
                // a zero-balance voter cannot tap Sign mid round-trip while hasEnoughBalance is
                // still at its default true and start the doomed ceremony this targets (#5044).
                checkFeeAffordability(transaction)

                state.update { it.copy(isLoading = false) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.e(t)
                state.update {
                    it.copy(
                        errorText = UiText.StringResource(R.string.try_again),
                        isLoading = false,
                    )
                }
            }
        }

        loadFastSign()
        loadPassword()
    }

    /**
     * Verifies the source account holds enough native balance to cover the required network fee
     * (plus any amount being sent) before the keysign ceremony can be started. Scoped to QBTC,
     * whose unfunded accounts otherwise stage a vote that the chain rejects at broadcast (#5044
     * / #5043). Fails closed: if the balance cannot be resolved (a network error, or an empty
     * lookup result) the Sign button is left disabled rather than defaulting to affordable —
     * mirroring iOS `canCoverVoteFee` — so an unverified balance can never start the doomed
     * ceremony this guards against.
     */
    private suspend fun checkFeeAffordability(
        transaction: com.vultisig.wallet.data.models.DepositTransaction
    ) {
        if (transaction.srcToken.chain != Chain.Qbtc) return

        val balance =
            try {
                balanceRepository
                    .getTokenValue(transaction.srcAddress, transaction.srcToken)
                    .first()
                    .value
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                // Fail closed: an unresolved balance keeps Sign disabled so a QBTC voter can't
                // start a doomed ceremony on a balance we never confirmed can cover the fee
                // (#5044).
                state.update {
                    it.copy(
                        hasEnoughBalance = false,
                        insufficientBalanceError =
                            UiText.StringResource(R.string.network_connection_lost),
                    )
                }
                return
            }
        val requiredSpend = transaction.estimatedFees.value + transaction.srcTokenValue.value

        if (balance < requiredSpend) {
            state.update {
                it.copy(
                    hasEnoughBalance = false,
                    insufficientBalanceError =
                        UiText.FormattedText(
                            R.string.insufficient_native_token,
                            listOf(transaction.srcToken.ticker),
                        ),
                )
            }
        }
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

    private fun keysign(keysignInitType: KeysignInitType) {
        if (!state.value.hasEnoughBalance) return
        val txId = transactionId ?: return
        val vault = vaultId ?: return
        viewModelScope.launch {
            launchKeysign(
                keysignInitType,
                txId,
                password.value,
                Route.Keysign.Keysign.TxType.Deposit,
                vault,
            )
        }
    }

    private fun loadPassword() {
        val vault = vaultId ?: return
        viewModelScope.launch {
            password.value =
                withContext(Dispatchers.IO) { vaultPasswordRepository.getPassword(vault) }
        }
    }

    private fun loadFastSign() {
        val vault = vaultId ?: return
        viewModelScope.launch {
            val hasFastSign = withContext(Dispatchers.IO) { isVaultHasFastSignById(vault) }
            state.update { it.copy(hasFastSign = hasFastSign) }
        }
    }
}
