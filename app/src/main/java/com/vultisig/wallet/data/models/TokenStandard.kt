package com.vultisig.wallet.data.models

enum class TokenStandard {
    ERC20,
    BITCOIN,

    @Deprecated("Don't use, fill with correct data")
    UNKNOWN, // TODO fill with data
}