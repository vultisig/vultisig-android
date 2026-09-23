package com.vultisig.wallet.data.crypto

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.blockchain.ton.TonNominatorPool
import com.vultisig.wallet.data.common.toHexByteArray
import com.vultisig.wallet.data.crypto.ton.TonAddressFlags
import com.vultisig.wallet.data.crypto.ton.TonBounceability
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

    fun getPreSignedInputData(payload: KeysignPayload): ByteArray {
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
                builder.addMessages(buildTonConnectTransfer(msg))
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

    private fun buildTonConnectTransfer(msg: TonMessage): TheOpenNetwork.Transfer {
        val toAddress = AnyAddress(msg.to, CoinType.TON)
        val amount = msg.amount.toLongOrNull() ?: 0L
        return TheOpenNetwork.Transfer.newBuilder()
            .setDest(toAddress.description())
            .setAmount(ByteString.copyFrom(amount.toHexString().toHexByteArray()))
            .setMode(SEND_MODE)
            .setBounceable(msg.isBounceable())
            .apply {
                msg.payload?.takeIf { it.isNotEmpty() }?.let { setCustomPayload(it) }
                msg.stateInit?.takeIf { it.isNotEmpty() }?.let { setStateInit(it) }
            }
            .build()
    }

    /**
     * The bounce flag a dApp message declares through its own destination, never the wallet-level
     * `tonSpecific.bounceable`. The flag is part of the signed body, so this must match the
     * initiator (SDK `getTonMessageBounceable`): `EQ` bounceable, `UQ` not, and a raw `wc:hex`
     * address — the only form `AnyAddress` accepts without a tag — bounceable unless the message
     * deploys the destination via `stateInit`.
     */
    private fun TonMessage.isBounceable(): Boolean =
        when (TonAddressFlags.bounceabilityOf(to)) {
            TonBounceability.BOUNCEABLE -> true
            TonBounceability.NON_BOUNCEABLE -> false
            TonBounceability.UNSPECIFIED -> stateInit.isNullOrEmpty()
        }

    private fun buildNativeTransfer(
        payload: KeysignPayload,
        tonSpecific: BlockChainSpecific.Ton,
    ): TheOpenNetwork.Transfer {
        val toAddress = AnyAddress(payload.toAddress, CoinType.TON)
        // Sign the explicit amount even for MAX, never an ATTACH_ALL_CONTRACT_BALANCE sweep: the
        // displayed MAX (balance - fee) must equal what is signed, and that reserved fee doubles as
        // the account's storage reserve. Matches iOS Ton.buildTransfers.
        val amount = payload.toAmount.toLong()

        // Nominator-pool deposits/withdrawals MUST be sent bounceable so a message the pool
        // rejects (e.g. an uninitialized or mis-funded pool) bounces back instead of being
        // absorbed (lost). tonSpecific.bounceable resolves to false for an uninitialized
        // destination, so force the flag for pool comments — matching the initiating device
        // which does the same — otherwise the pre-image hash diverges and the joiner 404s.
        val bounceable = tonSpecific.bounceable || TonNominatorPool.isTransferComment(payload.memo)

        return TheOpenNetwork.Transfer.newBuilder()
            .setDest(toAddress.description())
            .setAmount(ByteString.copyFrom(amount.toHexString().toHexByteArray()))
            .setMode(SEND_MODE)
            .setBounceable(bounceable)
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

        return TheOpenNetwork.Transfer.newBuilder()
            .setAmount(
                ByteString.copyFrom(RECOMMENDED_JETTONS_AMOUNT.toHexString().toHexByteArray())
            )
            .setComment(payload.memo.orEmpty())
            .setBounceable(true) // Jettons always bounceable
            .setMode(SEND_MODE)
            .setDest(tonSpecific.jettonAddress) // Will be set to origin Jetton address
            .setJettonTransfer(jettonTransfer)
            .build()
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
        val publicKey = PublicKey(pubKeyData, PublicKeyType.ED25519)
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

        // Verify against the vault key before broadcast, matching every sibling EdDSA helper. The
        // signature can arrive from the relay (DKLS-family recovery), so gate it cryptographically
        // rather than trusting the source.
        if (!publicKey.verify(signature, preSigningOutput.data.toByteArray())) {
            throw Exception("Invalid signature")
        }

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

    /**
     * Every app-built transfer - native, jetton and TonConnect alike - pays fees separately and
     * nothing more. `IGNORE_ACTION_PHASE_ERRORS` is deliberately absent: with it set, a transfer
     * the wallet contract cannot pay for is skipped while the transaction still lands un-aborted,
     * so the seqno is consumed and no funds move. The mode is part of the signed message body, so
     * every co-signer must derive the same one.
     */
    private const val SEND_MODE = TheOpenNetwork.SendMode.PAY_FEES_SEPARATELY_VALUE

    private const val MAX_TON_MESSAGES = 4
}
