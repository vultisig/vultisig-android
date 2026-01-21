package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.utils.ServerUtils.LOCAL_PARTY_ID_PREFIX
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
    var libType: SigningLibType = SigningLibType.GG20,
) {

    fun getKeyshare(pubKey: String): String? =
        keyshares.firstOrNull { it.pubKey == pubKey }?.keyShare

}

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

fun Vault.getPubKeyByChain(chain: Chain): String {
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
        Chain.Rootstock,
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