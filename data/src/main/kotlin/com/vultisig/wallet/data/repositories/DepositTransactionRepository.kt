package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TransactionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

interface DepositTransactionRepository {

    suspend fun addTransaction(transaction: DepositTransaction)

    suspend fun getTransaction(id: TransactionId): DepositTransaction
}

internal class DepositTransactionRepositoryImpl @Inject constructor() :
    DepositTransactionRepository {

    private val transactions = MutableStateFlow(mapOf<TransactionId, DepositTransaction>())

    override suspend fun addTransaction(transaction: DepositTransaction) {
        transactions.update {
            it + (transaction.id to transaction)
        }
    }

    override suspend fun getTransaction(id: TransactionId): DepositTransaction =
        transactions.map {
            it[id] ?: error("Transaction with id $id not found")
        }.first()
}