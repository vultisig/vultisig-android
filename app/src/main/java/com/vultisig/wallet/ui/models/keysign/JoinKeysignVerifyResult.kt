package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.TransactionHistoryData

/**
 * The synchronous output of a per-transaction-type verify UI-model builder: the sealed
 * [VerifyUiModel] the screen observes, plus the [TransactionTypeUiModel] and
 * [TransactionHistoryData] the done screen's `KeysignViewModel` carries forward. The ViewModel
 * assigns all three from this single result so each branch's three-way write stays atomic.
 */
internal data class JoinKeysignVerifyResult(
    val verifyUiModel: VerifyUiModel,
    val transactionTypeUiModel: TransactionTypeUiModel,
    val transactionHistoryData: TransactionHistoryData,
)

/**
 * The send branch's [JoinKeysignVerifyResult] plus the inputs the ViewModel needs to kick off the
 * background hero/scan enrichment — those launch into `viewModelScope` and mutate ViewModel state,
 * so they stay in the ViewModel while the synchronous model build moves into the builder.
 */
internal data class JoinSendUiModelResult(
    val result: JoinKeysignVerifyResult,
    val transaction: Transaction,
    val functionName: String?,
    val vaultCoins: List<Coin>,
)
