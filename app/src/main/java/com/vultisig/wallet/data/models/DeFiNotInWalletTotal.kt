package com.vultisig.wallet.data.models

/**
 * The DeFi addresses a vault surfaces: only the chains it selected for DeFi, and the Ethereum
 * (Circle) row only for a vault that already opened a Circle account.
 */
internal fun List<Address>.selectedDeFiAddresses(
    selectedChains: Set<Chain>,
    hasCircleAccount: Boolean,
): List<Address> = filter { address ->
    // New Circle (USDC yield) deposits are disabled; a vault with an account keeps the row so it
    // can still withdraw.
    val isCircleAllowed = address.chain != Chain.Ethereum || hasCircleAccount
    address.chain in selectedChains && isCircleAllowed
}

/** DeFi value the wallet total does not already count; position-only accounts are not in it. */
internal fun List<Address>.deFiNotInWalletTotal(): FiatValue? =
    flatMap { it.accounts }
        .filterNot { !it.isPositionOnly && Coins.isWalletHeldDefiReceipt(it.token) }
        .calculateAccountsPartialFiatValue()

/**
 * The "Wallet + DeFi" figure: null while [wallet] is unknown or the two sides are in different
 * currencies; a null [deFi] (no DeFi chains, or nothing resolved yet) adds nothing.
 */
internal fun combinedWalletAndDeFiTotal(wallet: FiatValue?, deFi: FiatValue?): FiatValue? {
    if (wallet == null) return null
    if (deFi == null) return wallet
    // The two load separately, so one can still be in the previous currency.
    if (deFi.currency != wallet.currency) return null
    return FiatValue(wallet.value + deFi.value, wallet.currency)
}
