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
import vultisig.keysign.v1.TonMessage
import wallet.core.java.AnySigner
import wallet.core.jni.AnyAddress
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TONAddressConverter
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.TheOpenNetwork

object TonHelper {

    private fun getPreSignedInputData(payload: KeysignPayload): ByteArray {
        require(payload.coin.chain == Chain.Ton) { "Coin is not TON" }

        val tonSpecific =
            payload.blockChainSpecific as? BlockChainSpecific.Ton
                ?: throw RuntimeException("Failed to get TON chain specific data")

        val publicKey = PublicKey(payload.coin.hexPublicKey.hexToByteArray(), PublicKeyType.ED25519)

        val builder =
            TheOpenNetwork.SigningInput.newBuilder()
                .setSequenceNumber(tonSpecific.sequenceNumber.toInt())
                .setExpireAt(tonSpecific.expireAt.toInt())
                .setWalletVersion(TheOpenNetwork.WalletVersion.WALLET_V4_R2)
                .setPublicKey(ByteString.copyFrom(publicKey.data()))

        addTransfersTo(builder, payload, tonSpecific)

        return builder.build().toByteArray()
    }

    private fun addTransfersTo(
        builder: TheOpenNetwork.SigningInput.Builder,
        payload: KeysignPayload,
        tonSpecific: BlockChainSpecific.Ton,
    ) {
        payload.signTon?.let { signTon ->
            val messages = signTon.tonMessages.filterNotNull()
            require(messages.isNotEmpty()) { "SignTon must have at least one message" }
            require(messages.size <= MAX_TON_MESSAGES) {
                "SignTon supports at most $MAX_TON_MESSAGES messages, got ${messages.size}"
            }
            messages.forEach { msg ->
                val amount = msg.amount.toLongOrNull() ?: 0L
                require(amount > 0) { "TonMessage amount must be positive, got ${msg.amount}" }
                builder.addMessages(buildTonConnectTransfer(msg, tonSpecific))
            }
        }
            ?: run {
                val transfer =
                    if (payload.coin.isNativeToken) {
                        buildNativeTransfer(payload, tonSpecific)
                    } else {
                        buildJettonTransfer(payload, tonSpecific)
                    }
                builder.addMessages(transfer)
            }
    }

    private fun buildTonConnectTransfer(
        msg: TonMessage,
        tonSpecific: BlockChainSpecific.Ton,
    ): TheOpenNetwork.Transfer {
        val toAddress = AnyAddress(msg.to, CoinType.TON)
        val amount = msg.amount.toLongOrNull() ?: 0L
        val mode = calculateSendMode(sendMaxAmount = false)

        return TheOpenNetwork.Transfer.newBuilder()
            .setDest(toAddress.description())
            .setAmount(ByteString.copyFrom(amount.toHexString().toHexByteArray()))
            .setMode(mode)
            .setBounceable(tonSpecific.bounceable)
            .apply {
                msg.payload?.takeIf { it.isNotEmpty() }?.let { setCustomPayload(it) }
                msg.stateInit?.takeIf { it.isNotEmpty() }?.let { setStateInit(it) }
            }
            .build()
    }

    private fun buildNativeTransfer(
        payload: KeysignPayload,
        tonSpecific: BlockChainSpecific.Ton,
    ): TheOpenNetwork.Transfer {
        val toAddress = AnyAddress(payload.toAddress, CoinType.TON)
        val amount = if (tonSpecific.sendMaxAmount) 0L else payload.toAmount.toLong()

        val mode = calculateSendMode(tonSpecific.sendMaxAmount)

        return TheOpenNetwork.Transfer.newBuilder()
            .setDest(toAddress.description())
            .setAmount(ByteString.copyFrom(amount.toHexString().toHexByteArray()))
            .setMode(mode)
            .setBounceable(tonSpecific.bounceable)
            .apply { payload.memo?.let { setComment(it) } }
            .build()
    }

