package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.ui.models.mappers.DepositTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.SendTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Route
import javax.inject.Inject
import timber.log.Timber
import vultisig.keysign.v1.TransactionType

/** The verify/details UI model + history-data pair derived from a keysign payload. */
internal data class KeysignTransactionUiModelResult(
    val transactionTypeUiModel: TransactionTypeUiModel,
    val transactionHistoryData: TransactionHistoryData?,
)

/**
 * Resolves the [TransactionTypeUiModel] (Send / Swap / Deposit) and its history-data for a non-null
 * keysign payload.
 *
 * Extracted verbatim from `KeysignFlowViewModel.updateTransactionUiModel` — the dispatch over
 * transaction type and the delegation to the already-existing mappers. Moving it here lets the
 * mappers and transaction repositories leave the ViewModel constructor. Returns `null` when the
 * send transaction can't be found (the original logged and skipped, leaving data unloaded).
 */
internal class BuildKeysignTransactionUiModelUseCase
@Inject
constructor(
    private val transactionRepository: TransactionRepository,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val mapTransactionToUiModel: TransactionToUiModelMapper,
    private val mapDepositTransactionUiModel: DepositTransactionToUiModelMapper,
    private val mapSwapTransactionToUiModel: SwapTransactionToUiModelMapper,
    private val transactionHistoryDataMapper: SendTransactionHistoryDataMapper,
    private val depositTransactionHistoryDataMapper: DepositTransactionHistoryDataMapper,
    private val swapTransactionToHistoryDataMapper: SwapTransactionToHistoryDataMapper,
) {
    suspend operator fun invoke(
        keysignPayload: KeysignPayload,
        txType: Route.Keysign.Keysign.TxType,
        transactionId: String,
    ): KeysignTransactionUiModelResult? {
        val isSwap =
            keysignPayload.swapPayload != null || txType == Route.Keysign.Keysign.TxType.Swap

        val isDeposit =
            when (val specific = keysignPayload.blockChainSpecific) {
                is BlockChainSpecific.MayaChain -> specific.isDeposit
                is BlockChainSpecific.THORChain -> specific.isDeposit
                is BlockChainSpecific.Ton -> specific.isDeposit
                is BlockChainSpecific.Cosmos ->
                    specific.transactionType == TransactionType.TRANSACTION_TYPE_IBC_TRANSFER ||
                        try {
                            depositTransactionRepository.getTransaction(transactionId)
                            true
                        } catch (e: Exception) {
                            false
                        }

                else -> txType == Route.Keysign.Keysign.TxType.Deposit
            }

        return when {
            isSwap -> {
                val swapTransactionUiModel =
                    mapSwapTransactionToUiModel(
                        swapTransactionRepository.getTransaction(transactionId)
                    )
                KeysignTransactionUiModelResult(
                    transactionTypeUiModel = TransactionTypeUiModel.Swap(swapTransactionUiModel),
                    transactionHistoryData =
                        swapTransactionToHistoryDataMapper(swapTransactionUiModel),
                )
            }

            isDeposit -> {
                val depositTransactionUiModel =
                    mapDepositTransactionUiModel(
                        depositTransactionRepository.getTransaction(transactionId)
                    )
                KeysignTransactionUiModelResult(
                    transactionTypeUiModel =
                        TransactionTypeUiModel.Deposit(depositTransactionUiModel),
                    transactionHistoryData =
                        depositTransactionHistoryDataMapper(depositTransactionUiModel),
                )
            }

            else -> {
                val tx =
                    transactionRepository.getTransaction(transactionId)
                        ?: run {
                            Timber.e("Transaction not found: %s", transactionId)
                            return null
                        }
                val transactionDetailsUiModel = mapTransactionToUiModel(tx)
                KeysignTransactionUiModelResult(
                    transactionTypeUiModel = TransactionTypeUiModel.Send(transactionDetailsUiModel),
                    transactionHistoryData = transactionHistoryDataMapper(transactionDetailsUiModel),
                )
            }
        }
    }
}
