@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.chains.helpers.MayaChainHelper
import com.vultisig.wallet.data.chains.helpers.PublicKeyHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import javax.inject.Inject

interface ChainAccountAddressRepository {

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
                val publicKey =
                    PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
                if (chain == Chain.MayaChain) {
                    return Pair(
                        MayaChainHelper.getAddress(publicKey), derivedPublicKey
                    )
                } else {
                    val pk = publicKey.takeIf { chain.coinType != CoinType.TRON }
                        ?: publicKey.uncompressed()
                    val address = adjustAddressPrefix(
                        chain.coinType,
                        chain.coinType.deriveAddressFromPublicKey(pk)
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
    ): Boolean = if (chain == Chain.MayaChain) {
        AnyAddress.isValidBech32(address, chain.coinType, "maya")
    } else {
        chain.coinType.validate(address)
    }

    private fun adjustAddressPrefix(type: CoinType, address: String): String =
        if (type == CoinType.BITCOINCASH) {
            address.replace("bitcoincash:", "")
        } else address

}