package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import java.security.MessageDigest
import tss.KeysignResponse
import wallet.core.jni.Base58
import wallet.core.jni.BitcoinSigHashType
import wallet.core.jni.CoinType
import wallet.core.jni.proto.BitcoinV2
import wallet.core.jni.proto.BitcoinV2.Input.Sequence
import wallet.core.jni.proto.Utxo
import wallet.core.jni.proto.Zcash

internal class SwapKitZcashSignerException(message: String) : Exception(message)

/**
 * Signs SwapKit's pre-built ZEC PSBT. The transparent ZEC tx is wrapped in a BIP-174 envelope; the
 * inner unsigned-tx body is **NU5 v5 (ZIP-225)**: header `0x80000005`, version group `0x26A7270A`,
 * consensus branch id, lock time, expiry height, the transparent bundle, then three zero counts for
 * Sapling spends/outputs and Orchard actions. Sapling v4 is refused (invalid on mainnet since NU7),
 * as are shielded bundles, non-P2PKH inputs and a branch id that differs from the live one.
 *
 * Sighash: ZIP-244 through WalletCore's signing-V2 builder ([UtxoHelper.zcashPreImageHashesV2]).
 * SwapKit's inputs, output scripts (verbatim) and their order are kept untouched, so the signed txid
 * is the one SwapKit / NEAR Intents tracks the route by.
 */
