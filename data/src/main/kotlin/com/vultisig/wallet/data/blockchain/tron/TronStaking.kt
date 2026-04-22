package com.vultisig.wallet.data.blockchain.tron

/** Matches exactly FREEZE:BANDWIDTH, FREEZE:ENERGY, UNFREEZE:BANDWIDTH, UNFREEZE:ENERGY */
val TRON_STAKING_MEMO_REGEX = Regex("^(FREEZE|UNFREEZE):(BANDWIDTH|ENERGY)$")
