@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.mappers.VaultFromOldJsonMapper
import com.vultisig.wallet.data.mappers.utils.MapHexToPlainString
import com.vultisig.wallet.data.models.OldJsonVault
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import vultisig.vault.v1.Vault as VaultProto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vultisig.keygen.v1.LibType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertEquals

@OptIn(ExperimentalEncodingApi::class)
internal class ParseVaultFromStringUseCaseImplTest {

    private val protoBuf = ProtoBuf

    private lateinit var useCase: ParseVaultFromStringUseCaseImpl

    @BeforeEach
    fun setUp() {
        useCase = ParseVaultFromStringUseCaseImpl(
            vaultFromOldJsonMapper = mockk<VaultFromOldJsonMapper>(),
            mapHexToPlainString = mockk<MapHexToPlainString>(),
            encryption = mockk<Encryption>(),
            protoBuf = protoBuf,
            json = mockk(),
        )
    }

    private fun encodeVaultContainer(
        vaultProto: VaultProto,
        isEncrypted: Boolean = false,
    ): String {
        val vaultBytes = protoBuf.encodeToByteArray(vaultProto)
        val vaultBase64 = Base64.encode(vaultBytes)
        val container = VaultContainerProto(
            vault = vaultBase64,
            isEncrypted = isEncrypted,
        )
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
    ) = VaultProto(
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
        val proto = testVaultProto(
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
        val proto = testVaultProto(
            chainPublicKeys = listOf(
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
            ),
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
        val proto = testVaultProto(
            keyShares = listOf(
                VaultProto.KeyShare(publicKey = "pubA", keyshare = "shareA1"),
                VaultProto.KeyShare(publicKey = "pubB", keyshare = "shareB1"),
                VaultProto.KeyShare(publicKey = "pubA", keyshare = "shareA2"),
            ),
        )

        val vault = useCase(encodeVaultContainer(proto), null)

        assertEquals(2, vault.keyshares.size)
        val keyshareMap = vault.keyshares.associateBy { it.pubKey }
        assertEquals("shareA2", keyshareMap["pubA"]?.keyShare)
        assertEquals("shareB1", keyshareMap["pubB"]?.keyShare)
    }

    @Test
    fun `parses libType KeyImport from protobuf`() {
        val proto = testVaultProto(
            libType = LibType.LIB_TYPE_KEYIMPORT,
        )

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

        val fallbackUseCase = ParseVaultFromStringUseCaseImpl(
            vaultFromOldJsonMapper = mapper,
            mapHexToPlainString = hexMapper,
            encryption = mockk(),
            protoBuf = protoBuf,
            json = Json,
        )

        val oldJson = """
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
        """.trimIndent()

        val result = fallbackUseCase(oldJson, null)

        assertEquals(expectedVault, result)
        assertEquals("OldVault", capturedOldVault.captured.name)
        assertEquals("eddsa", capturedOldVault.captured.pubKeyEdDSA)
    }
}
