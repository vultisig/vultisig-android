package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.PublicKeyHelper
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.models.coinType
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import javax.inject.Inject

internal interface ChainAccountAddressRepository {

    suspend fun getAddress(
        chain: Chain,
        vault: Vault,
    ): String

    suspend fun getAddress(
        type: CoinType,
        publicKey: PublicKey,
    ): String

}

internal class ChainAccountAddressRepositoryImpl @Inject constructor() :
    ChainAccountAddressRepository {

    override suspend fun getAddress(
        chain: Chain,
        vault: Vault,
    ): String = getAddress(
        chain.coinType,
        PublicKeyHelper.getPublicKey(
            vault.pubKeyECDSA,
            vault.hexChainCode,
            chain.coinType,
        )
    )

    override suspend fun getAddress(
        type: CoinType,
        publicKey: PublicKey,
    ): String = type.deriveAddressFromPublicKey(publicKey)

}