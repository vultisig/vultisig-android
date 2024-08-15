package com.vultisig.wallet.chains

import com.vultisig.wallet.common.Numeric
import com.vultisig.wallet.common.toHexByteArray
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific
import com.vultisig.wallet.presenter.keysign.KeysignPayload
import com.vultisig.wallet.tss.getSignature
import io.ktor.util.encodeBase64
import timber.log.Timber
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import java.math.BigInteger

internal class SolanaHelper(
    private val vaultHexPublicKey: String,
) {

    private val coinType = CoinType.SOLANA

    companion object {
        internal val DefaultFeeInLamports: BigInteger = 1000000.toBigInteger()
    }

    fun getCoin(): Coin? {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val address = coinType.deriveAddressFromPublicKey(publicKey)
        return Coins.getCoin("SOL", address, vaultHexPublicKey, coinType)
    }

    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
            ?: throw IllegalArgumentException("Invalid blockChainSpecific")
        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)
        val transfer = wallet.core.jni.proto.Solana.Transfer.newBuilder()
            .setRecipient(toAddress.description())
            .setValue(keysignPayload.toAmount.toLong())
        keysignPayload.memo?.let {
            transfer.setMemo(it)
        }
        val input = wallet.core.jni.proto.Solana.SigningInput.newBuilder()
            .setRecentBlockhash(solanaSpecific.recentBlockHash)
            .setSender(keysignPayload.coin.address)
            .setTransferTransaction(transfer.build())
            .setPriorityFeePrice(
                wallet.core.jni.proto.Solana.PriorityFeePrice.newBuilder()
                    .setPrice(solanaSpecific.priorityFee.toLong())
                    .build()
            )
            .build()
        return input.toByteArray()
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
        val output = wallet.core.jni.proto.Solana.SigningOutput.parseFrom(compiledWithSignature)
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
        val output = wallet.core.jni.proto.Solana.SigningOutput.parseFrom(compiledWithSignature)
        return output.encoded
    }
}