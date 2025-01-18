@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import tss.KeysignResponse
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.TheOpenNetwork

object TonHelper {

    private fun getPreSignedInputData(payload: KeysignPayload): ByteArray {
        require(payload.coin.chain == Chain.Ton) { "Coin is not TON" }

        val (sequenceNumber, expireAt, bounceable) = payload.blockChainSpecific as? BlockChainSpecific.Ton
            ?: throw RuntimeException("Fail to get Ton chain specific")

        val toAddress = AnyAddress(payload.toAddress, CoinType.TON)

        val publicKey =
            PublicKey(payload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.ED25519)

        val transfer = TheOpenNetwork.Transfer.newBuilder()
            .setDest(toAddress.description())
            .setAmount(payload.toAmount.toLong())
            .setMode(
                TheOpenNetwork.SendMode.PAY_FEES_SEPARATELY_VALUE
                        or TheOpenNetwork.SendMode.IGNORE_ACTION_PHASE_ERRORS_VALUE
            )
            .setBounceable(false) // TODO
            .let {
                if (payload.memo != null) {
                    it.setComment(payload.memo)
                } else it
            }
            .build()

        val input = TheOpenNetwork.SigningInput.newBuilder()
            .addMessages(transfer)
            .setSequenceNumber(sequenceNumber.toInt())
            .setExpireAt(expireAt.toInt())
            .setWalletVersion(TheOpenNetwork.WalletVersion.WALLET_V4_R2)
            .setPublicKey(ByteString.copyFrom(publicKey.data()))
            .build()

        return input.toByteArray()
    }

    fun getPreSignedImageHash(payload: KeysignPayload): List<String> {
        val inputData = getPreSignedInputData(payload)
        val hashes = TransactionCompiler.preImageHashes(CoinType.TON, inputData)
        val preSigningOutput = wallet.core.jni.proto.TransactionCompiler.PreSigningOutput
            .parseFrom(hashes)
            .checkError()

        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray()))
    }

    fun getSignedTransaction(
        vaultHexPublicKey: String,
        payload: KeysignPayload,
        signatures: Map<String, KeysignResponse>
    ): SignedTransactionResult {
        val pubKeyData = vaultHexPublicKey.hexToByteArray()
        val publicKey = PublicKey(pubKeyData, PublicKeyType.ED25519)
        val inputData = getPreSignedInputData(payload)
        val hashes = TransactionCompiler.preImageHashes(CoinType.TON, inputData)
        val preSigningOutput = wallet.core.jni.proto.TransactionCompiler.PreSigningOutput
            .parseFrom(hashes)
            .checkError()

        val allSignatures = DataVector()
        val publicKeys = DataVector()

        val signature = signatures[Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())]
            ?.getSignature()
            ?: throw Exception("Signature not found")

        allSignatures.add(signature)
        publicKeys.add(pubKeyData)

        val compileWithSignature = TransactionCompiler.compileWithSignatures(
            CoinType.TON,
            inputData,
            allSignatures,
            publicKeys
        )

        val output = wallet.core.jni.proto.TheOpenNetwork.SigningOutput
            .parseFrom(compileWithSignature)

        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.hash.toByteArray().toHexString()
        )
    }

}
