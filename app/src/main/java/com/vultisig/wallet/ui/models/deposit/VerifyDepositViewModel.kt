package com.vultisig.wallet.ui.models.deposit

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.nativeToken
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.models.limitorder.LimitOrderCancelPresentation
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.depositVerifyTitleRes
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.resolveDstVaultName
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
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
    /**
     * Base64 raw transactions when the deposit carries pre-built Solana bytes (Kamino Earn, native
     * staking). Rendered as a decoded instruction list, so the device approving the transaction —
     * either device, in a co-signed vault — can see what it actually does rather than only its
     * amount and destination.
     */
    val signSolana: List<String> = emptyList(),
    @StringRes val titleRes: Int = R.string.verify_deposit_sending,
    /**
     * `SRC → TGT` of the limit order a `m=<` cancel closes, parsed straight out of the memo. Null
     * for every other transaction. Shown in THORChain's own asset spelling, so the initiator and a
     * co-signer — which derives it from the same memo bytes — cannot disagree.
     */
    val limitCancelPair: String? = null,
    /**
     * True when this cancel is the L1 route, i.e. it attaches dust to reach THORChain.
     *
     * That dust is DONATED — nothing attached to an `m=<` has a refund path — and on Dogecoin it is
     * 2 DOGE, not a rounding error. It is the cancel's second cost and the only one the network-fee
     * row does not cover, so the explanation has to name it. False for the THORChain route, whose
     * `MsgDeposit` carries no coins and whose whole cost really is one network fee.
     */
    val limitCancelDonatesDust: Boolean = false,
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
    private val vaultRepository: VaultRepository,
    private val addressBookRepository: AddressBookRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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
                        titleRes = depositVerifyTitleRes(transaction.operation, transaction.memo),
                        limitCancelPair =
                            LimitOrderCancelPresentation.pairCaption(transaction.memo),
                        limitCancelDonatesDust =
                            LimitOrderCancelPresentation.donatesDust(
                                memo = transaction.memo,
                                amount = transaction.srcTokenValue.value,
                            ),
                    )

                state.update {
                    it.copy(isLoading = true, depositTransactionUiModel = initialTransaction)
                }

                val depositTransactionUiModel =
                    mapTransactionToUiModel(transaction)
                        .withVaultLabels(
                            vaultId = transaction.vaultId,
                            chain = transaction.srcToken.chain,
                            dstAddress = transaction.dstAddress,
                        )
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
     * Resolves the From/To labels so the confirmation renders "VaultName (address)" instead of a
     * raw `thor1…` string, matching the Send verify screen (issue #5301). From is the signing
     * vault; To resolves to a locally-stored vault when the destination is one (e.g. a
     * self-operated node), else an address-book title, else the raw address.
     */
    private suspend fun DepositTransactionUiModel.withVaultLabels(
        vaultId: String,
        chain: Chain,
        dstAddress: String,
    ): DepositTransactionUiModel {
        val allVaults = withContext(ioDispatcher) { vaultRepository.getAll() }
        val srcVaultName = allVaults.find { it.id == vaultId }?.name
        val dstVaultName =
            dstAddress
                .takeIf { it.isNotEmpty() }
                ?.let {
                    resolveDstVaultName(
                        allVaults = allVaults,
                        chain = chain,
                        dstAddress = it,
                        chainAccountAddressRepository = chainAccountAddressRepository,
                        dispatcher = ioDispatcher,
                    )
                }
        val dstAddressBookTitle =
            if (dstVaultName == null && dstAddress.isNotEmpty()) {
                addressBookRepository.getEntry(chain.id, dstAddress)?.title
            } else null

        return copy(
            srcVaultName = srcVaultName,
            dstVaultName = dstVaultName,
            dstAddressBookTitle = dstAddressBookTitle,
        )
    }

    /**
     * Verifies the wallet can cover what the transaction spends, before the keysign ceremony can be
     * started.
     *
     * The amount and the fee are not always drawn from one balance. Where the fee is denominated in
     * the source token they are, and the balance has to carry both — QBTC's vote is that shape, and
     * its whole cost is the fee (#5044 / #5043). Where they differ the fee is checked on its own
     * against the chain's native balance, and the amount is left to the form that already sized it
     * against the source balance. A Kamino USDC deposit is the second shape: it pays its Solana fee
     * in SOL, so a vault holding only USDC passed this screen and the entire MPC ceremony before
     * being rejected at broadcast (#5607).
     *
     * An unresolved balance fails closed on QBTC alone. There the gate mirrors iOS
     * `canCoverVoteFee` and guards a vote the chain refuses outright, so an unverified balance must
     * not start it. Reading one flaky RPC call as "cannot afford" everywhere else would disable
     * Sign on transactions that are perfectly fundable.
     */
    private suspend fun checkFeeAffordability(transaction: DepositTransaction) {
        val srcToken = transaction.srcToken
        val fee = transaction.estimatedFees
        // Tickers rather than coins: TokenValue records the unit it was built with, which is the
        // only statement the transaction makes about which balance pays the fee.
        val feeLeavesSourceBalance = fee.unit.equals(srcToken.ticker, ignoreCase = true)

        val feeToken =
            if (feeLeavesSourceBalance) srcToken
            else srcToken.chain.nativeToken.copy(address = transaction.srcAddress)
        val requiredSpend =
            if (feeLeavesSourceBalance) fee.value + transaction.srcTokenValue.value else fee.value

        val balance =
            readBalance(
                address = transaction.srcAddress,
                token = feeToken,
                failClosed = srcToken.chain == Chain.Qbtc,
            ) ?: return

        if (balance < requiredSpend) {
            state.update {
                it.copy(
                    hasEnoughBalance = false,
                    insufficientBalanceError =
                        UiText.FormattedText(
                            R.string.insufficient_native_token,
                            listOf(feeToken.ticker),
                        ),
                )
            }
        }
    }

    /**
     * The balance in [token], or null when it could not be read — disabling Sign on the way out
     * when [failClosed], so an unverified balance cannot start a ceremony that must not run.
     */
    private suspend fun readBalance(
        address: String,
        token: Coin,
        failClosed: Boolean,
    ): BigInteger? =
        try {
            balanceRepository.getTokenValue(address, token).first().value
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            if (failClosed) {
                state.update {
                    it.copy(
                        hasEnoughBalance = false,
                        insufficientBalanceError =
                            UiText.StringResource(R.string.network_connection_lost),
                    )
                }
            }
            null
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
                withContext(ioDispatcher) { vaultPasswordRepository.getPassword(vault) }
        }
    }

    private fun loadFastSign() {
        val vault = vaultId ?: return
        viewModelScope.launch {
            val hasFastSign = withContext(ioDispatcher) { isVaultHasFastSignById(vault) }
            state.update { it.copy(hasFastSign = hasFastSign) }
        }
    }
}
