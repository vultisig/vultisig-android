package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.AddressBookEntryDao
import com.vultisig.wallet.data.db.models.AddressBookEntryEntity
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AddressBookRepositoryImplTest {

    private lateinit var dao: FakeAddressBookEntryDao
    private lateinit var repository: AddressBookRepositoryImpl

    @BeforeEach
    fun setUp() {
        dao = FakeAddressBookEntryDao()
        repository = AddressBookRepositoryImpl(dao)
    }

    @Test
    fun `EVM entry is found when looked up with a different checksum casing`() = runTest {
        repository.add(
            AddressBookEntry(chain = Chain.Ethereum, address = CHECKSUMMED, title = "Alice")
        )

        assertTrue(repository.entryExists(Chain.Ethereum.id, LOWERCASED))
    }

    @Test
    fun `EVM entry is fetched by a different casing while keeping the stored casing`() = runTest {
        repository.add(
            AddressBookEntry(chain = Chain.Ethereum, address = CHECKSUMMED, title = "Alice")
        )

        val entry = repository.getEntry(Chain.Ethereum.id, LOWERCASED)

        assertNotNull(entry)
        assertEquals(CHECKSUMMED, entry.address)
        assertEquals("Alice", entry.title)
    }

    @Test
    fun `getEntry returns null when the address is not saved`() = runTest {
        assertNull(repository.getEntry(Chain.Ethereum.id, CHECKSUMMED))
        assertNull(repository.getEntry(Chain.Bitcoin.id, BTC_ADDRESS))
    }

    @Test
    fun `EVM entry is deleted when removed with a different casing`() = runTest {
        repository.add(
            AddressBookEntry(chain = Chain.Ethereum, address = CHECKSUMMED, title = "Alice")
        )

        repository.delete(Chain.Ethereum.id, LOWERCASED)

        assertFalse(repository.entryExists(Chain.Ethereum.id, CHECKSUMMED))
    }

    @Test
    fun `deleting an EVM entry clears case-variant duplicates saved before this change`() =
        runTest {
            // Two rows for the same account, as could have been stored when matching was
            // case-sensitive; a single case-insensitive delete removes both.
            repository.add(
                AddressBookEntry(chain = Chain.Ethereum, address = CHECKSUMMED, title = "Alice")
            )
            repository.add(
                AddressBookEntry(chain = Chain.Ethereum, address = LOWERCASED, title = "Alice")
            )

            repository.delete(Chain.Ethereum.id, LOWERCASED)

            assertFalse(repository.entryExists(Chain.Ethereum.id, CHECKSUMMED))
        }

    @Test
    fun `non-EVM entry is matched case-sensitively`() = runTest {
        repository.add(
            AddressBookEntry(chain = Chain.Bitcoin, address = BTC_ADDRESS, title = "Cold storage")
        )

        assertFalse(repository.entryExists(Chain.Bitcoin.id, BTC_ADDRESS.lowercase()))
        assertTrue(repository.entryExists(Chain.Bitcoin.id, BTC_ADDRESS))
    }

    @Test
    fun `non-EVM entry is not deleted by a different casing`() = runTest {
        repository.add(
            AddressBookEntry(chain = Chain.Bitcoin, address = BTC_ADDRESS, title = "Cold storage")
        )

        repository.delete(Chain.Bitcoin.id, BTC_ADDRESS.lowercase())

        assertTrue(repository.entryExists(Chain.Bitcoin.id, BTC_ADDRESS))
    }

    private companion object {
        // EIP-55 checksummed reference address and its all-lowercase canonical form.
        const val CHECKSUMMED = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
        const val LOWERCASED = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"
        const val BTC_ADDRESS = "1BoatSLRHtKNngkdXEeobR76b53LETtpyT"
    }
}

/**
 * In-memory [AddressBookEntryDao] that mirrors SQLite semantics: the exact methods compare with the
 * default binary collation, and the `IgnoringCase` methods compare with `COLLATE NOCASE` (ASCII
 * case-folding), so the repository's per-chain routing can be exercised without a real database.
 */
private class FakeAddressBookEntryDao : AddressBookEntryDao {

    private val entries = mutableListOf<AddressBookEntryEntity>()

    override suspend fun getEntries(): List<AddressBookEntryEntity> = entries.toList()

    override suspend fun getEntry(chainId: String, address: String): AddressBookEntryEntity? =
        entries.firstOrNull { it.chainId == chainId && it.address == address }

    override suspend fun getEntryIgnoringCase(
        chainId: String,
        address: String,
    ): AddressBookEntryEntity? =
        entries.firstOrNull {
            it.chainId == chainId && it.address.equals(address, ignoreCase = true)
        }

    override suspend fun upsert(entry: AddressBookEntryEntity) {
        entries.removeAll { it.chainId == entry.chainId && it.address == entry.address }
        entries.add(entry)
    }

    override suspend fun delete(chainId: String, address: String) {
        entries.removeAll { it.chainId == chainId && it.address == address }
    }

    override suspend fun deleteIgnoringCase(chainId: String, address: String) {
        entries.removeAll { it.chainId == chainId && it.address.equals(address, ignoreCase = true) }
    }

    override suspend fun entryExists(chainId: String, address: String): Boolean =
        entries.any { it.chainId == chainId && it.address == address }

    override suspend fun entryExistsIgnoringCase(chainId: String, address: String): Boolean =
        entries.any { it.chainId == chainId && it.address.equals(address, ignoreCase = true) }
}
