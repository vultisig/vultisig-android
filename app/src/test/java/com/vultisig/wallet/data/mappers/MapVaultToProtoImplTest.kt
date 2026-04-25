@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.mappers.utils.MapHexToPlainString
import com.vultisig.wallet.data.models.ChainPublicKey
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.VaultContainerProto
import com.vultisig.wallet.data.usecases.ParseVaultFromStringUseCaseImpl
import com.vultisig.wallet.data.usecases.VaultBackupEncryption
import io.mockk.mockk
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.assertNotNull
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
        )
}
