package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.TransactionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

interface TransactionRepository {

    suspend fun addTransaction(transaction: Transaction)

    fun getTransaction(id: TransactionId): Flow<Transaction>

}

internal class TransactionRepositoryImpl @Inject constructor() : TransactionRepository {

    private val transactions = MutableStateFlow(mapOf<TransactionId, Transaction>())

    override suspend fun addTransaction(transaction: Transaction) {
        transactions.update {
            it + (transaction.id to transaction)
        }
    }

    override fun getTransaction(id: TransactionId): Flow<Transaction> =
        transactions.map {
            it[id] ?: error("Transaction with id $id not found")
        }

}