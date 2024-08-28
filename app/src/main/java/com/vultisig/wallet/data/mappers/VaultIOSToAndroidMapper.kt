package com.vultisig.wallet.data.mappers

import com.google.gson.Gson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.IOSVaultRoot
import com.vultisig.wallet.models.Vault
import timber.log.Timber
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
            pubKeyECDSA = vault.pubKeyECDSA,
            pubKeyEDDSA = vault.pubKeyEdDSA,
            hexChainCode = vault.hexChainCode,
            localPartyID = vault.localPartyID,
            signers = vault.signers,
            resharePrefix = vault.keyShares[0].keyShare.extractResharePrefix(),
            keyshares = vault.keyShares.map { KeyShare(it.pubKey,it.keyShare) },
            coins = try {
                vault.coins?.map { coin ->
                    coin.copy(address = adjustAddressPrefix(coin))
                } ?: emptyList()
            } catch (e: Exception) {
                // if there's a problem with coins, just ignore them
                Timber.e(e)
                emptyList()
            },
        )
    }

    private fun adjustAddressPrefix(coin: Coin) =
        if (coin.chain == Chain.BitcoinCash) {
            coin.address.replace("bitcoincash:", "")
        } else coin.address

    private fun String.extractResharePrefix(): String {
        return gson.fromJson(this, HashMap::class.java)["reshare_prefix"].toString()
    }
}
