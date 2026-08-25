package com.vultisig.wallet.data.blockchain.thorchain

/**
 * Rujira wasm contracts that more than one layer has to name.
 *
 * A staking contract is usually named in one place — the builder that spends it — and the app's own
 * DeFi constants carry those. The bRUNE liquid bond is not one of them: the pricing repository
 * reads its `{"status":{}}` NAV to value the ybRUNE receipt, and the bond/unbond executes are built
 * against the same address, so a change to one that missed the other would leave a position priced
 * off a contract it no longer transacts with. Only that address lives here; the rest stay where
 * their single caller is.
 */
object ThorchainStakingContracts {
    const val BRUNE_LIQUID_BOND = "thor179fex2rxd45caedmz4hxsnu42sw20lu0djyh4yukyh965sq8muuqptru2g"
}
