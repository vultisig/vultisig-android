package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.TransactionId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

interface TransactionRepository {

    suspend fun addTransaction(transaction: Transaction)

    suspend fun getTransaction(id: TransactionId): Transaction?
}

internal class TransactionRepositoryImpl @Inject constructor() : TransactionRepository {

    private val transactions = MutableStateFlow(mapOf<TransactionId, Transaction>())

    override suspend fun addTransaction(transaction: Transaction) {
        transactions.update { it + (transaction.id to transaction) }
    }

    override suspend fun getTransaction(id: TransactionId): Transaction? = transactions.value[id]
}
