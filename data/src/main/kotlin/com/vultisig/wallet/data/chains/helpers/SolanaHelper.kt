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
import okio.ByteString.Companion.decodeHex
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.Curve
import wallet.core.jni.DataVector
import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.SolanaAddress
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Solana
import java.math.BigInteger
import kotlin.io.encoding.ExperimentalEncodingApi

private const val PRIORITY_FEE_PRICE = 1000000L
private const val PRIORITY_FEE_LIMIT = 100000
private var unSignImageHash = byteArrayOf()

class SolanaHelper(
    private val vaultHexPublicKey: String,
) {

    private val coinType = CoinType.SOLANA

    companion object {
        val DefaultFeeInLamports: BigInteger = 1000000.toBigInteger()
    }

    private fun CreateNonceAccount(keysignPayload: KeysignPayload): ByteArray {
        // Generate a new private key and derive the corresponding public key for  creating a nonce account
//        val privateKey = PrivateKey()
//        val publicKey = privateKey.publicKeyEd25519
        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
        val nonceAccount = Solana.CreateNonceAccount.newBuilder()
            .setNonceAccountPrivateKey(
                ByteString.copyFrom(
                    "NONCE_ACCOUNT_PRIVATE_KEY".decodeHex()
                        .toByteArray()
                )
            )
            .setRent(1500000)
            .build()
        var creatingNonceAccountInput = Solana.SigningInput.newBuilder()
            .setRecentBlockhash(solanaSpecific?.recentBlockHash)
            .setCreateNonceAccount(nonceAccount)
            .setSender(keysignPayload.coin.address).build()
            .toByteArray()
        return creatingNonceAccountInput
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        if("THE_ACCOUN_HAS_NOT_NONCE_ACCOUNT" == "") {
            CreateNonceAccount(keysignPayload)
        }
        val advanceNonceAccount = Solana.AdvanceNonceAccount.newBuilder()
            .setNonceAccount("NONCE_ACCOUNT_ACCOUNT_ADDRESS")
            .build()

        val solanaSpecific = keysignPayload.blockChainSpecific as? BlockChainSpecific.Solana
            ?: error("Invalid blockChainSpecific")
        if (keysignPayload.coin.chain != Chain.Solana) {
            error("Chain is not Solana")
        }
        val toAddress = AnyAddress(keysignPayload.toAddress, coinType)
        val hasNonceAccount = false
        val recentBlockHash = if (hasNonceAccount)
            solanaSpecific.recentBlockHash
        else
            "FETCH_FROM_getAccountInfo_RPC_QUERY_ON_NONCE_ACCOUNT"

        val input = Solana.SigningInput.newBuilder()
            .setRecentBlockhash(recentBlockHash)
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

            return if (hasNonceAccount) {
                input.setTransferTransaction(transfer.build())
                    .setAdvanceNonceAccount(advanceNonceAccount)
                    .build()
            } else {
                input.setTransferTransaction(transfer.build())
                    .build()
            }
                .toByteArray()
        } else {
            if (solanaSpecific.fromAddressPubKey != null && solanaSpecific.toAddressPubKey != null) {
                val transfer = Solana.TokenTransfer.newBuilder()
                    .setTokenMintAddress(keysignPayload.coin.contractAddress)
                    .setSenderTokenAddress(solanaSpecific.fromAddressPubKey)
                    .setRecipientTokenAddress(solanaSpecific.toAddressPubKey)
                    .setAmount(keysignPayload.toAmount.toLong())
                    .setDecimals(keysignPayload.coin.decimal)

                return if (hasNonceAccount) {
                    input.setTokenTransferTransaction(transfer.build())
                        .setAdvanceNonceAccount(advanceNonceAccount)
                        .build()
                } else {
                    input.setTokenTransferTransaction(transfer.build())
                        .build()
                }
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

                return if (hasNonceAccount) {
                    input.setCreateAndTransferTokenTransaction(transferTokenMessage.build())
                        .setAdvanceNonceAccount(advanceNonceAccount)
                        .build()
                } else {
                    input.setCreateAndTransferTokenTransaction(transferTokenMessage.build())
                        .build()
                }
                    .toByteArray()
            }
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val result = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, result)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            error(preSigningOutput.errorMessage)
        }
        unSignImageHash = preSigningOutput.data.toByteArray()
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray()))
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {
        val publicKey = PublicKey(vaultHexPublicKey.toHexByteArray(), PublicKeyType.ED25519)
        val input = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(coinType, input)

        val nonceAccountPrivateKey = PrivateKey(
            "HEX_NONCE_PRIVATE_KEY".decodeHex()
                .toByteArray()
        )
        val creatingNonceAccountTransaction = false

        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (!preSigningOutput.errorMessage.isNullOrEmpty()) {
            error(preSigningOutput.errorMessage)
        }
        val key = Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())
        val allSignatures = DataVector()
        val publicKeys = DataVector()
        val signature = signatures[key]?.getSignature() ?: throw Exception("Signature not found")

        val nonceAccountSignature = nonceAccountPrivateKey.sign(
            unSignImageHash,
            Curve.ED25519
        )

        val nonceAccoutPubicKey = PublicKey(
            "NONCE_ACCOUNT_PUBLIC_KEY".toHexByteArray(),
            PublicKeyType.ED25519
        )

        if (creatingNonceAccountTransaction) {
            publicKeys.add(nonceAccoutPubicKey.data())
        }
        if (!publicKey.verify(signature, preSigningOutput.data.toByteArray())) {
            throw Exception("Signature verification failed")
        }
        allSignatures.add(signature)
        if (creatingNonceAccountTransaction) {
            if (!nonceAccoutPubicKey.verify(
                    nonceAccountSignature,
                    preSigningOutput.data.toByteArray()
                )
            ) {
                throw Exception("Nonce account signature verification failed")
            }
        }
        if (creatingNonceAccountTransaction)
            allSignatures.add(nonceAccountSignature)
        publicKeys.add(publicKey.data())
        if (creatingNonceAccountTransaction)
            publicKeys.add(nonceAccoutPubicKey.data())
        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(coinType, input, allSignatures, publicKeys)
        val output = Solana.SigningOutput.parseFrom(compiledWithSignature)
        if (!output.errorMessage.isNullOrEmpty()) {
            error(preSigningOutput.errorMessage)
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
        if (!output.errorMessage.isNullOrEmpty()) {
            error(output.errorMessage)
        }
        return output.encoded
    }
}