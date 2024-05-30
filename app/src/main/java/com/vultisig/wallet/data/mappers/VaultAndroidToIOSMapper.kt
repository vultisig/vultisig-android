package com.vultisig.wallet.data.mappers

import com.vultisig.wallet.models.IOSKeyShare
import com.vultisig.wallet.models.IOSVault
import com.vultisig.wallet.models.IOSVaultRoot
import com.vultisig.wallet.models.Vault
import javax.inject.Inject

internal interface VaultAndroidToIOSMapper : MapperFunc<Vault, IOSVaultRoot>

internal class VaultAndroidToIOSMapperImpl @Inject constructor() : VaultAndroidToIOSMapper {

    override fun invoke(from: Vault): IOSVaultRoot = IOSVaultRoot(
        version = "v1",
        vault = IOSVault(
            id = from.id,
            coins = from.coins,
            localPartyID = from.localPartyID,
            pubKeyECDSA = from.pubKeyECDSA,
            hexChainCode = from.hexChainCode,
            pubKeyEdDSA = from.pubKeyEDDSA,
            name = from.name,
            signers = from.signers,
            createdAt = 0f,
            keyshares = from.keyshares.map { IOSKeyShare(it.pubKey,it.keyshare) }
        )
    )

}