package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.AddressBookEntryDao
import com.vultisig.wallet.data.db.models.AddressBookEntryEntity
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.addressBookChainId
import javax.inject.Inject
import timber.log.Timber

interface AddressBookRepository {

    suspend fun getEntries(): List<AddressBookEntry>

    suspend fun getEntry(chainId: String, address: String): AddressBookEntry?

    suspend fun add(entry: AddressBookEntry)

    suspend fun delete(chainId: String, address: String)

    suspend fun entryExists(chainId: String, address: String): Boolean
}

internal class AddressBookRepositoryImpl
@Inject
constructor(private val addressBookEntryDao: AddressBookEntryDao) : AddressBookRepository {

    override suspend fun getEntries(): List<AddressBookEntry> =
        addressBookEntryDao.getEntries().mapNotNull { it.toAddressBookEntryOrNull() }

    override suspend fun add(entry: AddressBookEntry) {
        addressBookEntryDao.upsert(entry.toEntity())
    }

    override suspend fun getEntry(chainId: String, address: String): AddressBookEntry? {
        val lookup = chainId.toLookupKey()
        val entity =
            if (lookup.isEvm) {
                addressBookEntryDao.getEntryIgnoringCase(lookup.chainId, address)
            } else {
                addressBookEntryDao.getEntry(lookup.chainId, address)
            }
        return entity?.toAddressBookEntryOrNull()
    }

    override suspend fun delete(chainId: String, address: String) {
        val lookup = chainId.toLookupKey()
        if (lookup.isEvm) {
            addressBookEntryDao.deleteIgnoringCase(lookup.chainId, address)
        } else {
            addressBookEntryDao.delete(lookup.chainId, address)
        }
    }

    override suspend fun entryExists(chainId: String, address: String): Boolean {
        // A row on a since-retired chain can't be read back — getEntries and getEntry both drop it
        // — so reporting it as present would send a caller into an edit it can't populate.
        if (Chain.fromRawOrNull(chainId) == null) return false
        val lookup = chainId.toLookupKey()
        return if (lookup.isEvm) {
            addressBookEntryDao.entryExistsIgnoringCase(lookup.chainId, address)
        } else {
            addressBookEntryDao.entryExists(lookup.chainId, address)
        }
    }

    // EVM addresses use EIP-55 checksum casing, so the same account can be written with different
    // capitalization; matching them case-insensitively keeps one logical entry. Other chains use
    // case-sensitive address encodings (base58, bech32, base64), so they stay exact-match. EVM
    // chains also share one address space, so their chainId is canonicalized to Chain.Ethereum's id
    // before it reaches storage or a query. Unknown chain ids fall back to exact-match, unchanged.
    private fun String.toLookupKey(): LookupKey {
        val chain = Chain.fromRawOrNull(this)
        return LookupKey(
            chainId = chain?.addressBookChainId ?: this,
            isEvm = chain?.standard == TokenStandard.EVM,
        )
    }

    private data class LookupKey(val chainId: ChainId, val isEvm: Boolean)

    // Rows outlive the chains they were saved for: a retired chain leaves its entries behind, and
    // no Chain resolves for them. Drop those rows rather than fail the whole address book.
    private fun AddressBookEntryEntity.toAddressBookEntryOrNull(): AddressBookEntry? {
        val chain =
            Chain.fromRawOrNull(chainId)
                ?: run {
                    Timber.w("Dropping address book entry on unknown chain %s", chainId)
                    return null
                }
        return AddressBookEntry(chain = chain, address = address, title = title)
    }

    private fun AddressBookEntry.toEntity() =
        AddressBookEntryEntity(chainId = chain.addressBookChainId, address = address, title = title)
}
