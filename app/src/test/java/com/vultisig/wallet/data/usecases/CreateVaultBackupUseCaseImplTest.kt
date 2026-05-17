@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import io.ktor.util.decodeBase64Bytes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import vultisig.keygen.v1.LibType
import vultisig.vault.v1.Vault as VaultProto

internal class CreateVaultBackupUseCaseImplTest {

    private val protoBuf = ProtoBuf

    /**
     * Reversible no-op encryption that lets us exercise the full encode → container → decode →
     * verify pipeline without depending on `Pbkdf2AesEncryption` (which is `internal` to the data
     * module). PBKDF2/AES-GCM correctness is covered by `Pbkdf2AesEncryptionTest`.
     */
    private val identityEncryption =
        object : VaultBackupEncryption {
            override fun encrypt(data: ByteArray, password: ByteArray): ByteArray = data.copyOf()

            override fun decrypt(data: ByteArray, password: ByteArray): ByteArray? = data.copyOf()
        }

    private fun useCase(encryption: VaultBackupEncryption) =
        CreateVaultBackupUseCaseImpl(encryption = encryption, protoBuf = protoBuf)

    private fun testVaultProto(name: String = "TestVault") =
        VaultProto(
            name = name,
            publicKeyEcdsa = "ecdsaPubKey",
            publicKeyEddsa = "eddsaPubKey",
            hexChainCode = "chainCode",
            localPartyId = "party-1",
            signers = listOf("party-1", "party-2"),
            resharePrefix = "prefix",
            libType = LibType.LIB_TYPE_DKLS,
            keyShares = listOf(VaultProto.KeyShare(publicKey = "pubA", keyshare = "shareA")),
            chainPublicKeys = emptyList(),
        )

    @Test
    fun `produces encrypted container that round-trips back to the original vault bytes`() {
        val proto = testVaultProto()
        val output = useCase(identityEncryption).invoke(proto, "correct-pw")

        assertNotNull(output)
        val container =
            protoBuf.decodeFromByteArray<VaultContainerProto>(output.decodeBase64Bytes())
        assertTrue(container.isEncrypted)
        val decrypted = container.vault.decodeBase64Bytes() // identity → already plaintext
        val expected = protoBuf.encodeToByteArray(proto)
        assertContentEquals(expected, decrypted)
    }

    @Test
    fun `produces plaintext container when password is null`() {
        val proto = testVaultProto()
        val output = useCase(identityEncryption).invoke(proto, null)

        assertNotNull(output)
        val container =
            protoBuf.decodeFromByteArray<VaultContainerProto>(output.decodeBase64Bytes())
        assertEquals(false, container.isEncrypted)
        val payload = container.vault.decodeBase64Bytes()
        val expected = protoBuf.encodeToByteArray(proto)
        assertContentEquals(expected, payload)
    }

    @Test
    fun `produces plaintext container when password is blank`() {
        val proto = testVaultProto()
        val output = useCase(identityEncryption).invoke(proto, "   ")

        assertNotNull(output)
        val container =
            protoBuf.decodeFromByteArray<VaultContainerProto>(output.decodeBase64Bytes())
        assertEquals(false, container.isEncrypted)
    }

    @Test
    fun `returns null when encryption throws`() {
        // Regression: pre-existing behavior is preserved when the encryption layer itself fails.
        val encryption = mockk<VaultBackupEncryption>()
        every { encryption.encrypt(any(), any()) } throws IllegalStateException("boom")

        val output = useCase(encryption).invoke(testVaultProto(), "pw")

        assertNull(output)
    }

    @Test
    fun `returns null when round-trip decrypt returns null`() {
        // Encryption "succeeds" but the resulting ciphertext can't be decrypted back. Refusing
        // to return the output protects users from saving a backup they can never restore.
        val encryption = mockk<VaultBackupEncryption>()
        every { encryption.encrypt(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every { encryption.decrypt(any(), any()) } returns null

        val output = useCase(encryption).invoke(testVaultProto(), "pw")

        assertNull(output)
    }

    @Test
    fun `returns null when round-trip decrypt throws`() {
        val encryption = mockk<VaultBackupEncryption>()
        every { encryption.encrypt(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every { encryption.decrypt(any(), any()) } throws IllegalStateException("decrypt boom")

        val output = useCase(encryption).invoke(testVaultProto(), "pw")

        assertNull(output)
    }

    @Test
    fun `returns null when round-trip decrypt yields different bytes`() {
        // Surfaces export-time corruption (the scenario behind issue #4482) immediately, so the
        // user doesn't discover a broken backup weeks later when they try to restore it.
        val encryption = mockk<VaultBackupEncryption>()
        every { encryption.encrypt(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every { encryption.decrypt(any(), any()) } returns byteArrayOf(0xFF.toByte(), 0xEE.toByte())

        val output = useCase(encryption).invoke(testVaultProto(), "pw")

        assertNull(output)
    }

    @Test
    fun `verifies with the same password that was used to encrypt`() {
        val encryption = mockk<VaultBackupEncryption>()
        val password = "user-password"
        val vaultBytes = protoBuf.encodeToByteArray(testVaultProto())
        val ciphertext = byteArrayOf(0x01, 0x02, 0x03)
        every { encryption.encrypt(vaultBytes, password.toByteArray()) } returns ciphertext
        every { encryption.decrypt(ciphertext, password.toByteArray()) } returns vaultBytes

        val output = useCase(encryption).invoke(testVaultProto(), password)

        assertNotNull(output)
        verify(exactly = 1) { encryption.encrypt(vaultBytes, password.toByteArray()) }
        verify(exactly = 1) { encryption.decrypt(ciphertext, password.toByteArray()) }
    }

    @Test
    fun `does not invoke encryption layer at all when password is null`() {
        val encryption = mockk<VaultBackupEncryption>()
        // No `every {}` stubs — any call would throw and fail the test.

        val output = useCase(encryption).invoke(testVaultProto(), null)

        assertNotNull(output)
    }
}
