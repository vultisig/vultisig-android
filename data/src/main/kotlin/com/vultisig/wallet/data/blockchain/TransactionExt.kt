package com.vultisig.wallet.data.blockchain

fun Transaction.isSwap() = this is SmartContract || this is Swap