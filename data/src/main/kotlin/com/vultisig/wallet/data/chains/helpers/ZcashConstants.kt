package com.vultisig.wallet.data.chains.helpers

/**
 * ZIP-243 consensus branch id WalletCore reads as the personalised-Blake2b sighash identifier for
 * transparent ZEC spends. Single source of truth shared by the native send plan ([UtxoHelper]) and
 * the SwapKit signer ([SwapKitZcashSigner]) so the two digest paths can never drift apart.
 *
 * Regenerate per Zcash network upgrade: take `getblockchaininfo` -> `consensus.nextblock`
 * (currently NU6.2 `5437f330`) and reverse its four bytes to little endian -> `30f33754`.
 */
internal const val ZCASH_ZIP243_BRANCH_ID_HEX = "30f33754"
