package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
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
import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.SolanaAddress
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Solana
import java.math.BigInteger

private fun x(): Triple<String, ByteArray, ByteArray> {
    val privateKey = PrivateKey()
    val publicKey = privateKey.publicKeyEd25519
    val nonceAccountAddress = PublicKey(
        publicKey.data(),
        PublicKeyType.ED25519
    ).description()
    val nonceAccountBytes = publicKey.data()
    val nonceAccountPrivateKey = privateKey.data()
    return Triple(nonceAccountAddress, nonceAccountBytes, nonceAccountPrivateKey)
}


class SolanaHelper(
    private val vaultHexPublicKey: String,
) {

    private val coinType = CoinType.SOLANA

    companion object {
        val DefaultFeeInLamports: BigInteger = 1000000.toBigInteger()
    }
    fun createNonceAccount(lamports: Long): Triple<String, PrivateKey, Solana.SigningOutput> {
        // Generate a new private key for the nonce account
        val privateKey = PrivateKey()

        // Get the public key from the private key
        val publicKey = privateKey.publicKeyEd25519

        // Convert the public key to a Solana address
        val nonceAccountAddress = PublicKey(publicKey.data(), PublicKeyType.ED25519).description()

        // Create the nonce account
        val createNonceAccount = Solana.CreateNonceAccount.newBuilder()
            .setNonceAccount(nonceAccountAddress)
            .setRent(lamports)
            .build()

        // Create the signing input
        val signingInput = Solana.SigningInput.newBuilder()
            .setCreateNonceAccount(createNonceAccount)
            .build()

        // Sign the transaction
        val signedTransaction = Solana.SigningOutput.parseFrom(signingInput.toByteArray())

        // Return the nonce account address, private key, and signed transaction
        return Triple(nonceAccountAddress, privateKey, signedTransaction)
    }
    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
            ?: error("Invalid blockChainSpecific")
        if (keysignPayload.coin.chain != Chain.Solana) {
            error("Chain is not Solana")
        }

        // Create new account/keypair
        val newAccount = Account()  //

        // Convert the private key to ByteString for the SDK
        val privateKeyByteString = ByteString.copyFrom(newAccount.third)

        val input = Solana.SigningInput.newBuilder()
            .setRecentBlockhash(solanaSpecific.recentBlockHash)
            .setSender(keysignPayload.coin.address)
            .setPriorityFeePrice(
                Solana.PriorityFeePrice.newBuilder()
                    .setPrice(solanaSpecific.priorityFee.toLong())
                    .build()
            )
            .setCreateNonceAccount(
                Solana.CreateNonceAccount.newBuilder()
                    .setNonceAccount(newAccount.first)
          //          .setNonceAccountBytes(ByteString.copyFrom(newAccount.second))
                    .setRent(890880L)
                    .setNonceAccountPrivateKey(privateKeyByteString)
                    .build()
            )
            .build()

        return input.toByteArray()
    }

    private fun getRentExemptAmount(): Long {
        // Implement logic to get minimum balance for rent exemption
        // This should be enough for 80 bytes (nonce account size)
        // You can either hardcode this or fetch from RPC
        return 890880L  // Example value, you should get the actual minimum rent exempt amount
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