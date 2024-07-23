package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vultisig.wallet.data.db.models.AddressBookEntryEntity

@Dao
internal interface AddressBookEntryDao {

    @Query("SELECT * FROM address_book_entry")
    suspend fun getEntries(): List<AddressBookEntryEntity>

    @Upsert
    suspend fun upsert(entry: AddressBookEntryEntity)

    @Query("DELETE FROM address_book_entry WHERE chainId = :chainId AND address = :address")
    suspend fun delete(chainId: String, address: String)

}