@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.chains.helpers.BittensorHelper
import com.vultisig.wallet.data.chains.helpers.MayaChainHelper
import com.vultisig.wallet.data.chains.helpers.PublicKeyHelper
import com.vultisig.wallet.data.crypto.CardanoUtils
import com.vultisig.wallet.data.crypto.QbtcHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainPublicKey
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.utils.compatibleDerivationPath
import com.vultisig.wallet.data.utils.compatibleType
import javax.inject.Inject
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

interface ChainAccountAddressRepository {

    suspend fun getAddress(chain: Chain, vault: Vault): Pair<String, String>

    suspend fun getAddress(coin: Coin, vault: Vault): Pair<String, String>

    fun isValid(chain: Chain, address: String): Boolean
}

internal class ChainAccountAddressRepositoryImpl @Inject constructor() :
    ChainAccountAddressRepository {

    override suspend fun getAddress(chain: Chain, vault: Vault): Pair<String, String> {
        // For KeyImport vaults, chain-specific public keys are already derived.
        // Look for exact chain match first, then match by derivation path
        // (e.g., all EVM chains share m/44'/60'/0'/0/0)
        val chainPubKey = findChainPublicKey(chain, vault)

        when (chain.TssKeysignType) {
            TssKeyType.MLDSA -> {
                val mldsaPubKey = vault.pubKeyMLDSA
                require(mldsaPubKey.isNotBlank()) { "MLDSA public key is required for QBTC" }
                val address = QbtcHelper.deriveAddress(mldsaPubKey)
                return Pair(address, mldsaPubKey)
            }

            TssKeyType.ECDSA -> {
                val derivedPublicKey =
                    if (chainPubKey != null) {
                        chainPubKey.publicKey
                    } else {
                        PublicKeyHelper.getDerivedPublicKey(
                            vault.pubKeyECDSA,
                            vault.hexChainCode,
                            chain.coinType.compatibleDerivationPath(),
                        )
                    }
                val publicKey =
                    PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
                if (chain == Chain.MayaChain) {
                    return Pair(MayaChainHelper.getAddress(publicKey), derivedPublicKey)
                } else {
                    val pk =
                        publicKey.takeIf { chain.coinType != CoinType.TRON }
                            ?: publicKey.uncompressed()
                    val address =
                        adjustAddressPrefix(
                            chain.coinType.compatibleType,
                            chain.coinType.compatibleType.deriveAddressFromPublicKey(pk),
                        )
                    return Pair(address, derivedPublicKey)
                }
            }

            TssKeyType.EDDSA -> {
                val eddsaPubKey = chainPubKey?.publicKey ?: vault.pubKeyEDDSA

                if (chain == Chain.Cardano) {
                    val address = CardanoUtils.createEnterpriseAddress(eddsaPubKey)

                    if (!AnyAddress.isValid(address, CoinType.CARDANO)) {
                        error("WalletCore validation failed for Cardano address: $address")
                    }

                    return Pair(
                        AnyAddress(address, CoinType.CARDANO, "ada").description(),
                        eddsaPubKey,
                    )
                }
                val publicKey = PublicKey(eddsaPubKey.hexToByteArray(), PublicKeyType.ED25519)
                if (chain == Chain.Bittensor) {
                    // eddsaPubKey is the raw 32-byte ed25519 hex — use first 64 hex chars
                    val rawKey = BittensorHelper.hexToBytes(eddsaPubKey.take(64))
                    val address = BittensorHelper.ss58Encode(rawKey)
                    return Pair(address, eddsaPubKey)
                }
                return Pair(chain.coinType.deriveAddressFromPublicKey(publicKey), eddsaPubKey)
            }
        }
    }

    override suspend fun getAddress(coin: Coin, vault: Vault): Pair<String, String> =
        getAddress(coin.chain, vault)

    override fun isValid(chain: Chain, address: String): Boolean =
        when (chain) {
            Chain.MayaChain -> AnyAddress.isValidBech32(address, chain.coinType, "smaya")

            Chain.Qbtc -> AnyAddress.isValidBech32(address, CoinType.COSMOS, "qbtc")

            Chain.Sei -> AnyAddress.isValid(address, CoinType.ETHEREUM)

            Chain.Bittensor -> AnyAddress.isValidSS58(address, CoinType.POLKADOT, 42)

            else -> chain.coinType.validate(address)
        }

    /**
     * For KeyImport vaults, find the chain-specific public key. First tries an exact chain match,
     * then falls back to finding another chain with the same derivation path (e.g., all EVM chains
     * share m/44'/60'/0'/0/0).
     */
    private fun findChainPublicKey(chain: Chain, vault: Vault): ChainPublicKey? {
        if (vault.libType != SigningLibType.KeyImport) return null

        val isEddsa = chain.TssKeysignType == TssKeyType.EDDSA

        // Exact chain match
        val exact =
            vault.chainPublicKeys.firstOrNull { it.chain == chain.raw && it.isEddsa == isEddsa }
        if (exact != null) return exact

        // For ECDSA chains, find another chain with the same derivation path
        if (!isEddsa) {
            val targetDerivePath = chain.coinType.compatibleDerivationPath()
            return vault.chainPublicKeys.firstOrNull { cpk ->
                !cpk.isEddsa &&
                    try {
                        Chain.fromRaw(cpk.chain).coinType.compatibleDerivationPath() ==
                            targetDerivePath
                    } catch (_: Exception) {
                        false
                    }
            }
        }

        return null
    }

    private fun adjustAddressPrefix(type: CoinType, address: String): String =
        if (type == CoinType.BITCOINCASH) {
            address.replace("bitcoincash:", "")
        } else address
}
