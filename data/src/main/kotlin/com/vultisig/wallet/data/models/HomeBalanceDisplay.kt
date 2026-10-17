package com.vultisig.wallet.data.models

/** What the home header's balance means. App-wide, chosen in Settings. */
enum class HomeBalanceDisplay(internal val storedValue: String) {
    /** Each tab shows its own total: wallet holdings on Wallet, positions on DeFi. */
    Separate("separate"),

    /** Both tabs show wallet holdings plus the DeFi value the wallet does not already hold. */
    Combined("combined"),
}
