package com.vultisig.wallet.data.wallet

import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.models.Chain
import wallet.core.jni.CoinType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.TransactionCompiler.PreSigningOutput

internal object Swaps {

    fun getPreSignedImageHash(
        inputData: ByteArray,
        coinType: CoinType,
        chain: Chain,
    ): List<String> {
        val preImageHashes = TransactionCompiler.preImageHashes(coinType, inputData)

        return when (chain.standard) {
            TokenStandard.UTXO -> {
                val preSigningOutput = Bitcoin.PreSigningOutput.parseFrom(preImageHashes)
                if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
                    throw Exception(preSigningOutput.errorMessage)
                }
                preSigningOutput.hashPublicKeysList.map { Numeric.toHexStringNoPrefix(it.dataHash.toByteArray()) }
            }

            TokenStandard.EVM, TokenStandard.THORCHAIN, TokenStandard.COSMOS ->
                getPreSigningOutput(preImageHashes)

            else -> error("Unsupported chain type $chain")
        }
    }

    fun getPreSigningOutput(preImageHashes: ByteArray): List<String> {
        val preSigningOutput = PreSigningOutput.parseFrom(preImageHashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            throw Exception(preSigningOutput.errorMessage)
        }
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

}