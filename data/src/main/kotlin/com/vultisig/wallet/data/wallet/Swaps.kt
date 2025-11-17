package com.vultisig.wallet.data.wallet

import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.utils.Numeric
import wallet.core.jni.CoinType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Solana
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
                    .checkError()
                preSigningOutput.hashPublicKeysList.map { Numeric.toHexStringNoPrefix(it.dataHash.toByteArray()) }
            }

            TokenStandard.EVM, TokenStandard.THORCHAIN, TokenStandard.COSMOS , TokenStandard.RIPPLE, TokenStandard.TRC20  ->
                getPreSigningOutput(preImageHashes)

            TokenStandard.SOL ->{
                val preSigningOutput = Solana.PreSigningOutput.parseFrom(preImageHashes)
                    .checkError()
                return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray()))
            }

            else -> error("Unsupported chain type $chain")
        }
    }

    fun getPreSigningOutput(preImageHashes: ByteArray): List<String> {
        val preSigningOutput = PreSigningOutput.parseFrom(preImageHashes)
            .checkError()
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

}