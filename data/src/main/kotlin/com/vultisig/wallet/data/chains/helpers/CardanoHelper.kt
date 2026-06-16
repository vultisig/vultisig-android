package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.CardanoUtils
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.Numeric.hexStringToByteArray
import timber.log.Timber
import wallet.core.java.AnySigner
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Cardano
import wallet.core.jni.proto.Cardano.TransactionPlan
import wallet.core.jni.proto.Common.SigningError

@OptIn(ExperimentalStdlibApi::class)
object CardanoHelper {

    /**
     * Assembles a [Cardano.SigningInput.Builder] from raw transaction parameters.
     *
     * [forceFee] pins the body fee: a positive value forces that exact fee (used when signing so
     * the MPC sighash matches the fee transmitted by the initiator); `0` leaves the fee unset so
     * WalletCore's planner derives it from the transaction size (used for fee estimation).
     */
    private fun buildSigningInput(
        toAmount: Long,
        toAddress: String,
        changeAddress: String,
        sendMaxAmount: Boolean,
        ttl: Long,
        utxos: List<UtxoInfo>,
        forceFee: Long,
    ): Cardano.SigningInput.Builder {
        val input =
            Cardano.SigningInput.newBuilder()
                .setTransferMessage(
                    Cardano.Transfer.newBuilder()
                        .setAmount(toAmount)
                        .setToAddress(toAddress)
                        .setUseMaxAmount(sendMaxAmount)
                        .setChangeAddress(changeAddress)
                        .setForceFee(forceFee)
                )
                // TODO: Implement memo support when WalletCore adds Cardano metadata support
                .setTtl(ttl)

        // Add UTXOs to the input
        for (inputUtxo in utxos) {
            val utxo =
                Cardano.TxInput.newBuilder()
                    .setOutPoint(
                        Cardano.OutPoint.newBuilder()
                            .setTxHash(ByteString.copyFrom(hexStringToByteArray(inputUtxo.hash)))
                            .setOutputIndex(inputUtxo.index.toLong())
                            .build()
                    )
                    .setAmount(inputUtxo.amount.toLong())
                    .setAddress(changeAddress)
                    .build()
            input.addUtxos(utxo)
        }

        return input
    }

    /**
     * Assembles the [Cardano.SigningInput.Builder] from [keysignPayload], forcing the body fee to
     * the transmitted [BlockChainSpecific.Cardano.byteFee] so every co-signer produces an identical
     * sighash regardless of WalletCore version.
     */
    private fun buildSigningInputBuilder(
        keysignPayload: KeysignPayload
    ): Cardano.SigningInput.Builder {
        require(keysignPayload.coin.chain == Chain.Cardano) { "Coin is not ada" }

        val (byteFee, sendMaxAmount, ttl) =
            keysignPayload.blockChainSpecific as? BlockChainSpecific.Cardano
                ?: error("fail to get Cardano chain specific parameters")

        return buildSigningInput(
            toAmount = keysignPayload.toAmount.toLong(),
            toAddress = keysignPayload.toAddress,
            changeAddress = keysignPayload.coin.address,
            sendMaxAmount = sendMaxAmount,
            ttl = ttl.toLong(),
            utxos = keysignPayload.utxos,
            forceFee = byteFee,
        )
    }

    /**
     * Returns serialized [Cardano.SigningInput] bytes with the body fee forced to the transmitted
     * `byteFee` carried on the payload.
     *
     * Mirrors the iOS/SDK signing path: even though [buildSigningInputBuilder] already seeds
     * `forceFee = byteFee`, we still run WalletCore's planner here (which honors the seeded
     * `forceFee`, so `plan.fee == byteFee`) and pin both the resulting `plan` and `plan.fee` into
     * the input. Embedding the plan makes the pre-image-hash phase and the compile phase consume
     * byte-identical bytes, and signing from `plan.fee` is exactly what an iOS join device does —
     * so every co-signer reproduces the same Blake2b sighash regardless of platform.
     */
    fun getPreSignedInputData(keysignPayload: KeysignPayload): ByteArray {
        val inputBuilder = buildSigningInputBuilder(keysignPayload)

        val plan = AnySigner.plan(inputBuilder.build(), CoinType.CARDANO, TransactionPlan.parser())
        if (plan.error != SigningError.OK) {
            Timber.e("Cardano Plan Error: %s", plan.error.name)
            error("Cardano transaction plan error: ${plan.error.name}")
        }

        return inputBuilder
            .setTransferMessage(inputBuilder.transferMessage.toBuilder().setForceFee(plan.fee))
            .setPlan(plan)
            .build()
            .toByteArray()
    }

