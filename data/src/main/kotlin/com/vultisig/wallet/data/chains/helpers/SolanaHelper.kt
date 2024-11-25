package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.common.toByteString
import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import io.ktor.util.encodeBase64
//import org.p2p.solanaj.core.Account
//import org.p2p.solanaj.utils.ByteUtils
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
//import com.solana.programs.SystemProgram

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


    fun createNonceAccount3(lamports: Long): Triple<String, PrivateKey, Solana.SigningOutput> {
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

    fun createNonceAccount2(lamports: Long): Triple<String, PrivateKey, Solana.SigningOutput> {
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
        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)

        val senderAddress = SolanaAddress(keysignPayload.coin.address)

        // Get rent-exempt amount for nonce account (80 bytes)
        val rentExemptAmount = getRentExemptAmount() // You'll need to implement this function



        //create bytestring and set 80
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
                    .setNonceAccount("HUpmNETCjf7n7JUgyv24kwt7NyP9HvNXqs8wcyhwy3y4") // The multisig address to upgrade

                    //   .setNonceAccountBytes(ByteString.copyFrom("a74c34f40cde7afbe5492ac7fcc504520784d9021a85c896c2f0c9e60a5a868c".toHexByteArray()))//                    .setAuthority(keysignPayload.coin.address)    // The multisig will be the authority
//                    .setNonceAccountBytes(ByteString.copyFromUtf8("80"))

                    //                    .setAuthority(keysignPayload.coin.address)    // The multisig will be the authority
                    .setRent(rentExemptAmount)        // Rent exempt amount for 80 byte
                    // Don't set nonceAccountPrivateKey since it's a multisig
                    .build()

            )
            .build()


        return input.toByteArray()
    }

    private fun getPreSignedInputData1(keysignPayload: KeysignPayload): ByteArray {
        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
            ?: error("Invalid blockChainSpecific")
        if (keysignPayload.coin.chain != Chain.Solana) {
            error("Chain is not Solana")
        }

        // Create new account/keypair
//      val newAccount = Account()  //newAccount = {Account@32349} org.p2p.solanaj.core.Account@d6c5f3e
//        newAccount.secretKey
//        newAccount.publicKey.toBase58()
//        var base64Str =newAccount.secretKey.encodeBase64()
//        Timber.d("solana secret:${base64Str}")
        var  base64Str="MIVu6XA7tMp9r9nV27Lj/uAJ6+FGI7ia+oANWmQaYd6SszO0Yd2wj58NnA05RzvQIXMofUb6+ISiY2uHigJhZQ=="
        // convert base64 to byte array
        val decodedBytes = base64Str.toByteArray()
        val secretKey = decodedBytes
        var pubkey="Asf1e2Bityqd5hmTfiagSCBYCb39MoQu1x9zxhJRUCP6"
//        val address = (newAccount.publicKey.toBase58())


        // Convert the private key to ByteString for the SDK
        val privateKeyByteString = ByteString.copyFrom(secretKey)

        val input = Solana.SigningInput.newBuilder()
            .setRecentBlockhash(solanaSpecific.recentBlockHash)

            .setSender(keysignPayload.coin.address)
            .setCreateNonceAccount(
                Solana.CreateNonceAccount.newBuilder()
//                  .setNonceAccount(address)
                    //  .setNonceAccountBytes(pubkey.toByteString())
                    .setRent(890880L)
                    //.setNonceAccountPrivateKey(privateKeyByteString)
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