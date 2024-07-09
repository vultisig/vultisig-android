package com.vultisig.wallet.data.mappers

import com.google.gson.Gson
import com.vultisig.wallet.models.IOSVaultRoot
import com.vultisig.wallet.models.KeyShare
import com.vultisig.wallet.models.Vault
import java.util.UUID
import javax.inject.Inject

internal interface VaultIOSToAndroidMapper : MapperFunc<IOSVaultRoot, Vault>

internal class VaultIOSToAndroidMapperImpl @Inject constructor(private val gson: Gson) :
    VaultIOSToAndroidMapper {
    override fun invoke(from: IOSVaultRoot): Vault {
        val vault = from.vault
        return Vault(
            id = vault.id ?: UUID.randomUUID().toString(),
            name = vault.name,
            backedUp = true,
            pubKeyECDSA = vault.pubKeyECDSA,
            pubKeyEDDSA = vault.pubKeyEdDSA,
            hexChainCode = vault.hexChainCode,
            localPartyID = vault.localPartyID,
            signers = vault.signers,
            resharePrefix = vault.keyshares[0].keyshare.extractResharePrefix(),
            keyshares = vault.keyshares.map { KeyShare(it.pubkey,it.keyshare) },
            coins = vault.coins?: emptyList()
        )
    }

    private fun String.extractResharePrefix(): String {
        return gson.fromJson(this, HashMap::class.java)["reshare_prefix"].toString()
    }
}
