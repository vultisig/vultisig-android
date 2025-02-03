package com.vultisig.wallet.data.crypto

import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Cosmos
import wallet.core.jni.proto.Ethereum
import wallet.core.jni.proto.Polkadot
import wallet.core.jni.proto.Solana
import wallet.core.jni.proto.Sui
import wallet.core.jni.proto.TransactionCompiler.PreSigningOutput
import wallet.core.jni.proto.Tron

internal fun PreSigningOutput.checkError(): PreSigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("PreSigningOutput contains error: $errorMessage")
    }
    return this
}

internal fun Bitcoin.PreSigningOutput.checkError(): Bitcoin.PreSigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("PreSigningOutput contains error: $errorMessage")
    }
    return this
}


internal fun Solana.PreSigningOutput.checkError(): Solana.PreSigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("PreSigningOutput contains error: $errorMessage")
    }
    return this
}

internal fun Ethereum.SigningOutput.checkError(): Ethereum.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

internal fun Bitcoin.SigningOutput.checkError(): Bitcoin.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

internal fun Cosmos.SigningOutput.checkError(): Cosmos.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

internal fun Solana.SigningOutput.checkError(): Solana.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

internal fun Polkadot.SigningOutput.checkError(): Polkadot.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

internal fun Sui.SigningOutput.checkError(): Sui.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}

internal fun Tron.SigningOutput.checkError(): Tron.SigningOutput {
    if (!errorMessage.isNullOrEmpty()) {
        error("SigningOutput contains error: $errorMessage")
    }
    return this
}
internal fun  Solana.DecodingTransactionOutput.checkError():   Solana.DecodingTransactionOutput{
    if (!errorMessage.isNullOrEmpty()) {
        error(" Decoding Transaction Output contains error: $errorMessage")
    }
    return this
}