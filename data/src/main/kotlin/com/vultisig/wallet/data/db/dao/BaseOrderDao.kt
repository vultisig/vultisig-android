package com.vultisig.wallet.data.db.dao

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.vultisig.wallet.data.db.models.BaseOrderEntity
import kotlinx.coroutines.flow.Flow

abstract class BaseOrderDao<T : BaseOrderEntity>(private val tableName: String) {
    abstract fun loadOrders(parentId: String?): Flow<List<T>>
    suspend fun find(value: String, parentId: String?): T {
        val query = SimpleSQLiteQuery(
            "SELECT * FROM $tableName WHERE `value` = '$value'" + includeParentId(parentId,true)
        )
        return findQuery(query)
    }

    suspend fun safeFind(value: String): T? {
        val query = SimpleSQLiteQuery(
            "SELECT * FROM $tableName WHERE `value` = '$value'"
        )
        return try {
            safeFindQuery(query)
        } catch (_ : Exception) {
            null
        }
    }


    suspend fun getMaxOrder(parentId: String?): T? {
        val query = SimpleSQLiteQuery(
            "SELECT * FROM $tableName WHERE `order` = (SELECT max(`order`)" +
                    " FROM $tableName ${includeParentId(parentId,false)})"
        )
        return getMaxQuery(query)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(order: T)

    suspend fun delete(value: String, parentId: String?) {
        val query = SimpleSQLiteQuery(
            "DELETE FROM $tableName where value = '$value'"
                    + includeParentId(parentId,true)
        )
        deleteQuery(query)
    }

    suspend fun deleteAll(parentId: String?) {
        val query = SimpleSQLiteQuery(
            "DELETE FROM $tableName" + includeParentId(parentId,false)
        )
        deleteAllQuery(query)
    }

    suspend fun removeParentId(parentId: String?, values: List<String>) {
        val valuesStr = values.joinToString("','", "'", "'")
        val parentIdStr =
            if (parentId != null)"'$parentId'"
            else "NULL"
        val query = SimpleSQLiteQuery(
            "UPDATE $tableName SET `parentId` = $parentIdStr WHERE `value` IN ($valuesStr)"
        )
        executeQuery(query)
    }

    suspend fun removeParentId(parentId: String?) {
        val query = SimpleSQLiteQuery(
            "UPDATE $tableName SET `parentId` = NULL WHERE `parentId` = '$parentId'"
        )
        executeQuery(query)
    }

    @Update
    abstract suspend fun update(value: T)

    @RawQuery
    protected abstract suspend fun findQuery(query: SupportSQLiteQuery): T

    @RawQuery
    protected abstract suspend fun safeFindQuery(query: SupportSQLiteQuery): T?

    @RawQuery
    protected abstract suspend fun getMaxQuery(query: SupportSQLiteQuery): T?

    @RawQuery
    protected abstract suspend fun deleteQuery(query: SupportSQLiteQuery): Int

    @RawQuery
    protected abstract suspend fun deleteAllQuery(query: SupportSQLiteQuery): Int

    @RawQuery
    protected abstract suspend fun executeQuery(query: SupportSQLiteQuery): Int

    private fun includeParentId(parentId: String?, hasAnd: Boolean) =
        if (parentId != null) (if (hasAnd) " AND " else " WHERE ") +
                " `parentId` = '$parentId'" else ""
}