package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.getDustThreshold
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import timber.log.Timber
import tss.KeysignResponse
import vultisig.keysign.v1.BitcoinInput
import vultisig.keysign.v1.BitcoinOutput
import vultisig.keysign.v1.SignBitcoin
import wallet.core.java.AnySigner
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.DataVector
import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

class UtxoHelper(
    val coinType: CoinType,
    val vaultHexPublicKey: String,
    val vaultHexChainCode: String,
) {
    companion object {
        fun getHelper(vault: Vault, coinType: CoinType): UtxoHelper {
            when (coinType) {
                CoinType.BITCOIN,
                CoinType.BITCOINCASH,
                CoinType.LITECOIN,
                CoinType.DOGECOIN,
                CoinType.DASH,
                CoinType.ZCASH -> {
                    return UtxoHelper(
                        coinType = coinType,
                        vaultHexPublicKey = vault.pubKeyECDSA,
                        vaultHexChainCode = vault.hexChainCode,
                    )
                }

                else -> throw Exception("Unsupported chain")
            }
        }
    }

    fun getPreSignedImageHash(keysignPayload: KeysignPayload): List<String> {
        val inputData = getBitcoinPreSigningInputData(keysignPayload)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput = Bitcoin.PreSigningOutput.parseFrom(preHashes).checkError()
        return preSigningOutput.hashPublicKeysList
            .map { Numeric.toHexStringNoPrefix(it.dataHash.toByteArray()) }
            .sorted()
    }

    fun getSwapPreSigningInputData(keysignPayload: KeysignPayload): Bitcoin.SigningInput {
        val thorChainSwapPayload =
            keysignPayload.swapPayload as? SwapPayload.ThorChain
                ?: throw Exception("Invalid swap payload for THORChain")
        require(!keysignPayload.memo.isNullOrEmpty()) { "Memo is required for THORChain swap" }
        require(thorChainSwapPayload.data.vaultAddress.isNotEmpty()) {
            "Vault address is required for THORChain swap"
        }
        val input =
            Bitcoin.SigningInput.newBuilder()
                .setHashType(BitcoinScript.hashTypeForCoin(coinType))
                .setAmount(thorChainSwapPayload.data.fromAmount.toLong())
                .setToAddress(thorChainSwapPayload.data.vaultAddress)
                .setChangeAddress(keysignPayload.coin.address)
                .setByteFee(1L)
                .setCoinType(coinType.value())
                .setUseMaxAmount(false)
                .setOutputOpReturn( // output index is the latest by default
                    ByteString.copyFromUtf8(keysignPayload.memo)
                )
        return input.build()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getSigningInputData(
        keysignPayload: KeysignPayload,
        signingInput: Bitcoin.SigningInput.Builder,
    ): ByteArray {
        val utxo = keysignPayload.blockChainSpecific as BlockChainSpecific.UTXO
        signingInput
            .setHashType(BitcoinScript.hashTypeForCoin(coinType))
            .setUseMaxAmount(utxo.sendMaxAmount)
            .setByteFee(utxo.byteFee.toLong())
            .setFixedDustThreshold(coinType.getDustThreshold)
        for (item in keysignPayload.utxos) {
            val lockScript =
                BitcoinScript.lockScriptForAddress(keysignPayload.coin.address, coinType)
            val output =
                Bitcoin.OutPoint.newBuilder()
                    .setHash(
                        ByteString.copyFrom(Numeric.hexStringToByteArray(item.hash).reversedArray())
                    )
                    .setIndex(item.index.toInt())
                    .setSequence(Long.MAX_VALUE.toInt())
                    .build()
            val utxoItem =
                Bitcoin.UnspentTransaction.newBuilder()
                    .setAmount(item.amount)
                    .setOutPoint(output)
                    .setScript(ByteString.copyFrom(lockScript.data()))

            when (coinType) {
                CoinType.BITCOIN,
                CoinType.LITECOIN -> {
                    val keyHash = lockScript.matchPayToWitnessPublicKeyHash()
                    val redeemScript = BitcoinScript.buildPayToWitnessPubkeyHash(keyHash)
                    signingInput.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data()),
                    )
                }

                CoinType.DOGECOIN,
                CoinType.BITCOINCASH,
                CoinType.DASH,
                CoinType.ZCASH -> {
                    val keyHash = lockScript.matchPayToPubkeyHash()
                    val redeemScript = BitcoinScript.buildPayToPublicKeyHash(keyHash)
                    signingInput.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data()),
                    )
                }

                else -> throw Exception("Unsupported coin")
            }
            signingInput.addUtxo(utxoItem.build())
        }

        val plan: Bitcoin.TransactionPlan =
            AnySigner.plan(signingInput.build(), coinType, Bitcoin.TransactionPlan.parser())
        signingInput.setPlan(plan)
        return signingInput.build().toByteArray()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getBitcoinSigningInput(keysignPayload: KeysignPayload): Bitcoin.SigningInput.Builder {
        val utxo = keysignPayload.blockChainSpecific as BlockChainSpecific.UTXO
        val input =
            Bitcoin.SigningInput.newBuilder()
                .setHashType(BitcoinScript.hashTypeForCoin(coinType))
                .setAmount(keysignPayload.toAmount.toLong())
                .setUseMaxAmount(utxo.sendMaxAmount)
                .setToAddress(keysignPayload.toAddress)
                .setChangeAddress(keysignPayload.coin.address)
                .setByteFee(utxo.byteFee.toLong())
                .setCoinType(coinType.value())
                .setFixedDustThreshold(coinType.getDustThreshold)
        keysignPayload.memo?.let { input.setOutputOpReturn(ByteString.copyFromUtf8(it)) }

        for (item in keysignPayload.utxos) {
            val lockScript =
                BitcoinScript.lockScriptForAddress(keysignPayload.coin.address, coinType)
            val output =
                Bitcoin.OutPoint.newBuilder()
                    .setHash(
                        ByteString.copyFrom(Numeric.hexStringToByteArray(item.hash).reversedArray())
                    )
                    .setIndex(item.index.toInt())
                    .setSequence(Long.MAX_VALUE.toInt())
                    .build()
            val utxoItem =
                Bitcoin.UnspentTransaction.newBuilder()
                    .setAmount(item.amount)
                    .setOutPoint(output)
                    .setScript(ByteString.copyFrom(lockScript.data()))

            when (coinType) {
                CoinType.BITCOIN,
                CoinType.LITECOIN -> {
                    val keyHash = lockScript.matchPayToWitnessPublicKeyHash()
                    val redeemScript = BitcoinScript.buildPayToWitnessPubkeyHash(keyHash)
                    input.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data()),
                    )
                }

                CoinType.DOGECOIN,
                CoinType.BITCOINCASH,
                CoinType.DASH,
                CoinType.ZCASH -> {
                    val keyHash = lockScript.matchPayToPubkeyHash()
                    val redeemScript = BitcoinScript.buildPayToPublicKeyHash(keyHash)
                    input.putScripts(
                        keyHash.toHexString(),
                        ByteString.copyFrom(redeemScript.data()),
                    )
                }

                else -> throw Exception("Unsupported coin")
            }
            input.addUtxo(utxoItem.build())
        }
        return input
    }

    private fun getBitcoinPreSigningInputData(keysignPayload: KeysignPayload): ByteArray {
        val signingInput = getBitcoinSigningInput(keysignPayload)
        val initialPlan: Bitcoin.TransactionPlan =
            AnySigner.plan(signingInput.build(), coinType, Bitcoin.TransactionPlan.parser())

        val plan =
            if (coinType == CoinType.ZCASH) {
                initialPlan.toBuilder().setBranchId(ByteString.fromHex("f04dec4d")).build()
            } else initialPlan

        signingInput.setPlan(plan)

        return signingInput.build().toByteArray()
    }

    fun getSignedTransaction(
        keysignPayload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = getBitcoinPreSigningInputData(keysignPayload)
        return getSignedTransaction(inputData, signatures)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getSignedTransaction(
        inputData: ByteArray,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val derivedPublicKey =
            PublicKeyHelper.getDerivedPublicKey(
                vaultHexPublicKey,
                vaultHexChainCode,
                coinType.derivationPath(),
            )
        val publicKey = PublicKey(derivedPublicKey.hexToByteArray(), PublicKeyType.SECP256K1)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput = Bitcoin.PreSigningOutput.parseFrom(preHashes).checkError()
        val publicKeys = DataVector()
        val allSignatures = DataVector()
        for (item in preSigningOutput.hashPublicKeysList) {
            val preImageHash = item.dataHash
            val key = Numeric.toHexStringNoPrefix(preImageHash.toByteArray())
            signatures[key]?.let {
                if (
                    !publicKey.verifyAsDER(
                        it.derSignature.hexToByteArray(),
                        preImageHash.toByteArray(),
                    )
                ) {
                    Timber.d("Invalid signature")
                    throw Exception("Invalid signature")
                }
                allSignatures.add(it.derSignature.hexToByteArray())
                publicKeys.add(publicKey.data())
            }
        }

        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(
                coinType,
                inputData,
                allSignatures,
                publicKeys,
            )
        val output = Bitcoin.SigningOutput.parseFrom(compiledWithSignature).checkError()

        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(output.encoded.toByteArray()),
            transactionHash = output.transactionId,
        )
    }

    fun getZeroSignedTransaction(keysignPayload: KeysignPayload): String {
        val inputData = getBitcoinPreSigningInputData(keysignPayload)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput = Bitcoin.PreSigningOutput.parseFrom(preHashes).checkError()
        val publicKeys = DataVector()
        val allSignatures = DataVector()

        // Create a dummy private key for generating valid DER signatures
        val privateKey = PrivateKey()
        val publicKey = privateKey.getPublicKeySecp256k1(true)

        for (item in preSigningOutput.hashPublicKeysList) {
            val derSignature = privateKey.signAsDER(item.dataHash.toByteArray())
            allSignatures.add(derSignature)
            publicKeys.add(publicKey.data())
        }

        val compiledWithSignature =
            TransactionCompiler.compileWithSignatures(
                coinType,
                inputData,
                allSignatures,
                publicKeys,
            )
        val output = Bitcoin.SigningOutput.parseFrom(compiledWithSignature).checkError()

        return Numeric.toHexStringNoPrefix(output.encoded.toByteArray())
    }

    fun getBitcoinTransactionPlan(keysignPayload: KeysignPayload): Bitcoin.TransactionPlan {
        keysignPayload.signBitcoin?.let {
            return getBitcoinTransactionPlanFromSignBitcoin(it)
        }
        val signingInput = getBitcoinSigningInput(keysignPayload)
        val plan: Bitcoin.TransactionPlan =
            AnySigner.plan(signingInput.build(), coinType, Bitcoin.TransactionPlan.parser())
        return plan
    }

    /**
     * Builds a synthetic [Bitcoin.TransactionPlan] from a structured PSBT payload. PSBT co-signing
     * bypasses WalletCore tx planning (UTXOs live in `signBitcoin.inputs`, not
     * `keysignPayload.utxos`), so callers that previously relied on `getBitcoinTransactionPlan` for
     * fee/amount would otherwise hit `Error_missing_input_utxos` with a zero fee. The fee is
     * derived as `sum(inputs.amount) - sum(non_change_outputs + change_outputs)`.
     */
    fun getBitcoinTransactionPlanFromSignBitcoin(
        signBitcoin: SignBitcoin
    ): Bitcoin.TransactionPlan {
        val inputs = signBitcoin.inputs.filterNotNull()
        val outputs = signBitcoin.outputs.filterNotNull()
        val available = inputs.sumOf { it.amount }
        val change = outputs.filter { it.isChange }.sumOf { it.amount }
        val sent = outputs.filterNot { it.isChange }.sumOf { it.amount }
        val fee = available - (sent + change)
        return Bitcoin.TransactionPlan.newBuilder()
            .setAmount(sent)
            .setAvailableAmount(available)
            .setFee(fee)
            .setChange(change)
            .setError(SigningError.OK)
            .build()
    }

    /**
     * Returns the sorted hex-encoded BIP-143 sighashes for every input owned by this device in the
     * structured PSBT payload, ready to be dispatched to the MPC engine. Bypasses WalletCore tx
     * planning entirely. Only P2WPKH and P2SH-P2WPKH inputs are supported; P2TR is rejected.
     */
    fun getPreSignedImageHashFromSignBitcoin(signBitcoin: SignBitcoin): List<String> =
        computeOurSighashes(signBitcoin).map { Numeric.toHexStringNoPrefix(it) }.sorted()

    /**
     * Compiles the raw signed segwit transaction from a structured `SignBitcoin` payload and the
     * MPC signatures keyed by sighash hex. Witnesses are populated only for inputs marked `is_ours
     * = true`; other inputs receive an empty witness stack for downstream finalization.
     */
    fun getSignedTransactionFromSignBitcoin(
        signBitcoin: SignBitcoin,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val derivedPublicKey =
            PublicKeyHelper.getDerivedPublicKey(
                vaultHexPublicKey,
                vaultHexChainCode,
                coinType.derivationPath(),
            )
        val publicKey =
            PublicKey(Numeric.hexStringToByteArray(derivedPublicKey), PublicKeyType.SECP256K1)
        val pubKeyBytes = publicKey.data()

        val inputs = signBitcoin.inputs.filterNotNull()
        val outputs = signBitcoin.outputs.filterNotNull()
        val sighashes = computeOurSighashes(signBitcoin)
        val sighashByOursIdx = mutableListOf<Pair<Int, ByteArray>>()
        var s = 0
        inputs.forEachIndexed { idx, input ->
            if (input.isOurs) {
                sighashByOursIdx += idx to sighashes[s++]
            }
        }

        val witnessByIndex = mutableMapOf<Int, Pair<ByteArray, ByteArray>>()
        sighashByOursIdx.forEach { (idx, sighash) ->
            val input = inputs[idx]
            val key = Numeric.toHexStringNoPrefix(sighash)
            val sig = signatures[key] ?: error("Missing MPC signature for input $idx sighash $key")
            val derSig = Numeric.hexStringToByteArray(sig.derSignature)
            if (!publicKey.verifyAsDER(derSig, sighash)) {
                Timber.d("Invalid signature for PSBT input %d", idx)
                error("Invalid signature for PSBT input $idx")
            }
            val sighashFlag = (input.sighashType ?: SIGHASH_ALL.toUInt()).toInt() and 0xFF
            witnessByIndex[idx] = (derSig + byteArrayOf(sighashFlag.toByte())) to pubKeyBytes
        }

        val segwitTx = serializeSegwitTransaction(inputs, outputs, signBitcoin, witnessByIndex)
        val legacyTx = serializeLegacyTransaction(inputs, outputs, signBitcoin)
        val txid = sha256d(legacyTx).reversedArray()

        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(segwitTx),
            transactionHash = Numeric.toHexStringNoPrefix(txid),
        )
    }

    private fun computeOurSighashes(signBitcoin: SignBitcoin): List<ByteArray> {
        val inputs = signBitcoin.inputs.filterNotNull()
        val outputs = signBitcoin.outputs.filterNotNull()
        val hashPrevouts = sha256d(serializePrevouts(inputs))
        val hashSequence = sha256d(serializeSequences(inputs))
        val hashOutputs = sha256d(serializeOutputs(outputs))
        val version = signBitcoin.version.toInt()
        val locktime = signBitcoin.locktime.toInt()
        return inputs.mapNotNull { input ->
            if (!input.isOurs) return@mapNotNull null
            val pubKeyHash = extractWitnessPubKeyHash(input)
            val scriptCode = buildP2WPKHScriptCode(pubKeyHash)
            val sighashFlag = (input.sighashType ?: SIGHASH_ALL.toUInt()).toInt()
            val sequence = (input.sequence ?: 0xFFFFFFFFu).toInt()
            val preimage =
                buildBip143Preimage(
                    version = version,
                    hashPrevouts = hashPrevouts,
                    hashSequence = hashSequence,
                    prevTxidHex = input.hash,
                    prevIndex = input.index.toInt(),
                    scriptCode = scriptCode,
                    amount = input.amount,
                    sequence = sequence,
                    hashOutputs = hashOutputs,
                    locktime = locktime,
                    sighashType = sighashFlag,
                )
            sha256d(preimage)
        }
    }

    private fun extractWitnessPubKeyHash(input: BitcoinInput): ByteArray =
        when (input.scriptType.lowercase()) {
            "p2wpkh" -> parseWitnessProgram(input.scriptPubKey)
            "p2sh-p2wpkh" ->
                parseWitnessProgram(
                    input.redeemScript
                        ?: error(
                            "redeem_script required for P2SH-P2WPKH input ${input.hash}:${input.index}"
                        )
                )
            "p2tr" ->
                error(
                    "P2TR (Taproot) signing is not supported yet for input ${input.hash}:${input.index}"
                )
            else -> error("Unsupported script_type ${input.scriptType}")
        }

    private fun parseWitnessProgram(hex: String): ByteArray {
        val bytes = Numeric.hexStringToByteArray(hex)
        require(
            bytes.size == WITNESS_V0_KEY_HASH_LEN &&
                bytes[0] == 0x00.toByte() &&
                bytes[1] == 0x14.toByte()
        ) {
            "Expected v0 witness program 0x0014<20-byte hash>, got $hex"
        }
        return bytes.copyOfRange(2, WITNESS_V0_KEY_HASH_LEN)
    }

    private fun buildP2WPKHScriptCode(pubKeyHash: ByteArray): ByteArray {
        require(pubKeyHash.size == 20) { "P2WPKH pubkey hash must be 20 bytes" }
        return byteArrayOf(0x76.toByte(), 0xa9.toByte(), 0x14.toByte()) +
            pubKeyHash +
            byteArrayOf(0x88.toByte(), 0xac.toByte())
    }

    private fun serializePrevouts(inputs: List<BitcoinInput>): ByteArray {
        val buf = ByteArrayOutputStream()
        inputs.forEach { input ->
            buf.write(Numeric.hexStringToByteArray(input.hash).reversedArray())
            buf.write(uint32LE(input.index.toInt()))
        }
        return buf.toByteArray()
    }

    private fun serializeSequences(inputs: List<BitcoinInput>): ByteArray {
        val buf = ByteArrayOutputStream()
        inputs.forEach { input -> buf.write(uint32LE((input.sequence ?: 0xFFFFFFFFu).toInt())) }
        return buf.toByteArray()
    }

    private fun serializeOutputs(outputs: List<BitcoinOutput>): ByteArray {
        val buf = ByteArrayOutputStream()
        outputs.forEach { out ->
            buf.write(uint64LE(out.amount))
            val script = Numeric.hexStringToByteArray(out.scriptPubKey)
            buf.write(varInt(script.size.toLong()))
            buf.write(script)
        }
        return buf.toByteArray()
    }

    private fun buildBip143Preimage(
        version: Int,
        hashPrevouts: ByteArray,
        hashSequence: ByteArray,
        prevTxidHex: String,
        prevIndex: Int,
        scriptCode: ByteArray,
        amount: Long,
        sequence: Int,
        hashOutputs: ByteArray,
        locktime: Int,
        sighashType: Int,
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(uint32LE(version))
        buf.write(hashPrevouts)
        buf.write(hashSequence)
        buf.write(Numeric.hexStringToByteArray(prevTxidHex).reversedArray())
        buf.write(uint32LE(prevIndex))
        buf.write(varInt(scriptCode.size.toLong()))
        buf.write(scriptCode)
        buf.write(uint64LE(amount))
        buf.write(uint32LE(sequence))
        buf.write(hashOutputs)
        buf.write(uint32LE(locktime))
        buf.write(uint32LE(sighashType))
        return buf.toByteArray()
    }

    private fun buildScriptSig(input: BitcoinInput): ByteArray =
        when (input.scriptType.lowercase()) {
            "p2sh-p2wpkh" -> {
                val redeem =
                    Numeric.hexStringToByteArray(
                        input.redeemScript ?: error("redeem_script required for P2SH-P2WPKH input")
                    )
                ByteArrayOutputStream()
                    .apply {
                        write(byteArrayOf(redeem.size.toByte()))
                        write(redeem)
                    }
                    .toByteArray()
            }
            else -> ByteArray(0)
        }

    private fun serializeLegacyTransaction(
        inputs: List<BitcoinInput>,
        outputs: List<BitcoinOutput>,
        signBitcoin: SignBitcoin,
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(uint32LE(signBitcoin.version.toInt()))
        buf.write(varInt(inputs.size.toLong()))
        inputs.forEach { input ->
            buf.write(Numeric.hexStringToByteArray(input.hash).reversedArray())
            buf.write(uint32LE(input.index.toInt()))
            val scriptSig = buildScriptSig(input)
            buf.write(varInt(scriptSig.size.toLong()))
            buf.write(scriptSig)
            buf.write(uint32LE((input.sequence ?: 0xFFFFFFFFu).toInt()))
        }
        buf.write(varInt(outputs.size.toLong()))
        buf.write(serializeOutputs(outputs))
        buf.write(uint32LE(signBitcoin.locktime.toInt()))
        return buf.toByteArray()
    }

    private fun serializeSegwitTransaction(
        inputs: List<BitcoinInput>,
        outputs: List<BitcoinOutput>,
        signBitcoin: SignBitcoin,
        witnessByIndex: Map<Int, Pair<ByteArray, ByteArray>>,
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(uint32LE(signBitcoin.version.toInt()))
        buf.write(byteArrayOf(0x00.toByte(), 0x01.toByte()))
        buf.write(varInt(inputs.size.toLong()))
        inputs.forEach { input ->
            buf.write(Numeric.hexStringToByteArray(input.hash).reversedArray())
            buf.write(uint32LE(input.index.toInt()))
            val scriptSig = buildScriptSig(input)
            buf.write(varInt(scriptSig.size.toLong()))
            buf.write(scriptSig)
            buf.write(uint32LE((input.sequence ?: 0xFFFFFFFFu).toInt()))
        }
        buf.write(varInt(outputs.size.toLong()))
        buf.write(serializeOutputs(outputs))
        inputs.forEachIndexed { idx, _ ->
            val witness = witnessByIndex[idx]
            if (witness != null) {
                buf.write(varInt(2))
                buf.write(varInt(witness.first.size.toLong()))
                buf.write(witness.first)
                buf.write(varInt(witness.second.size.toLong()))
                buf.write(witness.second)
            } else {
                buf.write(varInt(0))
            }
        }
        buf.write(uint32LE(signBitcoin.locktime.toInt()))
        return buf.toByteArray()
    }

    private fun uint32LE(v: Int): ByteArray =
        byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())

    private fun uint64LE(v: Long): ByteArray = ByteArray(8) { i -> (v ushr (i * 8)).toByte() }

    private fun varInt(v: Long): ByteArray =
        when {
            v < 0xFDL -> byteArrayOf(v.toByte())
            v <= 0xFFFFL -> byteArrayOf(0xFDu.toByte(), v.toByte(), (v ushr 8).toByte())
            v <= 0xFFFFFFFFL -> byteArrayOf(0xFEu.toByte()) + uint32LE(v.toInt())
            else -> byteArrayOf(0xFFu.toByte()) + uint64LE(v)
        }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun sha256d(data: ByteArray): ByteArray = sha256(sha256(data))
}

private const val SIGHASH_ALL: Int = 0x01
private const val WITNESS_V0_KEY_HASH_LEN: Int = 22
