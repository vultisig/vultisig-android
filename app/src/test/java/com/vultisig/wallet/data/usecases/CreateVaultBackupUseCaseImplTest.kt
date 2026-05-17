@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import io.ktor.util.decodeBase64Bytes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
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
     * Reversible no-op encryption that exercises the full encode → container → decode → verify
     * pipeline without depending on `Pbkdf2AesEncryption` (which is `internal` to the data module).
     * PBKDF2/AES-GCM correctness is covered by `Pbkdf2AesEncryptionTest`.
     */
    private val identityEncryption =
        object : VaultBackupEncryption {
            override fun encrypt(data: ByteArray, password: ByteArray): ByteArray = data.copyOf()

            override fun decrypt(data: ByteArray, password: ByteArray): ByteArray? = data.copyOf()
        }

    private fun useCase(encryption: VaultBackupEncryption) =
        CreateVaultBackupUseCaseImpl(encryption = encryption, protoBuf = protoBuf)

    private fun parseContainer(backup: String): VaultContainerProto =
        protoBuf.decodeFromByteArray(backup.decodeBase64Bytes())

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
        val container = parseContainer(output)
        assertTrue(container.isEncrypted)
        // Identity encryption leaves the payload as plaintext, so the decoded `vault` field is
        // already the original protobuf bytes.
        val decoded = container.vault.decodeBase64Bytes()
        assertContentEquals(protoBuf.encodeToByteArray(proto), decoded)
    }

    @Test
    fun `produces plaintext container when password is null`() {
        val proto = testVaultProto()
        val output = useCase(identityEncryption).invoke(proto, null)

        assertNotNull(output)
        val container = parseContainer(output)
        assertFalse(container.isEncrypted)
        assertContentEquals(protoBuf.encodeToByteArray(proto), container.vault.decodeBase64Bytes())
    }

    @Test
    fun `produces plaintext container when password is blank`() {
        val output = useCase(identityEncryption).invoke(testVaultProto(), "   ")

        assertNotNull(output)
        assertFalse(parseContainer(output).isEncrypted)
    }

    @Test
    fun `returns null when encryption throws`() {
        // Regression: pre-existing behavior is preserved when the encryption layer itself fails.
        val encryption = mockk<VaultBackupEncryption>()
        every { encryption.encrypt(any(), any()) } throws IllegalStateException("boom")

        assertNull(useCase(encryption).invoke(testVaultProto(), "pw"))
    }

    @Test
    fun `returns null when round-trip decrypt returns null`() {
        // Encryption "succeeds" but the resulting ciphertext can't be decrypted back. Refusing
        // to return the output protects users from saving a backup they can never restore.
        val encryption = mockk<VaultBackupEncryption>()
        every { encryption.encrypt(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every { encryption.decrypt(any(), any()) } returns null

        assertNull(useCase(encryption).invoke(testVaultProto(), "pw"))
    }

    @Test
    fun `returns null when round-trip decrypt throws`() {
        val encryption = mockk<VaultBackupEncryption>()
        every { encryption.encrypt(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every { encryption.decrypt(any(), any()) } throws IllegalStateException("decrypt boom")

        assertNull(useCase(encryption).invoke(testVaultProto(), "pw"))
    }

    @Test
    fun `returns null when round-trip decrypt yields different bytes`() {
        // Surfaces export-time corruption (the scenario behind issue #4482) immediately, so the
        // user doesn't discover a broken backup weeks later when they try to restore it.
        val encryption = mockk<VaultBackupEncryption>()
        every { encryption.encrypt(any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        every { encryption.decrypt(any(), any()) } returns byteArrayOf(0xFF.toByte(), 0xEE.toByte())

        assertNull(useCase(encryption).invoke(testVaultProto(), "pw"))
    }

    @Test
    fun `verifies with the same password that was used to encrypt`() {
        val encryption = mockk<VaultBackupEncryption>()
        val password = "user-password"
        val vaultBytes = protoBuf.encodeToByteArray(testVaultProto())
        val ciphertext = byteArrayOf(0x01, 0x02, 0x03)
        every { encryption.encrypt(vaultBytes, password.toByteArray()) } returns ciphertext
        every { encryption.decrypt(ciphertext, password.toByteArray()) } returns vaultBytes

        assertNotNull(useCase(encryption).invoke(testVaultProto(), password))

        verify(exactly = 1) { encryption.encrypt(vaultBytes, password.toByteArray()) }
        verify(exactly = 1) { encryption.decrypt(ciphertext, password.toByteArray()) }
    }

    @Test
    fun `does not invoke encryption layer at all when password is null`() {
        // No `every {}` stubs — any encryption call would throw and fail the test.
        val encryption = mockk<VaultBackupEncryption>()

        assertNotNull(useCase(encryption).invoke(testVaultProto(), null))
    }
}
