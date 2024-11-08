package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import io.ktor.util.encodeBase64
import timber.log.Timber
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.SolanaAddress
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Solana
import java.math.BigInteger

class SolanaHelper(
    private val vaultHexPublicKey: String,
) {

    private val coinType = CoinType.SOLANA

    companion object {
        val DefaultFeeInLamports: BigInteger = 1000000.toBigInteger()
    }

    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {

        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
            ?: error("Invalid blockChainSpecific")
        if (keysignPayload.coin.chain != Chain.Solana) {
            error("Chain is not Solana")
        }
        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)

        val input = Solana.SigningInput.newBuilder()
            .setRecentBlockhash(solanaSpecific.recentBlockHash)
            .setSender(keysignPayload.coin.address)
            .setPriorityFeePrice(
                Solana.PriorityFeePrice.newBuilder()
                    .setPrice(solanaSpecific.priorityFee.toLong())
                    .build()
            )

        if (keysignPayload.coin.isNativeToken) {
            val transfer = Solana.Transfer.newBuilder()
                .setRecipient(toAddress.description())
                .setValue(keysignPayload.toAmount.toLong())
            keysignPayload.memo?.let {
                transfer.setMemo(it)
            }

            return input
                .setTransferTransaction(transfer.build())
                .build()
                .toByteArray()
        } else {
            if (solanaSpecific.fromAddressPubKey != null && solanaSpecific.toAddressPubKey!= null) {
                val transfer = Solana.TokenTransfer.newBuilder()
                    .setTokenMintAddress(keysignPayload.coin.contractAddress)
                    .setSenderTokenAddress(solanaSpecific.fromAddressPubKey)
                    .setRecipientTokenAddress(solanaSpecific.toAddressPubKey)
                    .setAmount(keysignPayload.toAmount.toLong())
                    .setDecimals(keysignPayload.coin.decimal)

                return input
                    .setTokenTransferTransaction(transfer.build())
                    .build()
                    .toByteArray()
            } else {
                val receiverAddress = SolanaAddress(toAddress.description())
                val generatedRecipientAssociatedAddress = receiverAddress.defaultTokenAddress(
                    keysignPayload.coin.contractAddress
                )
                val transferTokenMessage =
                    Solana.CreateAndTransferToken.newBuilder()
                        .setRecipientMainAddress(toAddress.description())
                        .setTokenMintAddress(keysignPayload.coin.contractAddress)
                        .setRecipientTokenAddress(generatedRecipientAssociatedAddress)
                        .setSenderTokenAddress(solanaSpecific.fromAddressPubKey)
                        .setAmount(keysignPayload.toAmount.toLong())
                        .setDecimals(keysignPayload.coin.decimal)

                return input
                    .setCreateAndTransferTokenTransaction(transferTokenMessage.build())
                    .build()
                    .toByteArray()
            }
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        Timber.d("solana error:${preSigningOutput.errorMessage}")
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            throw Exception(preSigningOutput.errorMessage)
        }
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val input = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, input)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val signature = signatures[key]?.getSignature() ?: throw Exception("Signature not found")
        if (!publicKey.verify(signature, preSigningOutput.data.toByteArray())) {
            throw Exception("Signature verification failed")
        }
        allSignatures.add(signature)
        publicKeys.add(publicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature)
        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.encoded.take(64).encodeBase64()
        )
    }

    fun getZeroSignedTransaction(
        keysignPayload: KeysignPayload,
    ): String {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val input = getPreSignedInputData(keysignPayload)
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        allSignatures.add("0".repeat(128).toHexByteArray())
        publicKeys.add(publicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature)
        return output.encoded
    }
}