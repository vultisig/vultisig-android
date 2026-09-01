package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.settings.AppCurrency
import java.math.BigDecimal

data class Address(
    val chain: Chain,
    val address: String,
    val accounts: List<Account>,
    val isDefiProvider: Boolean = false,
)

data class Account(
    val token: Coin,
    val tokenValue: TokenValue?,
    val fiatValue: FiatValue?,
    val price: FiatValue?,
    /**
     * The account exists only to carry a DeFi position, because the vault does not hold this token
     * as a wallet balance — a Kamino Earn deposit in a token the wallet has none of, for instance.
     * Its balance is the position, so it counts towards the DeFi portfolio but is never something a
     * form may spend.
     */
    val isPositionOnly: Boolean = false,
) {
    companion object {
        val EMPTY = Account(token = Coin.EMPTY, tokenValue = null, fiatValue = null, price = null)
    }
}

fun List<Account>.calculateAccountsTotalFiatValue(): FiatValue? =
    this.fold(FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)) { acc, account ->
        // if any account dont have fiat value, return null, as in "not loaded yet"
        val fiatValue = account.fiatValue ?: return@calculateAccountsTotalFiatValue null
        FiatValue(acc.value + fiatValue.value, fiatValue.currency)
    }

/**
 * Strict all-or-nothing total: null as soon as *any* chain hasn't resolved its fiat ("not loaded
 * yet"). Used by the vault list and the delete screen, which want a final figure and would rather
 * show a loading state than an understated partial sum. The home stream wants the partial figure —
 * use [calculateAddressesPartialFiatValue] there.
 */
fun List<Address>.calculateAddressesTotalFiatValue(): FiatValue? =
    this.fold(FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)) { acc, account ->
        // if any account dont have fiat value, return null, as in "not loaded yet"
        val fiatValue =
            account.accounts.calculateAccountsTotalFiatValue()
                ?: return@calculateAddressesTotalFiatValue null
        FiatValue(acc.value + fiatValue.value, fiatValue.currency)
    }

/**
 * Sum of every account's fiat value across all addresses, skipping the ones that haven't resolved
 * yet. Returns null only when *nothing* has loaded (so the UI can show a loading state on a cold
 * start), but as soon as a single chain resolves its value contributes to the total. A slow chain
 * (e.g. Solana) therefore no longer blanks the whole portfolio total — the previously-known chains
 * keep counting while it refetches. See issue #4768.
 *
 * The seed currency is taken from the first resolved value rather than a hardcoded USD, so a
 * non-USD portfolio is labelled correctly (mixed-currency lists were never supported by either
 * fold).
 */
fun List<Address>.calculateAddressesPartialFiatValue(): FiatValue? =
    this.flatMap { it.accounts }.calculateAccountsPartialFiatValue()

/**
 * Lenient per-address total mirroring [calculateAddressesPartialFiatValue]: sums each account's
 * resolved fiat value and skips the ones still pending, returning null only when nothing in the
 * address has loaded. The home row mapper uses this so a row's fiat equals exactly its contribution
 * to the partial portfolio total — a chain holding several tokens with one still pending no longer
 * feeds the headline while its row blanks (the strict [calculateAccountsTotalFiatValue] would null
 * the whole row). See issue #4768.
 */
fun List<Account>.calculateAccountsPartialFiatValue(): FiatValue? {
    val resolved = this.mapNotNull { it.fiatValue }
    if (resolved.isEmpty()) return null
    return resolved.fold(FiatValue(BigDecimal.ZERO, resolved.first().currency)) { acc, fiatValue ->
        FiatValue(acc.value + fiatValue.value, fiatValue.currency)
    }
}

/**
 * How many of the chain's DeFi positions currently hold something.
 *
 * A DeFi address carries one account per token the chain can hold a position in — the vault's own
 * coins plus the injected receipts — and each one's token value *is* the position it backs, so an
 * account above zero is an active position and a zero is a position the vault has not opened. That
 * makes this the count behind the very figure the row already paints, which sums the same accounts.
 *
 * Null while nothing has resolved yet: a cold start would otherwise report a funded chain as having
 * no positions for as long as its balances take to arrive.
 */
fun List<Account>.calculateActiveDefiPositionsCount(): Int? {
    if (isEmpty()) return 0
    if (none { it.tokenValue != null }) return null
    return count { (it.tokenValue?.value?.signum() ?: 0) > 0 }
}
