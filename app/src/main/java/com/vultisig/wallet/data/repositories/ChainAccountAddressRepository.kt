package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.MayaChainHelper
import com.vultisig.wallet.chains.PublicKeyHelper
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.TssKeysignType
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.tss.TssKeyType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import javax.inject.Inject

internal interface ChainAccountAddressRepository {

    suspend fun getAddress(
        coin: Coin,
        vault: Vault,
    ): Pair<String, String>

}

internal class ChainAccountAddressRepositoryImpl @Inject constructor() :
    ChainAccountAddressRepository {

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun getAddress(
        coin: Coin,
        vault: Vault,
    ): Pair<String, String> {
        when (coin.chain.TssKeysignType) {
            TssKeyType.ECDSA -> {
                val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
                    vault.pubKeyECDSA, vault.hexChainCode, coin.coinType.derivationPath()
                )
                if (coin.chain == Chain.mayaChain) {
                    return Pair(
                        MayaChainHelper(
                            vault.pubKeyECDSA,
                            vault.hexChainCode
                        ).getCoin()?.address ?: "", derivedPublicKey
                    )
                } else {
                    val publicKey =
                        PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
                    return Pair(
                        coin.coinType.deriveAddressFromPublicKey(publicKey),
                        derivedPublicKey
                    )
                }
            }

            TssKeyType.EDDSA -> {
                val publicKey =
                    PublicKey(vault.pubKeyEDDSA.hexToByteArray(), PublicKeyType.ED25519)
                return Pair(coin.coinType.deriveAddressFromPublicKey(publicKey), vault.pubKeyEDDSA)
            }
        }

    }

}