package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.AddressBookEntryDao
import com.vultisig.wallet.data.db.models.AddressBookEntryEntity
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import javax.inject.Inject

interface AddressBookRepository {

    suspend fun getEntries(): List<AddressBookEntry>

    suspend fun getEntry(chainId: String, address: String): AddressBookEntry

    suspend fun add(entry: AddressBookEntry)

    suspend fun delete(chainId: String, address: String)

    suspend fun entryExists(chainId: String, address: String): Boolean
}

internal class AddressBookRepositoryImpl @Inject constructor(
    private val addressBookEntryDao: AddressBookEntryDao,
) : AddressBookRepository {

    override suspend fun getEntries(): List<AddressBookEntry> =
        addressBookEntryDao.getEntries().map { it.toAddressBookEntry() }

    override suspend fun add(entry: AddressBookEntry) {
        addressBookEntryDao.upsert(entry.toEntity())
    }

    override suspend fun getEntry(chainId: String, address: String): AddressBookEntry {
        return addressBookEntryDao.getEntry(chainId, address).toAddressBookEntry()
    }

    override suspend fun delete(chainId: String, address: String) {
        addressBookEntryDao.delete(chainId, address)
    }

    override suspend fun entryExists(chainId: String, address: String): Boolean {
        return addressBookEntryDao.entryExists(chainId, address)
    }


    private fun AddressBookEntryEntity.toAddressBookEntry() =
        AddressBookEntry(
            chain = Chain.fromRaw(chainId),
            address = address,
            title = title,
        )

    private fun AddressBookEntry.toEntity() = AddressBookEntryEntity(
        chainId = chain.id,
        address = address,
        title = title,
    )

}