internal class SwapKitZcashSigner(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    private val coinType = CoinType.ZCASH
    private val utxo = UtxoHelper(coinType, vaultHexPublicKey, vaultHexChainCode)

    /**
     * ZIP-244 sighashes (sorted hex) for every input. [zcashBranchId] is the live branch id
     * resolved at send time (carried on the source-chain payload's UTXO specific); a null/empty
     * branch id is rejected (buildSigningInputData throws) — there is no compiled-in fallback.
     */
    fun getPreSignedImageHash(
        psbtBytes: ByteArray,
        targetAddress: String,
        fromAmount: BigInteger,
        zcashBranchId: String? = null,
    ): List<String> {
        val inputData = buildSigningInputData(psbtBytes, targetAddress, fromAmount, zcashBranchId)
        return utxo.zcashPreImageHashesV2(inputData).map { Numeric.toHexStringNoPrefix(it) }.sorted()
    }

    /**
     * Assemble the signed v5 broadcast tx through [UtxoHelper.compileZcashV2] (verifies each
     * ECDSA-DER signature against the vault key, then compiles).
     */
    fun getSignedTransaction(
        psbtBytes: ByteArray,
        targetAddress: String,
        fromAmount: BigInteger,
        signatures: Map<String, KeysignResponse>,
        zcashBranchId: String? = null,
    ): SignedTransactionResult {
        val inputData = buildSigningInputData(psbtBytes, targetAddress, fromAmount, zcashBranchId)
        return utxo.compileZcashV2(inputData, signatures)
    }

    /**
     * Build the serialized signing-V2 input replaying the PSBT's inputs and outputs verbatim.
     * [fromAmount] is the quoted swap amount (zatoshi) used to bound the miner fee. Exposed
     * `internal` for unit tests.
     */
    internal fun buildSigningInputData(
        psbtBytes: ByteArray,
        targetAddress: String,
        fromAmount: BigInteger,
        zcashBranchId: String? = null,
    ): ByteArray {
        if (psbtBytes.isEmpty()) {
            throw SwapKitZcashSignerException("SwapKit ZEC PSBT payload is empty")
        }

        // 1. Parse BIP-174 framing + the v5 body.
        val header = SwapKitPsbtParser.parseFramingHeader(psbtBytes)
        val parsedTx = parseUnsignedTx(header.unsignedTxBytes)
        requireLiveBranchId(zcashBranchId, parsedTx.branchId)

        // 2. Drain per-input + per-output maps.
        val cursor = header.cursor
        val inputMaps = parsedTx.inputs.map { cursor.readMap() }
        parsedTx.outputs.forEach { cursor.readMap() }

        // 3. Resolve per-input UTXO (ZEC ships WITNESS_UTXO per the captured fixture; accept
        // NON_WITNESS_UTXO too for robustness).
        val inputs =
            parsedTx.inputs.mapIndexed { index, parsedInput ->
                val (amount, scriptPubKey) =
                    resolvePrevUtxo(
                        inputMaps[index],
                        parsedInput.prevTxIdLE,
                        parsedInput.prevIndex,
                        index,
                    )
                val keyHash = assertP2PKHAndExtractKeyHash(scriptPubKey, index, "input")
                ZcashInput(
                    prevTxIdLE = parsedInput.prevTxIdLE,
                    prevIndex = parsedInput.prevIndex,
                    sequence = parsedInput.sequence,
                    amount = amount,
                    scriptPubKey = scriptPubKey,
                    keyHash = keyHash,
                )
            }

        return assembleSigningInput(
            inputs,
            parsedTx,
            fromAmount,
        )
    }

    private fun assembleSigningInput(
        inputs: List<ZcashInput>,
        tx: ParsedZcashTx,
        fromAmount: BigInteger,
    ): ByteArray {
        val outputs = tx.outputs
        if (inputs.isEmpty() || outputs.isEmpty()) {
            throw SwapKitZcashSignerException("SwapKit ZEC PSBT has empty inputs or outputs")
        }
        // Deposit + optional change + optional OP_RETURN memo.
        if (outputs.size > 3) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT has ${outputs.size} outputs; ZEC signer expects at most 1 " +
                    "deposit + 1 change + 1 OP_RETURN"
            )
        }
        if (outputs.count { isOpReturn(it.scriptPubKey) } > 1) {
            throw SwapKitZcashSignerException("SwapKit ZEC PSBT has more than one OP_RETURN output")
        }
        outputs.forEachIndexed { idx, out ->
            if (!isOpReturn(out.scriptPubKey)) {
                assertP2PKHAndExtractKeyHash(out.scriptPubKey, idx, "output")
            }
        }
        val totalIn = inputs.sumOf { it.amount }
        val totalOut = outputs.sumOf { it.amount }
        val fee = totalIn - totalOut
        if (fee < 0) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT negative fee: inputs=$totalIn outputs=$totalOut"
            )
        }
        // Fee ceiling — bound the miner fee by the quoted swap amount (the Verify screen never
        // shows the fee and the deposit address isn't bound, so an under-allocated change output
        // would otherwise burn the remainder to the miner). Mirrors the legacy signer.
        if (BigInteger.valueOf(fee) > fromAmount) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT miner fee $fee exceeds the quoted swap amount $fromAmount; " +
                    "refusing to sign"
            )
        }
        verifyVaultBinding(inputs, outputs, fromAmount)

        val publicKey = ByteString.copyFrom(utxo.deriveVaultPublicKey().data())
        val builder =
            BitcoinV2.TransactionBuilder.newBuilder()
                .setVersion(BitcoinV2.TransactionVersion.UseDefault)
                .setLockTime(tx.lockTime.toInt())
                .setInputSelector(BitcoinV2.InputSelector.UseAll)
                .setFixedDustThreshold(0)
                .setZcashExtraData(
                    Zcash.TransactionBuilderExtraData.newBuilder()
                        .setBranchId(ByteString.copyFrom(tx.branchId))
                        .setExpiryHeight(tx.expiryHeight.toInt())
                        .setTxVersion(Zcash.TransactionVersion.V5)
                )
        inputs.forEach { input ->
            builder.addInputs(
                BitcoinV2.Input.newBuilder()
                    .setOutPoint(
                        Utxo.OutPoint.newBuilder()
                            .setHash(ByteString.copyFrom(input.prevTxIdLE))
                            .setVout(input.prevIndex.toInt())
                    )
                    .setValue(input.amount)
                    .setSequence(Sequence.newBuilder().setSequence(input.sequence.toInt()))
                    .setSighashType(BitcoinSigHashType.ALL.value())
                    .setScriptBuilder(
                        BitcoinV2.Input.InputBuilder.newBuilder()
                            .setP2Pkh(BitcoinV2.PublicKeyOrHash.newBuilder().setPubkey(publicKey))
                    )
            )
        }
        // Output scripts verbatim and in order, so the txid matches the one SwapKit baked in.
        outputs.forEach { out ->
            builder.addOutputs(
                BitcoinV2.Output.newBuilder()
                    .setValue(out.amount)
                    .setCustomScriptPubkey(ByteString.copyFrom(out.scriptPubKey))
            )
        }
        return utxo.zcashSigningInputV2(builder)
    }

    /**
     * Ties the PSBT to the vault before signing: every input spends from the vault, and the outputs
     * not paying back to it don't total more than the quoted amount. Mirrors the legacy signer. The
     * deposit recipient isn't pinned: the ZEC route's on-chain deposit address differs from
     * SwapKit's declared `targetAddress`.
     */
    private fun verifyVaultBinding(
        inputs: List<ZcashInput>,
        outputs: List<ZcashOutput>,
        fromAmount: BigInteger,
    ) {
        val vaultScript = utxo.vaultP2pkhLockScript()
        inputs.forEachIndexed { index, input ->
            if (!input.scriptPubKey.contentEquals(vaultScript)) {
                throw SwapKitZcashSignerException(
                    "SwapKit ZEC PSBT input #$index does not spend from this vault"
                )
            }
        }
        // Outputs paying the vault are change, wherever they sit; everything else leaves the vault.
        // SwapKit doesn't fix the output order or require a change output, so only the value
        // leaving the vault is bounded, not which index carries the deposit.
        val leavingVault =
            outputs
                .filterNot { it.scriptPubKey.contentEquals(vaultScript) }
                .sumOf { BigInteger.valueOf(it.amount) }
        if (leavingVault > fromAmount) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT sends $leavingVault out of the vault, more than the quoted swap " +
                    "amount $fromAmount; refusing to sign"
            )
        }
    }

    private fun resolvePrevUtxo(
        inputMap: Map<String, ByteArray>,
        prevTxIdLE: ByteArray,
        prevIndex: Long,
        inputIndex: Int,
    ): Pair<Long, ByteArray> {
        inputMap[KEY_NON_WITNESS_UTXO]?.let {
            return parseNonWitnessUtxo(it, prevTxIdLE, prevIndex, inputIndex)
        }
        inputMap[KEY_WITNESS_UTXO]?.let {
            return parseWitnessUtxo(it, inputIndex)
        }
        throw SwapKitZcashSignerException(
            "SwapKit ZEC PSBT input #$inputIndex is missing a prev-tx UTXO record"
        )
    }

    private fun parseNonWitnessUtxo(
        data: ByteArray,
        prevTxIdLE: ByteArray,
        prevIndex: Long,
        inputIndex: Int,
    ): Pair<Long, ByteArray> {
        // BIP-174: double-SHA256 of the embedded prev-tx must equal the outpoint txid, or a
        // substituted record could feed a forged amount into the ZIP-243 sighash (which commits the
        // input amount) — caught here rather than as a broadcast rejection.
        if (!sha256d(data).contentEquals(prevTxIdLE)) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT input #$inputIndex NON_WITNESS_UTXO does not hash to its outpoint txid"
            )
        }
        val cursor = PsbtCursor(data)
        cursor.readUInt32LE()
        val inCount = cursor.readCompactSize()
        repeat(cursor.asLength(inCount)) {
            cursor.readBytes(32)
            cursor.readUInt32LE()
            val sigLen = cursor.readCompactSize()
            cursor.readBytes(cursor.asLength(sigLen))
            cursor.readUInt32LE()
        }
        val outCount = cursor.readCompactSize()
        if (prevIndex >= outCount) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT input #$inputIndex prev index $prevIndex >= output count $outCount"
            )
        }
        var amount = 0L
        var scriptPubKey = ByteArray(0)
        for (i in 0 until outCount) {
            val outAmount = cursor.readUInt64LE()
            val scriptLen = cursor.readCompactSize()
            val script = cursor.readBytes(cursor.asLength(scriptLen))
            if (i == prevIndex) {
                amount = outAmount
                scriptPubKey = script
            }
        }
        return amount to scriptPubKey
    }

    private fun parseWitnessUtxo(data: ByteArray, inputIndex: Int): Pair<Long, ByteArray> {
        val cursor = PsbtCursor(data)
        val amount = cursor.readUInt64LE()
        val scriptLen = cursor.readCompactSize()
        val script = cursor.readBytes(cursor.asLength(scriptLen))
        if (!cursor.isAtEnd) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT input #$inputIndex WITNESS_UTXO has trailing bytes"
            )
        }
        return amount to script
    }

    private data class ZcashInput(
        val prevTxIdLE: ByteArray,
        val prevIndex: Long,
        val sequence: Long,
        val amount: Long,
        val scriptPubKey: ByteArray,
        val keyHash: ByteArray,
    )

    private data class ZcashOutput(val amount: Long, val scriptPubKey: ByteArray)

    private data class ParsedZcashTxInput(
        val prevTxIdLE: ByteArray,
        val prevIndex: Long,
        val sequence: Long,
    )

    private data class ParsedZcashTx(
        val branchId: ByteArray,
        val lockTime: Long,
        val expiryHeight: Long,
        val inputs: List<ParsedZcashTxInput>,
        val outputs: List<ZcashOutput>,
    )

    private fun sha256d(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(data))
    }

    internal companion object {
        private const val KEY_NON_WITNESS_UTXO = "00"
        private const val KEY_WITNESS_UTXO = "01"

        /** Overwinter flag (high bit) + version 5 — `0x80000005`. */
        private const val ZCASH_TX_VERSION = 0x80000005L
        /** NU5 (ZIP-225) version group id. */
        private const val ZCASH_VERSION_GROUP_ID = 0x26A7270AL

        /**
         * The transparent address the PSBT's deposit output pays: the `toAddress`
         * [buildSigningInputData] hands WalletCore, decoded from [psbtBytes] alone so a review can
         * show it without the vault or the branch id.
         *
         * @throws SwapKitPsbtException or [SwapKitZcashSignerException] when the PSBT doesn't
         *   parse, as signing it would.
         */
        fun depositAddress(psbtBytes: ByteArray, targetAddressHint: String): String {
            val header = SwapKitPsbtParser.parseFramingHeader(psbtBytes)
            val deposit =
                parseUnsignedTx(header.unsignedTxBytes).outputs.firstOrNull {
                    !isOpReturn(it.scriptPubKey)
                }
                    ?: throw SwapKitZcashSignerException("SwapKit ZEC PSBT has no outputs")
            val keyHash = assertP2PKHAndExtractKeyHash(deposit.scriptPubKey, 0, "output")
            return zcashAddress(keyHash) ?: targetAddressHint
        }

        /**
         * Parse the NU5 v5 (ZIP-225) unsigned-tx body. Rejects Sapling v4 outright (invalid since
         * NU7) and any shielded bundle (we can't sign those with MPC).
         */
        private fun parseUnsignedTx(data: ByteArray): ParsedZcashTx {
            val cursor = PsbtCursor(data)
            val version = cursor.readUInt32LE()
            val versionGroupID = cursor.readUInt32LE()
            if (version != ZCASH_TX_VERSION || versionGroupID != ZCASH_VERSION_GROUP_ID) {
                throw SwapKitZcashSignerException(
                    "SwapKit ZEC unsupported Zcash version 0x${version.toString(16)} / group " +
                        "0x${versionGroupID.toString(16)} (only NU5 v5 transparent can be signed)"
                )
            }
            val branchId = cursor.readBytes(4)
            val lockTime = cursor.readUInt32LE()
            val expiryHeight = cursor.readUInt32LE()
            val inCount = cursor.readCompactSize()
            val inputs =
                (0L until inCount).map {
                    val prevBytes = cursor.readBytes(32)
                    val prevIndex = cursor.readUInt32LE()
                    // Unsigned-tx inputs must carry empty scriptSigs; reject a non-empty one rather
                    // than silently drop it from the rebuilt tx (it would change the txid).
                    val sigLen = cursor.readCompactSize()
                    if (sigLen != 0L) {
                        throw SwapKitZcashSignerException(
                            "SwapKit ZEC unsigned-tx input #$it scriptSig must be empty"
                        )
                    }
                    val sequence = cursor.readUInt32LE()
                    ParsedZcashTxInput(prevBytes, prevIndex, sequence)
                }
            val outCount = cursor.readCompactSize()
            val outputs =
                (0L until outCount).map {
                    val amount = cursor.readUInt64LE()
                    val scriptLen = cursor.readCompactSize()
                    ZcashOutput(amount, cursor.readBytes(cursor.asLength(scriptLen)))
                }
            // Transparent-only: Sapling spends, Sapling outputs and Orchard actions must all be
            // zero, and nothing may follow (trailing bytes would be dropped from the rebuilt tx).
            val nSaplingSpend = cursor.readCompactSize()
            val nSaplingOutput = cursor.readCompactSize()
            val nOrchardAction = cursor.readCompactSize()
            if (nSaplingSpend != 0L || nSaplingOutput != 0L || nOrchardAction != 0L) {
                throw SwapKitZcashSignerException(
                    "SwapKit ZEC PSBT carries a shielded bundle; only transparent-only txs are supported"
                )
            }
            if (!cursor.isAtEnd) {
                throw SwapKitZcashSignerException("SwapKit ZEC unsigned-tx body has trailing bytes")
            }
            return ParsedZcashTx(branchId, lockTime, expiryHeight, inputs, outputs)
        }

        /** The PSBT's branch id must be the live one, or the signed tx is rejected on-chain. */
        private fun requireLiveBranchId(liveHex: String?, psbtBranchId: ByteArray) {
            if (liveHex.isNullOrEmpty()) {
                throw SwapKitZcashSignerException(
                    "Zcash consensus branch id is unavailable; cannot sign the SwapKit ZEC " +
                        "transaction without the live branch id"
                )
            }
            if (!Numeric.hexStringToByteArray(liveHex).contentEquals(psbtBranchId)) {
                throw SwapKitZcashSignerException(
                    "SwapKit ZEC PSBT branch id ${Numeric.toHexStringNoPrefix(psbtBranchId)} " +
                        "differs from the live branch id $liveHex"
                )
            }
        }

        private fun isOpReturn(script: ByteArray) = script.isNotEmpty() && script[0] == 0x6a.toByte()

        private fun assertP2PKHAndExtractKeyHash(
            scriptPubKey: ByteArray,
            index: Int,
            role: String,
        ): ByteArray {
            if (
                !(scriptPubKey.size == 25 &&
                    scriptPubKey[0] == 0x76.toByte() &&
                    scriptPubKey[1] == 0xa9.toByte() &&
                    scriptPubKey[2] == 0x14.toByte() &&
                    scriptPubKey[23] == 0x88.toByte() &&
                    scriptPubKey[24] == 0xac.toByte())
            ) {
                throw SwapKitZcashSignerException(
                    "SwapKit ZEC PSBT $role #$index scriptPubKey is not P2PKH: " +
                        Numeric.toHexStringNoPrefix(scriptPubKey)
                )
            }
            return scriptPubKey.copyOfRange(3, 23)
        }

        private fun zcashAddress(hash: ByteArray): String? =
            Base58.encode(byteArrayOf(0x1C, 0xB8.toByte()) + hash)
    }
}
