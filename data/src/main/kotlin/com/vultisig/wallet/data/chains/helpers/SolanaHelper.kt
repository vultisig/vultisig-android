package com.vultisig.wallet.data.chains.helpers

import androidx.compose.runtime.MutableState
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
import com.google.protobuf.ByteString
import kotlinx.coroutines.flow.MutableStateFlow
import okio.ByteString.Companion.decodeHex
import wallet.core.jni.Curve
import wallet.core.jni.PrivateKey
import wallet.core.jni.TransactionDecoder
import java.math.BigInteger
import java.util.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val PRIORITY_FEE_PRICE = 1000000L
private const val PRIORITY_FEE_LIMIT = 100000
private var image_hash = byteArrayOf()

class SolanaHelper(
    private val vaultHexPublicKey: String,
) {



    private val coinType = CoinType.SOLANA

    companion object {
        val DefaultFeeInLamports: BigInteger = 1000000.toBigInteger()
    }

    fun base64ToHex(base64: String): String {
        val bytes = Base64.getDecoder().decode(base64)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {

        // Generate a new private key
        val privateKey = PrivateKey()
        val publicKey = privateKey.publicKeyEd25519
        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
            ?: error("Invalid blockChainSpecific")
        if (keysignPayload.coin.chain != Chain.Solana) {
            error("Chain is not Solana")
        }
        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)

        val nonceAccount = Solana.CreateNonceAccount.newBuilder()
            .setNonceAccountPrivateKey(
                ByteString.copyFrom(
                    //nonce private key 017b9745062211b8ed330cf92ce0212a87c0ac68b16a9f02096198a77e3d424b
                    //nonce public key 99f61e674ef17b071d65b8b5f94b35957e18ebe40ffb78d2232179a28df5a417

                    "017b9745062211b8ed330cf92ce0212a87c0ac68b16a9f02096198a77e3d424b".decodeHex()
                        .toByteArray()
                )
            )
            .setRent(100000)
            .build()
        Solana.SigningInput.newBuilder()

        var input =Solana.SigningInput.newBuilder()
            .setRecentBlockhash(solanaSpecific.recentBlockHash)
            .setCreateNonceAccount(nonceAccount)
//            .setPrivateKey(
//                ByteString.copyFrom(
//                    "49aa18e396912a11d0736088db3fc72ea1070a6919e02ceaf5ebeb7b076eedc5"
//                        .decodeHex()
//                        .toByteArray()
//                )
//            )
            .setSender(keysignPayload.coin.address)

        val input1 = Solana.SigningInput.newBuilder()

            .setRecentBlockhash(solanaSpecific.recentBlockHash)
            .setSender(keysignPayload.coin.address)
            .setPriorityFeePrice(
                Solana.PriorityFeePrice.newBuilder()
                    .setPrice(PRIORITY_FEE_PRICE)
                    .build()
            )
            .setPriorityFeeLimit(
                Solana.PriorityFeeLimit.newBuilder()
                    .setLimit(PRIORITY_FEE_LIMIT)
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
//                .setTransferTransaction(transfer.build())
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
//                    .setTokenTransferTransaction(transfer.build())
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
//                    .setCreateAndTransferTokenTransaction(transferTokenMessage.build())
                    .build()
                    .toByteArray()
            }
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)

        image_hash=hashes

        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        Timber.d("solana error:${preSigningOutput.errorMessage}")
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            throw Exception(preSigningOutput.errorMessage)
        }
        image_hash= preSigningOutput.data.toByteArray()
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val input = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, input)
        val privateKey2 = PrivateKey(
            "017b9745062211b8ed330cf92ce0212a87c0ac68b16a9f02096198a77e3d424b".decodeHex()
                .toByteArray()
        )
        val signature2= privateKey2.sign(image_hash, Curve.ED25519)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            throw Exception(preSigningOutput.errorMessage)
        }
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val signature = signatures[key]?.getSignature() ?: throw Exception("Signature not found")
        val hexString = "99f61e674ef17b071d65b8b5f94b35957e18ebe40ffb78d2232179a28df5a417"
        val publicKey2= PublicKey(hexString.toHexByteArray(), PublicKeyType.ED25519)

//        if (!publicKey2.verify(signature, preSigningOutput.data.toByteArray())) {
//            throw Exception("Signature verification failed")
//        }
//        publicKeys.add(publicKey2.data())
        if (!publicKey.verify(signature, preSigningOutput.data.toByteArray())) {
            throw Exception("Signature verification failed")
        }
        allSignatures.add(signature)
        if (!publicKey2.verify(signature2, preSigningOutput.data.toByteArray())) {
            throw Exception("Signature verification failed")
        }
        allSignatures.add(signature2)
        publicKeys.add(publicKey.data())
        publicKeys.add(publicKey2.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature)
        if (!output.errorMessage.isNullOrEmpty()) {
            throw Exception(output.errorMessage)
        }
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