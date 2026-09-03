package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.CardanoCIP20
import com.vultisig.wallet.data.crypto.CardanoUtils
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.parseCardanoAssetId
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.CardanoTokenAsset
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.Numeric.hexStringToByteArray
import java.math.BigInteger
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
     * Lovelace attached to the recipient output of a native-token send.
     *
     * Cardano requires every output to carry a minimum ADA value that scales with the output's CBOR
     * size; a single-asset output is typically ~0.85 ADA. The fixed 1.5 ADA leaves headroom and is
     * the value iOS and the SDK send, so the three platforms build byte-identical bodies. Deriving
     * it dynamically (`Cardano.outputMinAdaAmount`) would be tighter but would put a per-device
     * value into the signed body, which is exactly what breaks a mixed-platform ceremony.
     */
    const val MIN_LOVELACE_ON_TOKEN_OUTPUT = 1_500_000L

    /**
     * Assembles a [Cardano.SigningInput.Builder] from raw transaction parameters.
     *
     * [forceFee] pins the body fee: a positive value forces that exact fee (used when signing so
     * the MPC sighash matches the fee transmitted by the initiator); `0` leaves the fee unset so
     * WalletCore's planner derives it from the transaction size (used for fee estimation).
     *
     * [tokenBundle] is the native assets the recipient output carries, or `null` for an ADA send.
     */
    private fun buildSigningInput(
        toAmount: BigInteger,
        toAddress: String,
        changeAddress: String,
        sendMaxAmount: Boolean,
        ttl: Long,
        utxos: List<UtxoInfo>,
        forceFee: Long,
        memo: String?,
        tokenBundle: Cardano.TokenBundle?,
    ): Cardano.SigningInput.Builder {
        // `transferMessage.amount` is the recipient output's lovelace. On an ADA send that is the
        // typed amount; on a token send the typed amount counts the token's own base units, so
        // passing it here would size the output in lovelace and the ledger would reject it as
        // insufficiently funded. The token rides in [tokenBundle] and the output takes the floor.
        val recipientLovelace =
            if (tokenBundle == null) toAmount.toLong() else MIN_LOVELACE_ON_TOKEN_OUTPUT

        val transfer =
            Cardano.Transfer.newBuilder()
                .setAmount(recipientLovelace)
                .setToAddress(toAddress)
                // `useMaxAmount` drains every input lovelace into the recipient output. On a token
                // send "max" means all of the token while the lovelace stays pinned at the floor
                // above, so the flag must never be set there.
                .setUseMaxAmount(sendMaxAmount && tokenBundle == null)
                .setChangeAddress(changeAddress)
                .setForceFee(forceFee)
        if (tokenBundle != null) {
            transfer.setTokenAmount(tokenBundle)
        }

        val input = Cardano.SigningInput.newBuilder().setTransferMessage(transfer).setTtl(ttl)

        // CIP-20 memo (label 674). When present, WalletCore commits
        // blake2b-256(auxDataCbor) into the body at map key 7 and embeds the aux
        // CBOR as the transaction's auxiliary_data element. The encoder is
        // byte-parity pinned to the SDK golden vector so co-signers agree on the
        // sighash.
        cip20AuxData(memo)?.let { input.setAuxiliaryData(ByteString.copyFrom(it)) }

        // Add UTXOs to the input. Per-UTxO token data rides on the wire
        // (`UtxoInfo.cardanoTokens`), populated by the initiator before keysign, so every
        // co-signer reads the same inputs without its own Koios call. Declaring it also lets the
        // planner conserve the assets a spent input carries — omit it and the body either trips
        // `errorLowBalance` on a token send or silently drops assets on an ADA send.
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
                    .addAllTokenAmount(inputUtxo.cardanoTokens.map { it.toTokenAmount() })
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
            toAmount = keysignPayload.toAmount,
            toAddress = keysignPayload.toAddress,
            changeAddress = keysignPayload.coin.address,
            sendMaxAmount = sendMaxAmount,
            ttl = ttl.toLong(),
            utxos = keysignPayload.utxos,
            forceFee = byteFee,
            memo = keysignPayload.memo,
            tokenBundle = tokenBundle(keysignPayload.coin, keysignPayload.toAmount),
        )
    }

    /**
     * The recipient output's native-asset bundle for a Cardano token send, or `null` when [coin] is
     * ADA itself.
     *
     * The asset id is read from `Coin.contractAddress`, the `<policy_id>.<asset_name_hex>` form the
     * curated catalog and every discovery path store.
     */
    private fun tokenBundle(coin: Coin, amount: BigInteger): Cardano.TokenBundle? {
        if (coin.isNativeToken) return null

        val assetId =
            parseCardanoAssetId(coin.contractAddress)
                ?: error("Cardano token ${coin.ticker} has a malformed asset id")
        require(amount.signum() >= 0) { "Cardano token ${coin.ticker} amount is negative" }

        return Cardano.TokenBundle.newBuilder()
            .addToken(
                Cardano.TokenAmount.newBuilder()
                    .setPolicyId(assetId.policyId)
                    .setAssetNameHex(assetId.assetNameHex)
                    .setAmount(ByteString.copyFrom(CardanoUtils.tokenAmountBytes(amount)))
            )
            .build()
    }

    private fun CardanoTokenAsset.toTokenAmount(): Cardano.TokenAmount =
        Cardano.TokenAmount.newBuilder()
            .setPolicyId(policyId)
            .setAssetNameHex(assetNameHex)
            .setAmount(ByteString.copyFrom(CardanoUtils.tokenAmountBytes(amount)))
            .build()

    /**
     * Canonical CIP-20 auxiliary-data CBOR (label 674) for the payload [memo], or `null` when there
     * is no memo. Both the pre-sign input (`Cardano.SigningInput.auxiliary_data`) and the
     * WalletCore-compiled signed envelope derive from these bytes, so the body's key-7 hash and the
     * embedded aux stay consistent — and byte-identical to the iOS/Extension co-signers.
     */
    private fun cip20AuxData(memo: String?): ByteArray? {
        if (memo.isNullOrEmpty()) return null
        return CardanoCIP20.buildAuxData(memo).auxDataCbor
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

        val plan = plan(inputBuilder.build())

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
     *
     * [toAmount] is lovelace when [coin] is ADA and the token's own base units otherwise.
     */
    fun estimateFee(
        coin: Coin,
        toAmount: BigInteger,
        toAddress: String,
        sendMaxAmount: Boolean,
        ttl: Long,
        utxos: List<UtxoInfo>,
        memo: String?,
    ): Long {
        val signingInput =
            buildSigningInput(
                    toAmount = toAmount,
                    toAddress = toAddress,
                    changeAddress = coin.address,
                    sendMaxAmount = sendMaxAmount,
                    ttl = ttl,
                    utxos = utxos,
                    forceFee = 0,
                    memo = memo,
                    // The bundle changes the body's size and output count, so a token send has to
                    // be priced on the shape that will actually be signed.
                    tokenBundle = tokenBundle(coin, toAmount),
                )
                .build()
        return plan(signingInput).fee
    }

    /**
     * Runs WalletCore's Cardano planner and returns the resulting [TransactionPlan], throwing on a
     * planning error. Centralises the planner invocation and error handling shared by
     * [getPreSignedInputData] and [estimateFee].
     */
    private fun plan(input: Cardano.SigningInput): TransactionPlan {
        val plan = AnySigner.plan(input, CoinType.CARDANO, TransactionPlan.parser())
        if (plan.error != SigningError.OK) {
            Timber.e("Cardano Plan Error: %s", plan.error.name)
            error("Cardano transaction plan error: ${plan.error.name}")
        }
        return plan
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
        // WalletCore emits the legacy 3-element envelope; Conway-era nodes require the 4-element
        // form with an is_valid flag. Splicing it in doesn't touch the signed body (element 0).
        val encoded = CardanoUtils.addIsValidFlag(output.encoded.toByteArray())
        val transactionHash = CardanoUtils.calculateCardanoTransactionHash(encoded)
        return SignedTransactionResult(
            rawTransaction = encoded.toHexString(),
            transactionHash = transactionHash,
        )
    }
}
