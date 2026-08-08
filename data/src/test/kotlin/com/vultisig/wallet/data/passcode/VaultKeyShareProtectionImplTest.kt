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

    @Test
    fun `protectAll encrypts every plaintext share`() = runTest {
        coEvery { vaultDao.loadAllKeyShares() } returns
            listOf(share("a", "share-a"), share("b", "share-b"))
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
    fun `protectAll skips shares that are already encrypted`() = runTest {
        // The half-migrated table left by a process death must not be double-encrypted on retry.
        coEvery { vaultDao.loadAllKeyShares() } returns
            listOf(encryptedShare("a", "share-a"), share("b", "share-b"))
        val written = slot<List<KeyShareEntity>>()

        protection.protectAll(dataKey)

        coVerify { vaultDao.upsertKeyshares(capture(written)) }
        assertEquals(1, written.captured.size)
        val only = written.captured.single()
        assertEquals("share-b", cipher.decrypt(only.keyShare, dataKey, only.identity()))
    }

    @Test
    fun `protectAll writes nothing when every share is already encrypted`() = runTest {
        coEvery { vaultDao.loadAllKeyShares() } returns listOf(encryptedShare("a", "share-a"))

        protection.protectAll(dataKey)

        coVerify(exactly = 0) { vaultDao.upsertKeyshares(any()) }
    }

    @Test
    fun `unprotectAll restores plaintext`() = runTest {
        coEvery { vaultDao.loadAllKeyShares() } returns
            listOf(encryptedShare("a", "share-a"), share("b", "already-plaintext"))
        val written = slot<List<KeyShareEntity>>()

        protection.unprotectAll(dataKey)

        coVerify { vaultDao.upsertKeyshares(capture(written)) }
        assertEquals(listOf("share-a"), written.captured.map { it.keyShare })
    }

    @Test
    fun `unprotectAll writes nothing when a share cannot be decrypted`() = runTest {
        coEvery { vaultDao.loadAllKeyShares() } returns
            listOf(encryptedShare("a", "share-a"), share("b", "vlpc1:Z2FyYmFnZQ=="))

        assertFailsWith<IllegalStateException> { protection.unprotectAll(dataKey) }

        // Partially decrypting would strand the good share in the clear while the bad one is lost.
        coVerify(exactly = 0) { vaultDao.upsertKeyshares(any()) }
    }

    @Test
    fun `an empty table is a no-op in both directions`() = runTest {
        coEvery { vaultDao.loadAllKeyShares() } returns emptyList()

        protection.protectAll(dataKey)
        protection.unprotectAll(dataKey)

        coVerify(exactly = 0) { vaultDao.upsertKeyshares(any()) }
    }
}
