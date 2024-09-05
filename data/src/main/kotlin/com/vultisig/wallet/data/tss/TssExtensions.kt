package com.vultisig.wallet.data.tss

import com.vultisig.wallet.data.utils.Numeric
import tss.KeysignResponse

fun KeysignResponse.getSignatureWithRecoveryID(): ByteArray {
    val rBytes = Numeric.hexStringToByteArray(r)
    val sBytes = Numeric.hexStringToByteArray(s)
    val recoveryId = Numeric.hexStringToByteArray(recoveryID)
    return rBytes + sBytes + recoveryId
}

fun KeysignResponse.getSignature(): ByteArray {
    val rBytes = Numeric.hexStringToByteArray(r)
    val sBytes = Numeric.hexStringToByteArray(s)
    return rBytes.reversedArray() + sBytes.reversedArray()
}