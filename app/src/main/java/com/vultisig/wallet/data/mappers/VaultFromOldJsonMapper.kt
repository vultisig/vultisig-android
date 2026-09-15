@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.OldJsonVault
import com.vultisig.wallet.data.models.Vault
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal interface VaultFromOldJsonMapper : MapperFunc<OldJsonVault, Vault>

internal class VaultFromOldJsonMapperImpl @Inject constructor(private val json: Json) :
    VaultFromOldJsonMapper {
    override fun invoke(vault: OldJsonVault): Vault {
        return Vault(
            id = vault.id ?: Uuid.random().toString(),
            name = vault.name,
            pubKeyECDSA = vault.pubKeyECDSA,
            pubKeyEDDSA = vault.pubKeyEdDSA,
            hexChainCode = vault.hexChainCode,
            localPartyID = vault.localPartyID,
            signers = vault.signers,
            resharePrefix =
                json.decodeFromString<OldVaultReshare>(vault.keyShares[0].keyShare).resharePrefix,
            keyshares = vault.keyShares.map { KeyShare(it.pubKey, it.keyShare) },
            coins = emptyList(),
        )
    }

    @Serializable
    private data class OldVaultReshare(@SerialName("reshare_prefix") val resharePrefix: String)
}
