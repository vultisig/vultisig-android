package com.vultisig.wallet.data.utils

import com.vultisig.wallet.data.common.add0x
import java.math.BigInteger

fun BigInteger.toHexString(): String {
    return this.toString(16).add0x()
}