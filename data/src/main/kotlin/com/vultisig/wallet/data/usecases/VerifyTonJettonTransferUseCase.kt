package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.tonUserFriendlyAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import javax.inject.Inject

/**
 * Checks that a TON jetton transfer moves the jetton it shows before a co-signer signs it.
 *
 * The signer sends the transfer from the payload's `tonSpecific.jettonAddress` and shows the
 * payload coin's ticker and decimals, and none of those are tied to each other: a payload can show
 * "1 FOO" (9 decimals) while naming the vault's USDT jetton wallet, which moves 1,000 USDT. So the
 * jetton wallet must be the vault's own wallet for the coin's jetton master, and the coin's ticker
 * and decimals must be the vault's own for that master — or, for a jetton the vault does not list,
 * the decimals the master itself reports.
 *
 * Throws [IllegalStateException] on a mismatch, and lets a failed lookup propagate: a transfer
 * that cannot be checked is not signed.
 */
class VerifyTonJettonTransferUseCase @Inject constructor(private val tonApi: TonApi) {

    suspend operator fun invoke(payload: KeysignPayload, vaultCoins: List<Coin>) {
        val coin = payload.coin
        // A TonConnect request is signed from its own BOC, decoded for display separately.
        if (coin.chain != Chain.Ton || coin.isNativeToken || payload.signTon != null) return

        val tonSpecific =
            checkNotNull(payload.blockChainSpecific as? BlockChainSpecific.Ton) {
                "TON jetton transfer without TON chain data"
            }
        val vaultJettonWallet =
            tonApi
                .getJettonWallet(coin.address, coin.contractAddress)
                .getJettonsAddress(coin.contractAddress, ::tonUserFriendlyAddress)
        check(sameTonAddress(vaultJettonWallet, tonSpecific.jettonAddress)) {
            "The transfer's jetton wallet is not this vault's ${coin.ticker} wallet"
        }

        val vaultCoin =
            vaultCoins.firstOrNull {
                it.chain == Chain.Ton &&
                    !it.isNativeToken &&
                    sameTonAddress(it.contractAddress, coin.contractAddress)
            }
        if (vaultCoin != null) {
            check(
                vaultCoin.decimal == coin.decimal &&
                    vaultCoin.ticker.equals(coin.ticker, ignoreCase = true)
            ) {
                "The transfer's token details don't match this vault's ${vaultCoin.ticker}"
            }
        } else {
            val metadata = tonApi.getJettonMetadata(coin.contractAddress)
            check(metadata != null && metadata.decimals == coin.decimal) {
                "The transfer's token decimals don't match its jetton"
            }
        }
    }

    private fun sameTonAddress(a: String?, b: String?): Boolean {
        val left = a?.let(::tonUserFriendlyAddress) ?: return false
        val right = b?.let(::tonUserFriendlyAddress) ?: return false
        return left == right
    }
}
