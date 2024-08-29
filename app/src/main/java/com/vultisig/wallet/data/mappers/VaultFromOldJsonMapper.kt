package com.vultisig.wallet.data.mappers

import com.google.gson.Gson
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.OldJsonVaultRoot
import com.vultisig.wallet.data.models.Vault
import java.util.UUID
import javax.inject.Inject

internal interface VaultFromOldJsonMapper : MapperFunc<OldJsonVaultRoot, Vault>

internal class VaultFromOldJsonMapperImpl @Inject constructor(private val gson: Gson) :
    VaultFromOldJsonMapper {
    override fun invoke(from: OldJsonVaultRoot): Vault {
        val vault = from.vault
        return Vault(
            id = vault.id ?: UUID.randomUUID().toString(),
            name = vault.name,
            pubKeyECDSA = vault.pubKeyECDSA,
            pubKeyEDDSA = vault.pubKeyEdDSA,
            hexChainCode = vault.hexChainCode,
            localPartyID = vault.localPartyID,
            signers = vault.signers,
            resharePrefix = vault.keyShares[0].keyShare.extractResharePrefix(),
            keyshares = vault.keyShares.map { KeyShare(it.pubKey,it.keyShare) },
            coins = emptyList(),
        )
    }

    private fun String.extractResharePrefix(): String {
        return gson.fromJson(this, HashMap::class.java)["reshare_prefix"].toString()
    }
}
