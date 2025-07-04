package com.vultisig.wallet.data.utils

import java.math.BigInteger

fun BigInteger.toHexString(): String {
    return this.toString(16).add0x()
}