package com.vultisig.wallet.ui.models.deposit

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue

/**
 * Static merge-token descriptor: the THORChain ticker and its wrapping contract address.
 *
 * @property ticker the merge token's display ticker (e.g. `KUJI`).
 * @property contract the THORChain contract address backing the token.
 */
internal data class TokenMergeInfo(val ticker: String, val contract: String) {

    /** The THORChain denom for this token, e.g. `thor.kuji`. */
    val denom: String
        get() = "thor.$ticker".lowercase()
}

/**
 * Static descriptor for a secured asset available to withdraw, pairing chain metadata with the
 * user's current balance.
 *
 * @property ticker the asset ticker.
 * @property contract the asset's contract address.
 * @property coin the backing [Coin].
 * @property tokenValue the user's balance for this asset, or `null` when unknown.
 */
internal data class TokenWithdrawSecureAsset(
    val ticker: String,
    val contract: String,
    val coin: Coin,
    val tokenValue: TokenValue?,
) {
    companion object {
        /** Placeholder shown before the user has picked a secured asset to withdraw. */
        val EMPTY =
            TokenWithdrawSecureAsset(
                ticker = "Select Asset",
                contract = "",
                coin = Coin.EMPTY,
                tokenValue = null,
            )
    }
}

/** Catalogue of THORChain merge tokens offered on the Merge / UnMerge deposit sub-forms. */
internal val tokensToMerge =
    listOf(
        TokenMergeInfo(
            ticker = "KUJI",
            contract = "thor14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s3p2nzy",
        ),
        TokenMergeInfo(
            ticker = "rKUJI",
            contract = "thor1yyca08xqdgvjz0psg56z67ejh9xms6l436u8y58m82npdqqhmmtqrsjrgh",
        ),
        TokenMergeInfo(
            ticker = "FUZN",
            contract = "thor1suhgf5svhu4usrurvxzlgn54ksxmn8gljarjtxqnapv8kjnp4nrsw5xx2d",
        ),
        TokenMergeInfo(
            ticker = "NSTK",
            contract = "thor1cnuw3f076wgdyahssdkd0g3nr96ckq8cwa2mh029fn5mgf2fmcmsmam5ck",
        ),
        TokenMergeInfo(
            ticker = "WINK",
            contract = "thor1yw4xvtc43me9scqfr2jr2gzvcxd3a9y4eq7gaukreugw2yd2f8tsz3392y",
        ),
        TokenMergeInfo(
            ticker = "LVN",
            contract = "thor1ltd0maxmte3xf4zshta9j5djrq9cl692ctsp9u5q0p9wss0f5lms7us4yf",
        ),
    )
