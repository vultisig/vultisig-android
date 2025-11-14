@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.chains.helpers.MayaChainHelper
import com.vultisig.wallet.data.chains.helpers.PublicKeyHelper
import com.vultisig.wallet.data.crypto.CardanoUtils
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.utils.evmCompatibleType
import com.vultisig.wallet.data.utils.evmDerivationPath
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
                    vault.pubKeyECDSA,
                    vault.hexChainCode,
                    chain.coinType.evmDerivationPath()
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
                        chain.coinType.evmCompatibleType,
                        chain.coinType.evmCompatibleType.deriveAddressFromPublicKey(pk)
                    )
                    return Pair(
                        address,
                        derivedPublicKey
                    )
                }
            }

            TssKeyType.EDDSA -> {
                if (chain == Chain.Cardano) {
                    // For Cardano, we still need to create a proper PublicKey for transaction signing
                    // even though we're creating the address manually
                    val address = CardanoUtils.createEnterpriseAddress(vault.pubKeyEDDSA)

                    // Always create Enterprise address to avoid "stake address" component
                    // Use WalletCore's proper Blake2b hashing for deterministic results across all devices
                    // Validate Cardano address using WalletCore's own validation
                    if (!AnyAddress.isValid(
                            address,
                            CoinType.CARDANO
                        )
                    ) {
                        error("WalletCore validation failed for Cardano address: $address")
                    }

                    return Pair(
                        AnyAddress(
                            address,
                            CoinType.CARDANO,
                            "ada"
                        ).description(),
                        vault.pubKeyEDDSA
                    )
                }
                val publicKey =
                    PublicKey(
                        vault.pubKeyEDDSA.hexToByteArray(),
                        PublicKeyType.ED25519
                    )
                return Pair(
                    chain.coinType.deriveAddressFromPublicKey(publicKey),
                    vault.pubKeyEDDSA
                )
            }
        }

    }

    override suspend fun getAddress(
        coin: Coin,
        vault: Vault,
    ): Pair<String, String> = getAddress(coin.chain, vault)

    override fun isValid(
        chain: Chain,
        address: String,
    ): Boolean = when (chain) {
        Chain.MayaChain -> AnyAddress.isValidBech32(
            address,
            chain.coinType,
            "maya"
        )

        Chain.Sei -> AnyAddress.isValid(
            address,
            CoinType.ETHEREUM
        )

        else -> chain.coinType.validate(address)
    }

    private fun adjustAddressPrefix(type: CoinType, address: String): String =
        if (type == CoinType.BITCOINCASH) {
            address.replace("bitcoincash:", "")
        } else address

}