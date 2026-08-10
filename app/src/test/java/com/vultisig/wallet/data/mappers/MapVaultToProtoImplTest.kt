@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.mappers.utils.MapHexToPlainString
import com.vultisig.wallet.data.models.ChainPublicKey
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import com.vultisig.wallet.data.usecases.ParseVaultFromStringUseCaseImpl
import com.vultisig.wallet.data.usecases.VaultBackupEncryption
import io.mockk.mockk
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalEncodingApi::class)
internal class MapVaultToProtoImplTest {

    private val mapper = MapVaultToProtoImpl()
    private val protoBuf = ProtoBuf

    @Test
    fun `proto carries every chainPublicKey entry`() {
        val proto =
            mapper(
                vault(
                    chainPublicKeys =
                        listOf(
                            ChainPublicKey(
                                chain = "Ethereum",
                                publicKey = "ethPub",
                                isEddsa = false,
                            ),
                            ChainPublicKey(chain = "Solana", publicKey = "solPub", isEddsa = true),
                        )
                )
            )

        assertEquals(2, proto.chainPublicKeys.size)
        // Generated proto element type is nullable; assertNotNull lifts it for direct access.
        val ethereum = assertNotNull(proto.chainPublicKeys[0])
        assertEquals("Ethereum", ethereum.chain)
        assertEquals("ethPub", ethereum.publicKey)
        assertEquals(false, ethereum.isEddsa)
        val solana = assertNotNull(proto.chainPublicKeys[1])
        assertEquals("Solana", solana.chain)
        assertEquals("solPub", solana.publicKey)
        assertEquals(true, solana.isEddsa)
    }

    @Test
    fun `proto carries the MLDSA public key`() {
        val proto = mapper(vault(pubKeyMLDSA = "mldsaPub"))

        assertEquals("mldsaPub", proto.publicKeyMldsa44)
    }

    @Test
    fun `proto leaves chainPublicKeys empty when source has none`() {
        val proto = mapper(vault(chainPublicKeys = emptyList()))

        assertTrue(proto.chainPublicKeys.isEmpty())
        assertEquals("", proto.publicKeyMldsa44)
    }

    @Test
    fun `KeyImport vault round-trips through export and parse without losing fields`() {
        val source =
            vault(
                libType = SigningLibType.KeyImport,
                pubKeyMLDSA = "mldsaPub",
                chainPublicKeys =
                    listOf(
                        ChainPublicKey(chain = "Ethereum", publicKey = "ethPub", isEddsa = false),
                        ChainPublicKey(chain = "Solana", publicKey = "solPub", isEddsa = true),
                    ),
            )

        val restored = roundTrip(source)

        assertEquals(SigningLibType.KeyImport, restored.libType)
        assertEquals("mldsaPub", restored.pubKeyMLDSA)
        assertEquals(2, restored.chainPublicKeys.size)
        assertEquals(source.chainPublicKeys.toSet(), restored.chainPublicKeys.toSet())
    }

    private fun roundTrip(source: Vault): Vault {
        val proto = mapper(source)
        val vaultBytes = protoBuf.encodeToByteArray(proto)
        val container = VaultContainerProto(vault = Base64.encode(vaultBytes), isEncrypted = false)
        val containerBytes = protoBuf.encodeToByteArray(container)
        val parser =
            ParseVaultFromStringUseCaseImpl(
                vaultFromOldJsonMapper = mockk(),
                mapHexToPlainString = mockk<MapHexToPlainString>(),
                encryption = mockk<VaultBackupEncryption>(),
                protoBuf = protoBuf,
                json = Json,
            )
        return parser(Base64.encode(containerBytes), null)
    }

    private fun vault(
        chainPublicKeys: List<ChainPublicKey> = emptyList(),
        pubKeyMLDSA: String = "",
        libType: SigningLibType = SigningLibType.DKLS,
    ): Vault =
        Vault(
            id = "vault-id",
            name = "Test",
            pubKeyECDSA = "ecdsa",
            pubKeyEDDSA = "eddsa",
            hexChainCode = "chainCode",
            localPartyID = "party",
            signers = listOf("party"),
            resharePrefix = "",
            libType = libType,
            chainPublicKeys = chainPublicKeys,
            pubKeyMLDSA = pubKeyMLDSA,
            // Every real vault has keyshares, and the mapper now refuses to export one that does
            // not: an empty list means they were dropped on the way out of storage, and exporting
            // that yields a .vult that restores cleanly and can never sign.
            keyshares = listOf(KeyShare(pubKey = "ecdsa", keyShare = "share")),
        )

    @Test
    fun `a vault with no keyshares is refused rather than exported`() {
        assertFailsWith<VaultKeysharesUnavailableException> {
            mapper(vault().copy(keyshares = emptyList()))
        }
    }

    @Test
    fun `exportableOrNull turns that refusal into a null the backup paths can report`() {
        assertNull(mapper.exportableOrNull(vault().copy(keyshares = emptyList())))
        assertNotNull(mapper.exportableOrNull(vault()))
    }

    /**
     * A vault whose stored rows were part plaintext, part encrypted, read with no data key, is
     * refused.
     *
     * The other half of this claim lives in `VaultRepositoryImplTest.get drops every keyshare when
     * only some can be read while locked`, which is where that vault is actually produced —
     * `VaultRepositoryImpl` is internal to the data module, so the two halves cannot meet in one
     * test. This one pins the side the mapper owns: given the all-or-nothing read, checking for
     * emptiness is what refuses a partial keyshare set, and it must keep doing so.
     */
    @Test
    fun `a partially readable keyshare set reaches the mapper as empty and is refused`() {
        val fromPartialLockedRead = vault().copy(keyshares = emptyList())

        assertFailsWith<VaultKeysharesUnavailableException> { mapper(fromPartialLockedRead) }
        assertNull(mapper.exportableOrNull(fromPartialLockedRead))
    }
}
