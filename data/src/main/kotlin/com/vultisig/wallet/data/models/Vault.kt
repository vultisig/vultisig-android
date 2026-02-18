package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.utils.ServerUtils.LOCAL_PARTY_ID_PREFIX
import com.vultisig.wallet.data.utils.compatibleDerivationPath
import kotlinx.serialization.Serializable

typealias VaultId = String

data class Vault(
    val id: VaultId,
    var name: String,
    var pubKeyECDSA: String = "",
    var pubKeyEDDSA: String = "",
    var hexChainCode: String = "",
    var localPartyID: String = "",
    var signers: List<String> = listOf(),
    var resharePrefix: String = "",
    var keyshares: List<KeyShare> = listOf(),
    val coins: List<Coin> = emptyList(),
    var chainPublicKeys: List<ChainPublicKey> = emptyList(),
    var libType: SigningLibType = SigningLibType.GG20,
) {

    fun getKeyshare(pubKey: String): String? =
        keyshares.firstOrNull { it.pubKey == pubKey }?.keyShare

}

data class ChainPublicKey(
    val chain: String,
    val publicKey: String,
    val isEddsa: Boolean,
)

@Serializable
enum class SigningLibType {
    DKLS,
    GG20,
    KeyImport;

    companion object {
        fun from(string: String) = when (string.lowercase()) {
            "dkls" -> DKLS
            "gg20" -> GG20
            "keyimport" -> KeyImport
            else -> null
        }
    }
}

fun SigningLibType.toProtoString() = when (this) {
    SigningLibType.DKLS -> "dkls"
    SigningLibType.GG20 -> "gg20"
    SigningLibType.KeyImport -> "keyimport"
}

fun Vault.getVaultPart(): Int {
    return signers.indexOf(localPartyID) + 1
}

fun Vault.getSignersExceptLocalParty(): List<String> {
    return signers.filter { it != localPartyID }
}

fun Vault.containsServerSigner(): Boolean {
    return signers.any { it.contains(LOCAL_PARTY_ID_PREFIX, ignoreCase = true) }
}

fun Vault.isServerVault(): Boolean {
    return localPartyID.contains(LOCAL_PARTY_ID_PREFIX, ignoreCase = true)
}

fun Vault.isFastVault(): Boolean {
    return containsServerSigner() && !isServerVault()
}

/**
 * Returns (publicKey, chainCode) for ECDSA signing on the given [chain].
 *
 * For KeyImport vaults, resolves the per-chain key from [chainPublicKeys]:
 * - Exact chain match first, then derivation-path match (e.g. all EVM chains share one key).
 * - Returns empty chainCode when a per-chain key is found, signaling that BIP32 derivation
 *   should be skipped (the key is already fully derived).
 * - Falls back to root (pubKeyECDSA + hexChainCode) for chains not in [chainPublicKeys].
 */
fun Vault.getEcdsaSigningKey(chain: Chain): Pair<String, String> {
    if (libType != SigningLibType.KeyImport) {
        return Pair(pubKeyECDSA, hexChainCode)
    }
    val chainPubKey = chainPublicKeys.firstOrNull { it.chain == chain.raw && !it.isEddsa }
        ?: chainPublicKeys.firstOrNull { cpk ->
            !cpk.isEddsa && try {
                Chain.fromRaw(cpk.chain).coinType.compatibleDerivationPath() ==
                    chain.coinType.compatibleDerivationPath()
            } catch (_: Exception) {
                false
            }
        }
    return if (chainPubKey != null) {
        Pair(chainPubKey.publicKey, "")
    } else {
        Pair(pubKeyECDSA, hexChainCode)
    }
}

fun Vault.getEddsaSigningKey(chain: Chain): String {
    if (libType != SigningLibType.KeyImport) {
        return pubKeyEDDSA
    }
    return chainPublicKeys.firstOrNull { it.chain == chain.raw && it.isEddsa }?.publicKey
        ?: pubKeyEDDSA
}

fun Vault.getPubKeyByChain(chain: Chain): String {
    // For KeyImport vaults, use chain-specific public key if available
    chainPublicKeys.firstOrNull { it.chain == chain.raw }?.let {
        return it.publicKey
    }
    // Fall back to root public key
    return when (chain) {
        Chain.ThorChain,
        Chain.MayaChain -> pubKeyECDSA

        // Evm
        Chain.Arbitrum,
        Chain.Avalanche,
        Chain.Base,
        Chain.CronosChain,
        Chain.BscChain,
        Chain.Blast,
        Chain.Ethereum,
        Chain.Optimism,
        Chain.Polygon,
        Chain.ZkSync,
        Chain.Mantle,
        Chain.Sei,
        Chain.Hyperliquid -> pubKeyECDSA

        // Utxo
        Chain.Bitcoin,
        Chain.BitcoinCash,
        Chain.Litecoin,
        Chain.Dogecoin,
        Chain.Dash,
        Chain.Zcash -> pubKeyECDSA

        Chain.Cardano -> pubKeyEDDSA

        // Cosmos
        Chain.GaiaChain,
        Chain.Kujira,
        Chain.Dydx,
        Chain.Osmosis,
        Chain.Terra,
        Chain.TerraClassic,
        Chain.Noble,
        Chain.Akash -> pubKeyECDSA

        // Others
        Chain.Solana -> pubKeyEDDSA
        Chain.Polkadot -> pubKeyEDDSA
        Chain.Sui -> pubKeyEDDSA
        Chain.Ton -> pubKeyEDDSA
        Chain.Ripple -> pubKeyEDDSA
        Chain.Tron -> pubKeyECDSA
    }
}