package com.vultisig.wallet.data.repositories

import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import javax.inject.Inject

internal interface ChainAccountAddressRepository {

    suspend fun getAddress(
        type: CoinType,
        publicKey: PublicKey
    ): String

}

internal class ChainAccountAddressRepositoryImpl @Inject constructor() :
    ChainAccountAddressRepository {

    override suspend fun getAddress(
        type: CoinType,
        publicKey: PublicKey
    ): String = type.deriveAddressFromPublicKey(publicKey)

}