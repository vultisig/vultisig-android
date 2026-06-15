package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.AddressBookEntryDao
import com.vultisig.wallet.data.db.models.AddressBookEntryEntity
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import javax.inject.Inject

interface AddressBookRepository {

    suspend fun getEntries(): List<AddressBookEntry>

    suspend fun getEntry(chainId: String, address: String): AddressBookEntry

    suspend fun add(entry: AddressBookEntry)

    suspend fun delete(chainId: String, address: String)

    suspend fun entryExists(chainId: String, address: String): Boolean
}

internal class AddressBookRepositoryImpl
@Inject
constructor(private val addressBookEntryDao: AddressBookEntryDao) : AddressBookRepository {

    override suspend fun getEntries(): List<AddressBookEntry> =
        addressBookEntryDao.getEntries().map { it.toAddressBookEntry() }

    override suspend fun add(entry: AddressBookEntry) {
        addressBookEntryDao.upsert(entry.toEntity())
    }

    override suspend fun getEntry(chainId: String, address: String): AddressBookEntry {
        val entity =
            if (isEvm(chainId)) {
                addressBookEntryDao.getEntryIgnoringCase(chainId, address)
            } else {
                addressBookEntryDao.getEntry(chainId, address)
            }
        return entity.toAddressBookEntry()
    }

    override suspend fun delete(chainId: String, address: String) {
        if (isEvm(chainId)) {
            addressBookEntryDao.deleteIgnoringCase(chainId, address)
        } else {
            addressBookEntryDao.delete(chainId, address)
        }
    }

    override suspend fun entryExists(chainId: String, address: String): Boolean =
        if (isEvm(chainId)) {
            addressBookEntryDao.entryExistsIgnoringCase(chainId, address)
        } else {
            addressBookEntryDao.entryExists(chainId, address)
        }

    // EVM addresses use EIP-55 checksum casing, so the same account can be written with different
    // capitalization; matching them case-insensitively keeps one logical entry. Other chains use
    // case-sensitive address encodings (base58, bech32, base64), so they stay exact-match. Unknown
    // chain ids fall back to exact-match, matching the pre-existing behavior.
    private fun isEvm(chainId: String): Boolean =
        Chain.entries.firstOrNull { it.raw.equals(chainId, ignoreCase = true) }?.standard ==
            TokenStandard.EVM

    private fun AddressBookEntryEntity.toAddressBookEntry() =
        AddressBookEntry(chain = Chain.fromRaw(chainId), address = address, title = title)

    private fun AddressBookEntry.toEntity() =
        AddressBookEntryEntity(chainId = chain.id, address = address, title = title)
}
