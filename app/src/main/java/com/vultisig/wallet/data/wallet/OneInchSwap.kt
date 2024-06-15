package com.vultisig.wallet.data.wallet

import com.google.protobuf.ByteString
import com.vultisig.wallet.chains.EvmHelper
import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.common.toHexBytesInByteString
import com.vultisig.wallet.data.api.models.OneInchSwapQuoteJson
import com.vultisig.wallet.data.models.OneInchSwapPayloadJson
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import tss.KeysignResponse
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Ethereum.SigningInput
import wallet.core.jni.proto.Ethereum.Transaction

internal class OneInchSwap(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {

    fun getPreSignedImageHash(
        swapPayload: OneInchSwapPayloadJson,
        keysignPayload: KeysignPayload,
    ): List<String> {
        val inputData = getPreSignedInputData(swapPayload.quote, keysignPayload)

        val chain = swapPayload.fromCoin.chain
        when (chain.standard) {
            TokenStandard.UTXO -> {
                val hashes =
                    TransactionCompiler.preImageHashes(keysignPayload.coin.coinType, inputData)
                val preSigningOutput =
                    Bitcoin.PreSigningOutput.parseFrom(hashes)
                return preSigningOutput.hashPublicKeysList.map { Numeric.toHexStringNoPrefix(it.dataHash.toByteArray()) }
            }

            TokenStandard.EVM, TokenStandard.THORCHAIN, TokenStandard.COSMOS -> {
                val hashes =
                    TransactionCompiler.preImageHashes(keysignPayload.coin.coinType, inputData)
                val preSigningOutput =
                    wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
            }

            else -> error("Unsupported chain type $chain")
        }
    }

    private fun getPreSignedInputData(
        quote: OneInchSwapQuoteJson,
        keysignPayload: KeysignPayload
    ): ByteArray {
        val input = SigningInput.newBuilder()
            .setToAddress(quote.tx.to)
            .setTransaction(
                Transaction.newBuilder()
                    .setContractGeneric(
                        Transaction.ContractGeneric.newBuilder()
                            .setAmount(
                                ByteString.copyFrom(
                                    quote.tx.value.toBigInteger().toByteArray()
                                )
                            )
                            .setData(quote.tx.data.toHexBytesInByteString())
                    )
            )

        val gasPrice = quote.tx.gasPrice.toBigInteger()
        val gas = EvmHelper.DefaultEthSwapGasUnit.toBigInteger()
        return EvmHelper(
            keysignPayload.coin.coinType,
            vaultHexPublicKey,
            vaultHexChainCode,
        ).getPreSignedInputData(gas, gasPrice, input, keysignPayload)
    }

    fun getSignedTransaction(
        swapPayload: OneInchSwapPayloadJson,
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = getPreSignedInputData(swapPayload.quote, keysignPayload)
        val helper =
            EvmHelper(keysignPayload.coin.coinType, vaultHexPublicKey, vaultHexChainCode)
        return helper.getSignedTransaction(inputData, signatures)
    }

}