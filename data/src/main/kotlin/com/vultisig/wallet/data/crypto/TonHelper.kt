@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.toUnit
import tss.KeysignResponse
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TONAddressConverter
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.TheOpenNetwork

object TonHelper {

    private fun getPreSignedInputData(payload: KeysignPayload): ByteArray {
        require(payload.coin.chain == Chain.Ton) { "Coin is not TON" }

        val (sequenceNumber, expireAt, bounceable, _, sendMaxAmount) =
            (payload.blockChainSpecific as? BlockChainSpecific.Ton)
                ?: throw RuntimeException("Fail to get Ton chain specific")

        val toAddress = AnyAddress(payload.toAddress, CoinType.TON)

        val publicKey =
            PublicKey(payload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.ED25519)

        val input = if (payload.coin.isNativeToken) {
            // If sending max amount, set amount to 0 (entire balance will be attached)
            val amount = if (sendMaxAmount) 0L else payload.toAmount.toLong()

            // Always include IGNORE_ACTION_PHASE_ERRORS_VALUE to prevent validators from retrying
            // until funds are depleted
            val mode = when {
                sendMaxAmount -> TheOpenNetwork.SendMode.ATTACH_ALL_CONTRACT_BALANCE.number
                else -> TheOpenNetwork.SendMode.PAY_FEES_SEPARATELY_VALUE
            } or TheOpenNetwork.SendMode.IGNORE_ACTION_PHASE_ERRORS_VALUE

            val transfer = TheOpenNetwork.Transfer.newBuilder()
                .setDest(toAddress.description())
                .setAmount(amount)
                .setMode(mode)
                .setBounceable(bounceable)
                .let {
                    if (payload.memo != null) {
                        it.setComment(payload.memo)
                    } else it
                }
                .build()

            TheOpenNetwork.SigningInput.newBuilder()
                .addMessages(transfer)
                .setSequenceNumber(sequenceNumber.toInt())
                .setExpireAt(expireAt.toInt())
                .setWalletVersion(TheOpenNetwork.WalletVersion.WALLET_V4_R2)
                .setPublicKey(ByteString.copyFrom(publicKey.data()))
                .build()
        } else {
            val destinationAddressBounceable =
                TONAddressConverter.toUserFriendly(payload.toAddress, true, false)

            val mode = TheOpenNetwork.SendMode.PAY_FEES_SEPARATELY_VALUE or
                    TheOpenNetwork.SendMode.IGNORE_ACTION_PHASE_ERRORS_VALUE

            val jettonsTransfer = TheOpenNetwork.JettonTransfer
                .newBuilder()
                .setJettonAmount(payload.toAmount.toLong())
                .setResponseAddress(payload.coin.address) // return remain TON to origin
                .setToOwner(destinationAddressBounceable)
                .setForwardAmount(1) // set 0 if destination wallet is inactive, 1 if active
                .build()

            val transfer = TheOpenNetwork.Transfer
                .newBuilder()
                .setAmount(RECOMMENDED_JETTONS_AMOUNT)
                .setComment(payload.memo.orEmpty())
                .setBounceable(true) // Jettons should always be bounceable
                .setMode(mode)
                .setDest("") // Origin Jettons address
                .setJettonTransfer(jettonsTransfer)
                .build()

            TheOpenNetwork.SigningInput
                .newBuilder()
                .addMessages(transfer)
                .setSequenceNumber(sequenceNumber.toInt())
                .setExpireAt(expireAt.toInt())
                .setWalletVersion(TheOpenNetwork.WalletVersion.WALLET_V4_R2)
                .setPublicKey(ByteString.copyFrom(publicKey.data()))
                .build()
        }

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
        PublicKey(pubKeyData, PublicKeyType.ED25519)
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

        val output = TheOpenNetwork.SigningOutput
            .parseFrom(compileWithSignature)

        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.hash.toByteArray().toHexString()
        )
    }
}

internal val RECOMMENDED_JETTONS_AMOUNT = CoinType.TON.toUnit("0.08".toBigDecimal()).toLong()