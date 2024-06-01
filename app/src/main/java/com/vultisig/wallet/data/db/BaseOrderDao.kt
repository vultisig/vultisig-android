package com.vultisig.wallet.data.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.vultisig.wallet.data.db.models.BaseOrderEntity
import kotlinx.coroutines.flow.Flow

internal abstract class BaseOrderDao<T : BaseOrderEntity>(private val tableName: String) {
    abstract fun loadOrders(): Flow<List<T>>
    suspend fun find(value: String): T {
        val query = SimpleSQLiteQuery("SELECT * FROM $tableName WHERE `value` = '$value'")
        return findQuery(query)
    }

    suspend fun getMaxOrder(): T? {
        val query = SimpleSQLiteQuery(
            "SELECT * FROM $tableName WHERE `order` = (SELECT max(`order`) FROM $tableName)"
        )
        return getMaxQuery(query)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(order: T)

    suspend fun delete(value: String) {
        val query = SimpleSQLiteQuery("DELETE FROM $tableName where value = '$value'")
        deleteQuery(query)
    }

    @Update
    abstract suspend fun update(value: T)

    @RawQuery
    protected abstract suspend fun findQuery(query: SupportSQLiteQuery): T

    @RawQuery
    protected abstract suspend fun getMaxQuery(query: SupportSQLiteQuery): T?

    @RawQuery
    protected abstract suspend fun deleteQuery(query: SupportSQLiteQuery): Int
}