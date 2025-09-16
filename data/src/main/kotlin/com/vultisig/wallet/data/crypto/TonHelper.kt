@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.crypto

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.toHexString
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

        val tonSpecific = payload.blockChainSpecific as? BlockChainSpecific.Ton
            ?: throw RuntimeException("Failed to get TON chain specific data")

        val publicKey = PublicKey(
            payload.coin.hexPublicKey.hexToByteArray(),
            PublicKeyType.ED25519
        )

        val transfer = if (payload.coin.isNativeToken) {
            buildNativeTransfer(payload, tonSpecific)
        } else {
            buildJettonTransfer(payload, tonSpecific)
        }

        return TheOpenNetwork.SigningInput.newBuilder()
            .addMessages(transfer)
            .setSequenceNumber(tonSpecific.sequenceNumber.toInt())
            .setExpireAt(tonSpecific.expireAt.toInt())
            .setWalletVersion(TheOpenNetwork.WalletVersion.WALLET_V4_R2)
            .setPublicKey(ByteString.copyFrom(publicKey.data()))
            .build()
            .toByteArray()
    }

    private fun buildNativeTransfer(
        payload: KeysignPayload,
        tonSpecific: BlockChainSpecific.Ton
    ): TheOpenNetwork.Transfer {
        val toAddress = AnyAddress(payload.toAddress, CoinType.TON)
        val amount = if (tonSpecific.sendMaxAmount) 0L else payload.toAmount.toLong()

        val mode = calculateSendMode(tonSpecific.sendMaxAmount)

        return TheOpenNetwork.Transfer.newBuilder()
            .setDest(toAddress.description())
            .setAmount(ByteString.copyFrom(amount.toHexString().toHexByteArray()))
            .setMode(mode)
            .setBounceable(tonSpecific.bounceable)
            .apply {
                payload.memo?.let { setComment(it) }
            }
            .build()
    }

    private fun buildJettonTransfer(
        payload: KeysignPayload,
        tonSpecific: BlockChainSpecific.Ton,
    ): TheOpenNetwork.Transfer {
        // Convert destination to bounceable, as jettons addresses are always EQ
        val destinationAddress =
            TONAddressConverter.toUserFriendly(payload.toAddress, true, false)

        require(tonSpecific.jettonAddress.isNotEmpty()) {
            "Jetton address cannot be empty"
        }

        val forwardAmountMsg = if (tonSpecific.isActiveDestination) 1L else 0L

        val jettonTransfer = TheOpenNetwork.JettonTransfer.newBuilder()
            .setJettonAmount(ByteString.copyFrom(payload.toAmount.toHexString().toHexByteArray()))
            .setResponseAddress(payload.coin.address)
            .setToOwner(destinationAddress)
            .setForwardAmount(ByteString.copyFrom(forwardAmountMsg.toHexString().toHexByteArray()))
            .build()

        val mode = TheOpenNetwork.SendMode.PAY_FEES_SEPARATELY_VALUE or
                TheOpenNetwork.SendMode.IGNORE_ACTION_PHASE_ERRORS_VALUE

        return TheOpenNetwork.Transfer.newBuilder()
            .setAmount(ByteString.copyFrom(RECOMMENDED_JETTONS_AMOUNT.toHexString().toHexByteArray()))
            .setComment(payload.memo.orEmpty())
            .setBounceable(true) // Jettons always bounceable
            .setMode(mode)
            .setDest(tonSpecific.jettonAddress) // Will be set to origin Jetton address
            .setJettonTransfer(jettonTransfer)
            .build()
    }

    private fun calculateSendMode(sendMaxAmount: Boolean): Int {
        val baseMode = if (sendMaxAmount) {
            TheOpenNetwork.SendMode.ATTACH_ALL_CONTRACT_BALANCE.number
        } else {
            TheOpenNetwork.SendMode.PAY_FEES_SEPARATELY_VALUE
        }

        // Always include IGNORE_ACTION_PHASE_ERRORS to prevent retry loops
        return baseMode or TheOpenNetwork.SendMode.IGNORE_ACTION_PHASE_ERRORS_VALUE
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


    val RECOMMENDED_JETTONS_AMOUNT = CoinType.TON.toUnit("0.08".toBigDecimal()).toLong()
}