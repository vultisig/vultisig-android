@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.MayaChainHelper
import com.vultisig.wallet.chains.PublicKeyHelper
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.TssKeysignType
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.models.coinType
import com.vultisig.wallet.tss.TssKeyType
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import javax.inject.Inject

internal interface ChainAccountAddressRepository {

    suspend fun getAddress(
        chain: Chain,
        vault: Vault,
    ): Pair<String, String>

    suspend fun getAddress(
        type: CoinType,
        publicKey: PublicKey,
    ): String

    suspend fun getAddress(
        coin: Coin,
        vault: Vault,
    ): Pair<String, String>

    fun isValid(
        chain: Chain,
        address: String,
    ): Boolean

}

internal class ChainAccountAddressRepositoryImpl @Inject constructor() :
    ChainAccountAddressRepository {

    override suspend fun getAddress(
        chain: Chain,
        vault: Vault,
    ): Pair<String, String> {
        when (chain.TssKeysignType) {
            TssKeyType.ECDSA -> {
                val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
                    vault.pubKeyECDSA, vault.hexChainCode, chain.coinType.derivationPath()
                )
                if (chain == Chain.mayaChain) {
                    return Pair(
                        MayaChainHelper(
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getCoin()?.address ?: "", derivedPublicKey
                    )
                } else {
                    val publicKey =
                        PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
                    val address = adjustAddressPrefix(
                        chain.coinType,
                        chain.coinType.deriveAddressFromPublicKey(publicKey)
                    )
                    return Pair(
                        address,
                        derivedPublicKey
                    )
                }
            }

            TssKeyType.EDDSA -> {
                val publicKey =
                    PublicKey(vault.pubKeyEDDSA.hexToByteArray(), PublicKeyType.ED25519)
                return Pair(chain.coinType.deriveAddressFromPublicKey(publicKey), vault.pubKeyEDDSA)
            }
        }

    }

    override suspend fun getAddress(
        type: CoinType,
        publicKey: PublicKey,
    ): String = adjustAddressPrefix(type, type.deriveAddressFromPublicKey(publicKey))

    override suspend fun getAddress(
        coin: Coin,
        vault: Vault,
    ): Pair<String, String> = getAddress(coin.chain, vault)

    override fun isValid(
        chain: Chain,
        address: String,
    ): Boolean = if (chain == Chain.mayaChain) {
        AnyAddress.isValidBech32(address, chain.coinType, "maya")
    } else {
        chain.coinType.validate(address)
    }

    private fun adjustAddressPrefix(type: CoinType, address: String): String =
        if (type == CoinType.BITCOINCASH) {
            address.replace("bitcoincash:", "")
        } else address

}