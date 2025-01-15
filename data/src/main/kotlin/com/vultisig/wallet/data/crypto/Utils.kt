package com.vultisig.wallet.data.crypto

import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Cosmos
import wallet.core.jni.proto.Ethereum
import wallet.core.jni.proto.Polkadot
import wallet.core.jni.proto.Solana
import wallet.core.jni.proto.Sui
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

fun Ethereum.SigningOutput.checkError(): Ethereum.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

fun Bitcoin.SigningOutput.checkError(): Bitcoin.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

fun Cosmos.SigningOutput.checkError(): Cosmos.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

fun Solana.SigningOutput.checkError(): Solana.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

fun Polkadot.SigningOutput.checkError(): Polkadot.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

fun Sui.SigningOutput.checkError(): Sui.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}