    private fun buildJettonTransfer(
        payload: KeysignPayload,
        tonSpecific: BlockChainSpecific.Ton,
    ): TheOpenNetwork.Transfer {
        // Convert destination to bounceable, as jettons addresses are always EQ
        val destinationAddress = TONAddressConverter.toUserFriendly(payload.toAddress, true, false)

        require(tonSpecific.jettonAddress.isNotEmpty()) { "Jetton address cannot be empty" }

        val forwardAmountMsg = if (tonSpecific.isActiveDestination) 1L else 0L

        val jettonTransfer =
            TheOpenNetwork.JettonTransfer.newBuilder()
                .setJettonAmount(
                    ByteString.copyFrom(payload.toAmount.toHexString().toHexByteArray())
                )
                .setResponseAddress(payload.coin.address)
                .setToOwner(destinationAddress)
                .setForwardAmount(
                    ByteString.copyFrom(forwardAmountMsg.toHexString().toHexByteArray())
                )
                .build()

        val mode =
            TheOpenNetwork.SendMode.PAY_FEES_SEPARATELY_VALUE or
                TheOpenNetwork.SendMode.IGNORE_ACTION_PHASE_ERRORS_VALUE

        return TheOpenNetwork.Transfer.newBuilder()
            .setAmount(
                ByteString.copyFrom(RECOMMENDED_JETTONS_AMOUNT.toHexString().toHexByteArray())
            )
            .setComment(payload.memo.orEmpty())
            .setBounceable(true) // Jettons always bounceable
            .setMode(mode)
            .setDest(tonSpecific.jettonAddress) // Will be set to origin Jetton address
            .setJettonTransfer(jettonTransfer)
            .build()
    }

    private fun calculateSendMode(sendMaxAmount: Boolean): Int {
        val baseMode =
            if (sendMaxAmount) {
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
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()

        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray()))
    }

    fun getZeroSignedTransaction(payload: KeysignPayload): String {
        val dummyPrivateKey = PrivateKey()
        val dummyPublicKey = dummyPrivateKey.getPublicKeyEd25519()

        require(payload.coin.chain == Chain.Ton) { "Coin is not TON" }

        val tonSpecific =
            payload.blockChainSpecific as? BlockChainSpecific.Ton
                ?: throw RuntimeException("Failed to get TON chain specific data")

        val builder =
            TheOpenNetwork.SigningInput.newBuilder()
                .setSequenceNumber(tonSpecific.sequenceNumber.toInt())
                .setExpireAt(tonSpecific.expireAt.toInt())
                .setWalletVersion(TheOpenNetwork.WalletVersion.WALLET_V4_R2)
                .setPublicKey(ByteString.copyFrom(dummyPublicKey.data()))
                .setPrivateKey(ByteString.copyFrom(dummyPrivateKey.data()))

        addTransfersTo(builder, payload, tonSpecific)

        val output =
            AnySigner.sign(builder.build(), CoinType.TON, TheOpenNetwork.SigningOutput.parser())

        return output.encoded
    }

    fun getSignedTransaction(
        vaultHexPublicKey: String,
        payload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val pubKeyData = vaultHexPublicKey.hexToByteArray()
        PublicKey(pubKeyData, PublicKeyType.ED25519)
        val inputData = getPreSignedInputData(payload)
        val hashes = TransactionCompiler.preImageHashes(CoinType.TON, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()

        val allSignatures = DataVector()
        val publicKeys = DataVector()

        val signature =
            signatures[Numeric.toHexStringNoPrefix(preSigningOutput.data.toByteArray())]
                ?.getSignature() ?: throw Exception("Signature not found")

        allSignatures.add(signature)
        publicKeys.add(pubKeyData)

        val compileWithSignature =
            TransactionCompiler.compileWithSignatures(
                CoinType.TON,
                inputData,
                allSignatures,
                publicKeys,
            )

        val output = TheOpenNetwork.SigningOutput.parseFrom(compileWithSignature)

        return SignedTransactionResult(
            rawTransaction = output.encoded,
            transactionHash = output.hash.toByteArray().toHexString(),
        )
    }

    val RECOMMENDED_JETTONS_AMOUNT = CoinType.TON.toUnit("0.08".toBigDecimal()).toLong()

    private const val MAX_TON_MESSAGES = 4
}
