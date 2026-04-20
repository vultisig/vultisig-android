@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.mappers.VaultFromOldJsonMapper
import com.vultisig.wallet.data.mappers.utils.MapHexToPlainString
import com.vultisig.wallet.data.models.OldJsonVault
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vultisig.keygen.v1.LibType
import vultisig.vault.v1.Vault as VaultProto

@OptIn(ExperimentalEncodingApi::class)
internal class ParseVaultFromStringUseCaseImplTest {

    private val protoBuf = ProtoBuf

    private lateinit var useCase: ParseVaultFromStringUseCaseImpl

    @BeforeEach
    fun setUp() {
        useCase =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk<VaultFromOldJsonMapper>(),
                mapHexToPlainString = mockk<MapHexToPlainString>(),
                encryption = mockk<Encryption>(),
                protoBuf = protoBuf,
                json = mockk(),
            )
    }

    private fun encodeVaultContainer(vaultProto: VaultProto, isEncrypted: Boolean = false): String {
        val vaultBytes = protoBuf.encodeToByteArray(vaultProto)
        val vaultBase64 = Base64.encode(vaultBytes)
        val container = VaultContainerProto(vault = vaultBase64, isEncrypted = isEncrypted)
        val containerBytes = protoBuf.encodeToByteArray(container)
        return Base64.encode(containerBytes)
    }

    private fun encodeEncryptedContainer(vaultBytes: ByteArray): String {
        val vaultBase64 = Base64.encode(vaultBytes)
        val container = VaultContainerProto(vault = vaultBase64, isEncrypted = true)
        val containerBytes = protoBuf.encodeToByteArray(container)
        return Base64.encode(containerBytes)
    }

    private fun testVaultProto(
        name: String = "TestVault",
        publicKeyEcdsa: String = "ecdsaPubKey123",
        publicKeyEddsa: String = "eddsaPubKey456",
        hexChainCode: String = "hexChainCode789",
        localPartyId: String = "party-device-1",
        signers: List<String> = listOf("party-device-1", "party-device-2"),
        resharePrefix: String = "resharePfx",
        libType: LibType = LibType.LIB_TYPE_DKLS,
        keyShares: List<VaultProto.KeyShare?> = emptyList(),
        chainPublicKeys: List<VaultProto.ChainPublicKey?> = emptyList(),
    ) =
        VaultProto(
            name = name,
            publicKeyEcdsa = publicKeyEcdsa,
            publicKeyEddsa = publicKeyEddsa,
            hexChainCode = hexChainCode,
            localPartyId = localPartyId,
            signers = signers,
            resharePrefix = resharePrefix,
            libType = libType,
            keyShares = keyShares,
            chainPublicKeys = chainPublicKeys,
        )

    @Test
    fun `parses basic vault fields correctly`() {
        val proto =
            testVaultProto(
                name = "MyVault",
                publicKeyEcdsa = "ecdsa1",
                publicKeyEddsa = "eddsa2",
                hexChainCode = "chain3",
                localPartyId = "localP",
                signers = listOf("signerA", "signerB"),
                resharePrefix = "prefix",
            )

        val vault = useCase(encodeVaultContainer(proto), null)

        assertEquals("MyVault", vault.name)
        assertEquals("ecdsa1", vault.pubKeyECDSA)
        assertEquals("eddsa2", vault.pubKeyEDDSA)
        assertEquals("chain3", vault.hexChainCode)
        assertEquals("localP", vault.localPartyID)
        assertEquals(listOf("signerA", "signerB"), vault.signers)
        assertEquals("prefix", vault.resharePrefix)
    }

    @Test
    fun `parses unencrypted vault without password`() {
        val proto = testVaultProto(name = "UnencryptedVault")
        val input = encodeVaultContainer(proto, isEncrypted = false)

        val vault = useCase(input, null)

        assertEquals("UnencryptedVault", vault.name)
        assertEquals("ecdsaPubKey123", vault.pubKeyECDSA)
        assertEquals("eddsaPubKey456", vault.pubKeyEDDSA)
    }

    @Test
    fun `parses chainPublicKeys from protobuf`() {
        val proto =
            testVaultProto(
                chainPublicKeys =
                    listOf(
                        VaultProto.ChainPublicKey(
                            chain = "Ethereum",
                            publicKey = "ethPub1",
                            isEddsa = false,
                        ),
                        VaultProto.ChainPublicKey(
                            chain = "Solana",
                            publicKey = "solPub2",
                            isEddsa = true,
                        ),
                    )
            )

        val vault = useCase(encodeVaultContainer(proto), null)

        assertEquals(2, vault.chainPublicKeys.size)
        with(vault.chainPublicKeys[0]) {
            assertEquals("Ethereum", chain)
            assertEquals("ethPub1", publicKey)
            assertEquals(false, isEddsa)
        }
        with(vault.chainPublicKeys[1]) {
            assertEquals("Solana", chain)
            assertEquals("solPub2", publicKey)
            assertEquals(true, isEddsa)
        }
    }

    @Test
    fun `deduplicates keyshares by pubKey keeping last`() {
        val proto =
            testVaultProto(
                keyShares =
                    listOf(
                        VaultProto.KeyShare(publicKey = "pubA", keyshare = "shareA1"),
                        VaultProto.KeyShare(publicKey = "pubB", keyshare = "shareB1"),
                        VaultProto.KeyShare(publicKey = "pubA", keyshare = "shareA2"),
                    )
            )

        val vault = useCase(encodeVaultContainer(proto), null)

        assertEquals(2, vault.keyshares.size)
        val keyshareMap = vault.keyshares.associateBy { it.pubKey }
        assertEquals("shareA2", keyshareMap["pubA"]?.keyShare)
        assertEquals("shareB1", keyshareMap["pubB"]?.keyShare)
    }

    @Test
    fun `parses libType KeyImport from protobuf`() {
        val proto = testVaultProto(libType = LibType.LIB_TYPE_KEYIMPORT)

        val vault = useCase(encodeVaultContainer(proto), null)

        assertEquals(SigningLibType.KeyImport, vault.libType)
    }

    @Test
    fun `falls back to OldJsonVault decode when root wrapper parse fails`() {
        val mapper = mockk<VaultFromOldJsonMapper>()
        val hexMapper = mockk<MapHexToPlainString>()
        val capturedOldVault = slot<OldJsonVault>()
        val expectedVault = Vault(id = "id", name = "Mapped Vault")
        every { hexMapper(any()) } answers { firstArg() }
        every { mapper(capture(capturedOldVault)) } returns expectedVault

        val fallbackUseCase =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mapper,
                mapHexToPlainString = hexMapper,
                encryption = mockk(),
                protoBuf = protoBuf,
                json = Json,
            )

        val oldJson =
            """
            {
              "id": "old-id",
              "localPartyID": "party-1",
              "pubKeyECDSA": "ecdsa",
              "hexChainCode": "chaincode",
              "pubKeyEDDSA": "eddsa",
              "name": "OldVault",
              "signers": ["party-1", "party-2"],
              "keyshares": [{"pubkey": "pub1", "keyshare": "share1"}]
            }
        """
                .trimIndent()

        val result = fallbackUseCase(oldJson, null)

        assertEquals(expectedVault, result)
        assertEquals("OldVault", capturedOldVault.captured.name)
        assertEquals("eddsa", capturedOldVault.captured.pubKeyEdDSA)
    }

    @Test
    fun `ignores password on unencrypted new-format container`() {
        // Defensive: isEncrypted=false must ignore any password and parse normally.
        val proto = testVaultProto(name = "UnencryptedVault")
        val input = encodeVaultContainer(proto, isEncrypted = false)

        val vault = useCase(input, "any-password-should-be-ignored")

        assertEquals("UnencryptedVault", vault.name)
    }

    @Test
    fun `decrypts encrypted old-format wrapper end-to-end with correct password`() {
        // End-to-end legacy path: base64 → AES-CBC decrypt → hex-to-plain → OldJsonVaultRoot.
        val encryption = mockk<Encryption>()
        val mapper = mockk<VaultFromOldJsonMapper>()
        val hexMapper = mockk<MapHexToPlainString>()
        val expectedVault = Vault(id = "id", name = "LegacyVault")

        // 32 bytes of zeros passes the legacy-ciphertext shape check.
        val ciphertextShaped = Base64.encode(ByteArray(32))
        // Mock decrypt → plaintext hex. mapHexToPlainString → JSON string wrapping
        // OldJsonVaultRoot.
        every { encryption.decrypt(any(), any()) } returns "deadbeef".toByteArray(Charsets.UTF_8)
        every { hexMapper(any()) } returns
            """
            {
              "version": "v1",
              "vault": {
                "id": "old-id",
                "localPartyID": "party-1",
                "pubKeyECDSA": "ecdsa",
                "hexChainCode": "chaincode",
                "pubKeyEDDSA": "eddsa",
                "name": "LegacyVault",
                "signers": ["party-1"],
                "keyshares": [{"pubkey": "pub1", "keyshare": "share1"}]
              }
            }
            """
                .trimIndent()
        every { mapper(any()) } returns expectedVault

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mapper,
                mapHexToPlainString = hexMapper,
                encryption = encryption,
                protoBuf = protoBuf,
                json = Json,
            )

        val result = parser(ciphertextShaped, "correct-pw")

        assertEquals(expectedVault, result)
    }

    @Test
    fun `decrypts encrypted new-format container with correct password`() {
        val encryption = mockk<Encryption>()
        val innerVault = testVaultProto(name = "DecryptedVault")
        val innerVaultBytes = protoBuf.encodeToByteArray(innerVault)
        // Inside the container `vault` is base64-encoded ciphertext; for this test we use
        // raw bytes and mock decrypt to return the plaintext protobuf.
        val ciphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val input = encodeEncryptedContainer(ciphertext)
        every { encryption.decrypt(any(), any()) } returns innerVaultBytes

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = encryption,
                protoBuf = protoBuf,
                json = mockk(),
            )

        val vault = parser(input, "correct-password")

        assertEquals("DecryptedVault", vault.name)
    }

    @Test
    fun `throws WrongPasswordException when encrypted new-format container decrypt returns null`() {
        val encryption = mockk<Encryption>()
        val ciphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val input = encodeEncryptedContainer(ciphertext)
        every { encryption.decrypt(any(), any()) } returns null

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = encryption,
                protoBuf = protoBuf,
                json = mockk(),
            )

        assertFailsWith<WrongPasswordException> { parser(input, "wrong-password") }
    }

    @Test
    fun `throws WrongPasswordException when encrypted new-format container decrypt throws`() {
        // Regression: on a real device with a wrong password, AesEncryption's GCM->CBC fallback
        // can throw IllegalBlockSizeException instead of returning null. That must map to
        // WrongPassword, not bubble up as a generic Failed ("Something went wrong").
        val encryption = mockk<Encryption>()
        val ciphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val input = encodeEncryptedContainer(ciphertext)
        every { encryption.decrypt(any(), any()) } throws
            javax.crypto.IllegalBlockSizeException("WRONG_FINAL_BLOCK_LENGTH")

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = encryption,
                protoBuf = protoBuf,
                json = mockk(),
            )

        assertFailsWith<WrongPasswordException> { parser(input, "wrong-password") }
    }

    @Test
    fun `throws WrongPasswordException when encrypted new-format container has no password`() {
        val ciphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val input = encodeEncryptedContainer(ciphertext)

        // No decryption attempt is made when password is null/blank, so the mocked Encryption
        // never gets called — any() answer would be fine.
        assertFailsWith<WrongPasswordException> { useCase(input, null) }
        assertFailsWith<WrongPasswordException> { useCase(input, "") }
        assertFailsWith<WrongPasswordException> { useCase(input, "   ") }
    }

    @Test
    fun `throws MalformedVaultException when decrypted bytes are not a valid VaultProto`() {
        val encryption = mockk<Encryption>()
        val ciphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val input = encodeEncryptedContainer(ciphertext)
        // Decrypt "succeeds" but returns garbage bytes that won't parse as a VaultProto.
        every { encryption.decrypt(any(), any()) } returns byteArrayOf(0x5A, 0x5B, 0x5C)

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = encryption,
                protoBuf = protoBuf,
                json = mockk(),
            )

        assertFailsWith<MalformedVaultException> { parser(input, "password") }
    }

    @Test
    fun `throws MalformedVaultException for garbage input with no password`() {
        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = mockk(),
                protoBuf = protoBuf,
                json = Json,
            )

        // Not a valid container, not plain JSON, base64-decodes to an odd-length blob that
        // can't be CBC ciphertext. No password → we're confident this isn't an encrypted
        // vault and must NOT pop a password prompt.
        assertFailsWith<MalformedVaultException> { parser("not a vault at all {}", null) }
    }

    @Test
    fun `throws WrongPasswordException when no-password input looks like an encrypted blob`() {
        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = mockk(),
                protoBuf = protoBuf,
                json = Json,
            )

        // 32 bytes of base64 decodes to 24 non-zero bytes — not a multiple of 16. Produce
        // something that IS a multiple of 16 (32 bytes) so the heuristic pops the prompt.
        // "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" is 32 bytes of zeros.
        val blob = Base64.encode(ByteArray(32))

        assertFailsWith<WrongPasswordException> { parser(blob, null) }
    }

    @Test
    fun `throws WrongPasswordException when old-format encrypted wrapper decrypt returns null`() {
        val encryption = mockk<Encryption>()
        every { encryption.decrypt(any(), any()) } returns null

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = encryption,
                protoBuf = protoBuf,
                json = Json,
            )

        // 32-byte blob passes the legacy-ciphertext shape heuristic; mocked decrypt returns
        // null → WrongPasswordException.
        val ciphertextShaped = Base64.encode(ByteArray(32))

        assertFailsWith<WrongPasswordException> { parser(ciphertextShaped, "wrong-pw") }
    }

    @Test
    fun `throws WrongPasswordException when old-format encrypted wrapper decrypt throws`() {
        // Regression mirror of the new-format case: IllegalBlockSizeException from the
        // GCM→CBC fallback must not bubble up as a generic Failed.
        val encryption = mockk<Encryption>()
        every { encryption.decrypt(any(), any()) } throws
            javax.crypto.IllegalBlockSizeException("WRONG_FINAL_BLOCK_LENGTH")

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = encryption,
                protoBuf = protoBuf,
                json = Json,
            )

        val ciphertextShaped = Base64.encode(ByteArray(32))

        assertFailsWith<WrongPasswordException> { parser(ciphertextShaped, "wrong-pw") }
    }

    @Test
    fun `throws MalformedVaultException in old-format path when input is not ciphertext shaped`() {
        // With password provided but input isn't plain JSON and isn't ciphertext-shaped
        // (base64-decoded size not a multiple of the AES block), the file is garbage —
        // must not prompt the user again or claim wrong password.
        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = mockk(),
                protoBuf = protoBuf,
                json = Json,
            )

        assertFailsWith<MalformedVaultException> { parser("VGhpcyBpcyBiYXNlNjQ=", "pw") }
    }

    @Test
    fun `throws MalformedVaultException when old-format decrypt succeeds but output is not hex`() {
        val encryption = mockk<Encryption>()
        val hexMapper = mockk<MapHexToPlainString>()
        // Decrypt returns plaintext bytes, but they aren't valid hex — hexMapper throws.
        every { encryption.decrypt(any(), any()) } returns byteArrayOf(0x7A, 0x7B, 0x7C)
        every { hexMapper(any()) } throws IllegalArgumentException("not hex")

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = hexMapper,
                encryption = encryption,
                protoBuf = protoBuf,
                json = Json,
            )

        assertFailsWith<MalformedVaultException> { parser("VGhpcyBpcyBiYXNlNjQ=", "password") }
    }

    @Test
    fun `throws MalformedVaultException when old-format decrypt succeeds but output is not JSON`() {
        val encryption = mockk<Encryption>()
        val hexMapper = mockk<MapHexToPlainString>()
        every { encryption.decrypt(any(), any()) } returns byteArrayOf(0x7A, 0x7B, 0x7C)
        every { hexMapper(any()) } returns "plain text, not json"

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = hexMapper,
                encryption = encryption,
                protoBuf = protoBuf,
                json = Json,
            )

        assertFailsWith<MalformedVaultException> { parser("VGhpcyBpcyBiYXNlNjQ=", "password") }
    }

    @Test
    fun `propagates CancellationException from encryption layer`() {
        val encryption = mockk<Encryption>()
        val ciphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val input = encodeEncryptedContainer(ciphertext)
        every { encryption.decrypt(any(), any()) } throws CancellationException("cancelled")

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = encryption,
                protoBuf = protoBuf,
                json = mockk(),
            )

        // CancellationException must not be reclassified as WrongPassword or Malformed —
        // structured concurrency requires it to propagate unchanged.
        assertFailsWith<CancellationException> { parser(input, "password") }
    }

    @Test
    fun `propagates CancellationException from old-format path`() {
        val encryption = mockk<Encryption>()
        every { encryption.decrypt(any(), any()) } throws CancellationException("cancelled")

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk(),
                encryption = encryption,
                protoBuf = protoBuf,
                json = Json,
            )

        // 32-byte blob passes the legacy-ciphertext shape check so decrypt actually runs
        // and we can observe CancellationException propagating through tryDecrypt.
        val ciphertextShaped = Base64.encode(ByteArray(32))

        assertFailsWith<CancellationException> { parser(ciphertextShaped, "password") }
    }

    @Test
    fun `plain JSON old vault is parsed even when password is provided`() {
        val mapper = mockk<VaultFromOldJsonMapper>()
        val hexMapper = mockk<MapHexToPlainString>()
        val expectedVault = Vault(id = "id", name = "Ignored Password Vault")
        every { hexMapper(any()) } answers { firstArg() }
        every { mapper(any()) } returns expectedVault

        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mapper,
                mapHexToPlainString = hexMapper,
                encryption = mockk(),
                protoBuf = protoBuf,
                json = Json,
            )

        val oldJson =
            """
            {
              "id": "old-id",
              "localPartyID": "party-1",
              "pubKeyECDSA": "ecdsa",
              "hexChainCode": "chaincode",
              "pubKeyEDDSA": "eddsa",
              "name": "OldVault",
              "signers": ["party-1"],
              "keyshares": [{"pubkey": "pub1", "keyshare": "share1"}]
            }
        """
                .trimIndent()

        val result = parser(oldJson, "whatever-password")

        assertEquals(expectedVault, result)
    }
}