    /**
     * Derives the size-based Cardano fee for a prospective transaction by running WalletCore's
     * planner with no forced fee. Used by the initiator to seed `byteFee` before signing; the
     * derived value is then transmitted and forced on every device.
     */
    fun estimateFee(
        toAmount: Long,
        toAddress: String,
        changeAddress: String,
        sendMaxAmount: Boolean,
        ttl: Long,
        utxos: List<UtxoInfo>,
    ): Long {
        val signingInput =
            buildSigningInput(
                    toAmount = toAmount,
                    toAddress = toAddress,
                    changeAddress = changeAddress,
                    sendMaxAmount = sendMaxAmount,
                    ttl = ttl,
                    utxos = utxos,
                    forceFee = 0,
                )
                .build()
        val plan = AnySigner.plan(signingInput, CoinType.CARDANO, TransactionPlan.parser())
        if (plan.error == SigningError.OK) {
            return plan.fee
        }

        Timber.e("Cardano Plan Error: %s", plan.error.name)
        throw RuntimeException("Signing Error During Plan calculation")
    }

    /**
     * Returns the Blake2b-256 pre-image hash for the given [keysignPayload], used in TSS signing.
     */
    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(CoinType.CARDANO, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
        if (preSigningOutput.errorMessage.isNotEmpty()) {
            val errorMessage = preSigningOutput.errorMessage
            Timber.e("$errorMessage")
            error(errorMessage)
        }
        return listOf(Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray()))
    }

    /** Compiles and returns the signed Cardano transaction from TSS [signatures]. */
    fun getSignedTransaction(
        vaultHexPublicKey: String,
        vaultHexChainCode: String,
        keysignPayload: KeysignPayload,
        signatures: Map<String, tss.KeysignResponse>,
    ): SignedTransactionResult {

        val extendedKeyData =
            CardanoUtils.createExtendedKey(
                spendingKeyHex = vaultHexPublicKey,
                chainCodeHex = vaultHexChainCode,
            )
        val spendingKeyData = vaultHexPublicKey.hexToByteArray()
        val verificationKey = PublicKey(spendingKeyData, PublicKeyType.ED25519)
        val inputData = getPreSignedInputData(keysignPayload)
        val hashes = TransactionCompiler.preImageHashes(CoinType.CARDANO, inputData)
        val preSigningOutput =
            wallet.core.jni.proto.TransactionCompiler.PreSigningOutput.parseFrom(hashes)
                .checkError()
        val allSignatures = DataVector()
        val publicKeys = DataVector()

        val key = Numeric.toHexStringNoPrefix(preSigningOutput.dataHash.toByteArray())

        val signature = signatures[key]?.getSignature() ?: error("Signature not found")

        if (!verificationKey.verify(signature, preSigningOutput.dataHash.toByteArray())) {
            error("Cardano signature verification failed")
        }

        allSignatures.add(signature)
        publicKeys.add(extendedKeyData)

        val compileWithSignature =
            TransactionCompiler.compileWithSignatures(
                CoinType.CARDANO,
                inputData,
                allSignatures,
                publicKeys,
            )

        val output = Cardano.SigningOutput.parseFrom(compileWithSignature).checkError()
        var transactionHash =
            CardanoUtils.calculateCardanoTransactionHash(output.encoded.toByteArray())
        return SignedTransactionResult(
            rawTransaction = output.encoded.toByteArray().toHexString(),
            transactionHash = transactionHash,
        )
    }
}
