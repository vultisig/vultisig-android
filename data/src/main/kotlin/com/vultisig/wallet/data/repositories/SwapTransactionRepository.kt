package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TransactionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

interface SwapTransactionRepository {

    suspend fun addTransaction(transaction: SwapTransaction)

    suspend fun getTransaction(id: TransactionId): SwapTransaction

}

internal class SwapTransactionRepositoryImpl @Inject constructor() : SwapTransactionRepository {

    private val transactions = MutableStateFlow(mapOf<TransactionId, SwapTransaction>())

    override suspend fun addTransaction(transaction: SwapTransaction) {
        transactions.update {
            it + (transaction.id to transaction)
        }
    }

    override suspend fun getTransaction(id: TransactionId): SwapTransaction =
        transactions.map {
            it[id] ?: error("Transaction with id $id not found")
        }.first()

}