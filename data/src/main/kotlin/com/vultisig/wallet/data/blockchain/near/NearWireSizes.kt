package com.vultisig.wallet.data.blockchain.near

/** Bytes in a NEAR block hash, as the node returns it and the transaction carries it. */
internal const val NEAR_BLOCK_HASH_BYTES = 32

/** Bytes in an Ed25519 public key, the only key type NEAR implicit accounts use. */
internal const val NEAR_ED25519_PUBLIC_KEY_BYTES = 32
