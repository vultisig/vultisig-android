package com.vultisig.wallet.data.passcode

import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.models.KeyShareEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VaultKeyShareProtectionImplTest {

    private val cipher = KeyShareCipher()
    private val dataKey = ByteArray(32) { it.toByte() }
    private lateinit var vaultDao: VaultDao
    private lateinit var protection: VaultKeyShareProtectionImpl

    @BeforeEach
    fun setUp() {
        vaultDao = mockk(relaxUnitFun = true)
        protection = VaultKeyShareProtectionImpl(vaultDao, cipher, UnconfinedTestDispatcher())
    }

    private fun share(vaultId: String, keyShare: String) =
        KeyShareEntity(vaultId = vaultId, pubKey = "pub-$vaultId", keyShare = keyShare)

    /** A row whose ciphertext is bound to that row's own identity, as production would write it. */
    private fun encryptedShare(vaultId: String, plaintext: String): KeyShareEntity {
        val row = share(vaultId, plaintext)
        return row.copy(keyShare = cipher.encrypt(plaintext, dataKey, row.identity()))
    }

    /** The rows the marker pattern selects, standing in for what SQLite's `GLOB` would return. */
    private fun matching(rows: List<KeyShareEntity>) =
        rows.filter { cipher.isEncrypted(it.keyShare) }

    private fun stubTable(rows: List<KeyShareEntity>) {
        val pattern = cipher.encryptedMarkerPattern
        coEvery { vaultDao.loadKeySharesMatching(pattern) } returns matching(rows)
        coEvery { vaultDao.loadKeySharesNotMatching(pattern) } returns rows - matching(rows).toSet()
        coEvery { vaultDao.hasKeySharesMatching(pattern) } returns matching(rows).isNotEmpty()
    }

    @Test
    fun `the marker pattern is a plain prefix match on what isEncrypted accepts`() {
        // Read straight into a GLOB query, where a stray metacharacter would silently widen the
        // match and hand plaintext rows to the decrypt path.
        val pattern = cipher.encryptedMarkerPattern
        assertTrue(pattern.endsWith("*"), "the pattern must be an open-ended prefix")
        val prefix = pattern.dropLast(1)
        assertTrue(
            prefix.none { it in "*?[" },
            "the marker must hold no GLOB metacharacter, was '$prefix'",
        )
        assertTrue(cipher.isEncrypted(prefix + "anything"))
        assertTrue(!cipher.isEncrypted(prefix.uppercase() + "anything"), "GLOB is case-sensitive")
    }

    @Test
    fun `protectAll encrypts every plaintext share`() = runTest {
        stubTable(listOf(share("a", "share-a"), share("b", "share-b")))
        val written = slot<List<KeyShareEntity>>()

        protection.protectAll(dataKey)

        coVerify { vaultDao.upsertKeyshares(capture(written)) }
        assertEquals(2, written.captured.size)
        assertTrue(written.captured.all { cipher.isEncrypted(it.keyShare) })
        assertEquals(
            listOf("share-a", "share-b"),
            written.captured.map { cipher.decrypt(it.keyShare, dataKey, it.identity()) },
        )
    }

    @Test
    fun `protectAll asks only for the shares still in the clear`() = runTest {
        // The half-migrated table left by a process death must not be double-encrypted on retry,
        // and the rows with no work to do are the largest blobs in the database.
        stubTable(listOf(encryptedShare("a", "share-a"), share("b", "share-b")))
        val written = slot<List<KeyShareEntity>>()

        protection.protectAll(dataKey)

        coVerify(exactly = 0) { vaultDao.loadKeySharesMatching(any()) }
        coVerify { vaultDao.upsertKeyshares(capture(written)) }
        val only = written.captured.single()
        assertEquals("share-b", cipher.decrypt(only.keyShare, dataKey, only.identity()))
    }

    @Test
    fun `protectAll writes nothing when every share is already encrypted`() = runTest {
        stubTable(listOf(encryptedShare("a", "share-a")))

        protection.protectAll(dataKey)

        coVerify(exactly = 0) { vaultDao.upsertKeyshares(any()) }
    }

    @Test
    fun `unprotectAll restores plaintext`() = runTest {
        stubTable(listOf(encryptedShare("a", "share-a"), share("b", "already-plaintext")))
        val written = slot<List<KeyShareEntity>>()

        protection.unprotectAll(dataKey)

        coVerify { vaultDao.upsertKeyshares(capture(written)) }
        assertEquals(listOf("share-a"), written.captured.map { it.keyShare })
    }

    @Test
    fun `unprotectAll writes nothing when a share cannot be decrypted`() = runTest {
        stubTable(listOf(encryptedShare("a", "share-a"), share("b", "vlpc1:Z2FyYmFnZQ==")))

        assertFailsWith<IllegalStateException> { protection.unprotectAll(dataKey) }

        // Partially decrypting would strand the good share in the clear while the bad one is lost.
        coVerify(exactly = 0) { vaultDao.upsertKeyshares(any()) }
    }

    @Test
    fun `hasEncryptedKeyShares asks the database rather than reading every blob`() = runTest {
        // It runs on every cold start, behind the opaque cover the guard draws while it waits.
        stubTable(listOf(encryptedShare("a", "share-a")))

        assertTrue(protection.hasEncryptedKeyShares())

        coVerify { vaultDao.hasKeySharesMatching(cipher.encryptedMarkerPattern) }
        coVerify(exactly = 0) { vaultDao.loadKeySharesMatching(any()) }
        coVerify(exactly = 0) { vaultDao.loadKeySharesNotMatching(any()) }
    }

    @Test
    fun `an empty table is a no-op in both directions`() = runTest {
        stubTable(emptyList())

        protection.protectAll(dataKey)
        protection.unprotectAll(dataKey)

        coVerify(exactly = 0) { vaultDao.upsertKeyshares(any()) }
    }
}
