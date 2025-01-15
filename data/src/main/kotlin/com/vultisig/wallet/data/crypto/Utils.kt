package com.vultisig.wallet.data.crypto

import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Solana
import wallet.core.jni.proto.TransactionCompiler.PreSigningOutput

fun PreSigningOutput.checkError(): PreSigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("PreSigningOutput contains error: $errorMessage")
    }
    return this
}

fun Bitcoin.PreSigningOutput.checkError(): Bitcoin.PreSigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("PreSigningOutput contains error: $errorMessage")
    }
    return this
}


fun Solana.PreSigningOutput.checkError(): Solana.PreSigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("PreSigningOutput contains error: $errorMessage")
    }
    return this
}