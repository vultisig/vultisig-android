package com.vultisig.wallet.data.models

import kotlinx.serialization.Serializable

/**
 * A `type=Deposit` link: a THORChain / MayaChain `MsgDeposit` memo handed over by a partner dApp.
 * The link is attacker-supplied, so it only ever prefills the Custom deposit form; the user still
 * reviews and signs.
 */
@Serializable
data class DepositDeeplinkData(
    val assetChain: String?,
    val assetTicker: String?,
    val amount: String?,
    val memo: String?,
) {
    /** The chain the link deposits on; null for anything but THORChain / MayaChain mainnet. */
    val chain: Chain?
        get() = DEPOSIT_CHAINS.firstOrNull { it.raw.equals(assetChain, ignoreCase = true) }

    /** Whether the link itself can ever succeed, checked before a vault is picked. */
    val isValid: Boolean
        get() =
            chain != null &&
                !memo.isNullOrBlank() &&
                assetTicker?.trim().orEmpty().let { it.isEmpty() || TICKER.matches(it) }

    /** The amount when it is a plain dot-decimal; anything else is dropped, not guessed at. */
    val prefillAmount: String?
        get() = amount?.trim()?.takeIf { DECIMAL_AMOUNT.matches(it) }

    /**
     * The vault coin the deposit rides on: the named ticker, or the chain's native coin. A ticker
     * shared by several coins is ambiguous, so it matches none.
     */
    fun findCoin(coins: List<Coin>): Coin? {
        val chain = chain ?: return null
        val ticker = assetTicker?.trim().orEmpty()
        return coins.singleOrNull { coin ->
            coin.chain == chain &&
                if (ticker.isEmpty()) coin.isNativeToken
                else coin.ticker.equals(ticker, ignoreCase = true)
        }
    }
}

private val DEPOSIT_CHAINS = listOf(Chain.ThorChain, Chain.MayaChain)

private val DECIMAL_AMOUNT = Regex("""\d+(\.\d+)?""")

private val TICKER = Regex("""[A-Za-z0-9.-]{1,15}""")
