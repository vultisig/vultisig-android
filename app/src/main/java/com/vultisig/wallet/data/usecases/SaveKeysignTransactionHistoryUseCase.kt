package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.db.models.TransactionStatus.BROADCASTED
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CommonTransactionHistoryData
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.UnknownTransactionHistoryData
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import javax.inject.Inject

/**
 * Persists a freshly broadcast keysign transaction to the local transaction-history store.
 *
 * Extracted from `KeysignViewModel` so the persistence logic can be unit-tested in isolation. All
 * ViewModel-derived values (explorer URL, Polkadot broadcast block) are passed in rather than read
 * from ViewModel state.
 */
class SaveKeysignTransactionHistoryUseCase
@Inject
constructor(private val transactionHistoryRepository: TransactionHistoryRepository) {

    /**
     * Records the broadcast transaction. No-op when [transactionHistoryData] is null or of an
     * unknown type. Any persistence failure is swallowed so it never breaks the keysign flow.
     *
     * @param vaultId Id of the vault that signed the transaction.
     * @param txHash On-chain hash of the broadcast transaction.
     * @param chain Chain the transaction was broadcast to.
     * @param explorerUrl Explorer (or swap-progress) link to store alongside the record.
     * @param transactionHistoryData Type-specific history payload, or null to skip persistence.
     * @param broadcastBlockNumber Head block at broadcast for mortal extrinsics (Polkadot), else
     *   null.
     */
    suspend operator fun invoke(
        vaultId: String,
        txHash: String,
        chain: Chain,
        explorerUrl: String?,
        transactionHistoryData: TransactionHistoryData?,
        broadcastBlockNumber: Long?,
    ) {
        transactionHistoryData?.let {
            runCatching {
                val now = System.currentTimeMillis()
                val historyData =
                    CommonTransactionHistoryData(
                        vaultId = vaultId,
                        txHash = txHash,
                        chain = chain.raw,
                        timestamp = now,
                        explorerUrl = explorerUrl,
                        status = BROADCASTED,
                        type =
                            when (it) {
                                is SendTransactionHistoryData -> TransactionType.SEND
                                is SwapTransactionHistoryData -> TransactionType.SWAP
                                is UnknownTransactionHistoryData -> return@runCatching
                            },
                        confirmedAt = null,
                        failureReason = null,
                        lastCheckedAt = now,
                        broadcastBlockNumber = broadcastBlockNumber,
                    )
                transactionHistoryRepository.recordTransaction(
                    vaultId = vaultId,
                    txHash = txHash,
                    txData = it,
                    genericData = historyData,
                )
            }
        }
    }
}
