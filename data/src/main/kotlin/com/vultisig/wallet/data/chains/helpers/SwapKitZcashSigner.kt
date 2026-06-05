package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.utils.Numeric
import java.math.BigInteger
import java.security.MessageDigest
import tss.KeysignResponse
import wallet.core.jni.Base58
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

internal class SwapKitZcashSignerException(message: String) : Exception(message)

/**
 * Signs SwapKit's pre-built ZEC PSBT. Ported from iOS' `SwapKitZcashSigner`. The transparent ZEC tx
 * is wrapped in a BIP-174 envelope but the inner unsigned-tx body is **Sapling-v4**, not BIP-144
 * segwit: extra `nVersionGroupId` (4B), `expiryHeight` (4B), `valueBalance` (i64), and three varint
 * zeros for shielded counts. We walk those, assert the chain is on Sapling-v4 transparent-only (v5
 * NU5 hard-rejected; non-zero shielded fields hard-rejected), then hand the P2PKH inputs to
 * WalletCore's `CoinType.ZCASH` path.
 *
 * Sighash: WalletCore's ZEC signer implements ZIP-243 (the Sapling signature digest with the
 * personalised Blake2b-256). It reads the branch id from the plan — the existing native ZEC send
 * uses [ZCASH_BRANCH_ID_HEX] `30f33754`, and we match that so the digest is identical to a manually
 * sent ZEC transaction. (Deviating to the Sapling-v4 spec id `0x76b809bb` would produce a different
 * digest the chain rejects.)
 *
 * The frozen-plan pattern is load-bearing for the same reason as [SwapKitLegacyP2PKHSigner]: NEAR
 * Intents tracks the route by the tx_id SwapKit baked into the PSBT, so we sign verbatim.
 */
internal class SwapKitZcashSigner(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
) {
    private val coinType = CoinType.ZCASH
    private val utxo = UtxoHelper(coinType, vaultHexPublicKey, vaultHexChainCode)

    /** ZIP-243 preimage hashes (sorted hex) for every input. */
    fun getPreSignedImageHash(
        psbtBytes: ByteArray,
        targetAddress: String,
        fromAmount: BigInteger,
    ): List<String> {
        val inputData = buildSigningInputData(psbtBytes, targetAddress, fromAmount)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput = Bitcoin.PreSigningOutput.parseFrom(preHashes).checkError()
        return preSigningOutput.hashPublicKeysList
            .map { Numeric.toHexStringNoPrefix(it.dataHash.toByteArray()) }
            .sorted()
    }

    /**
     * Assemble the signed broadcast tx with the Sapling-v4 header + ZIP-243 sighash baked into
     * WalletCore's compileWithSignatures. Reuses [UtxoHelper.getSignedTransaction] over the
     * frozen-plan input data (re-derives the vault pubkey, verifies each ECDSA-DER signature, then
     * compiles).
     */
    fun getSignedTransaction(
        psbtBytes: ByteArray,
        targetAddress: String,
        fromAmount: BigInteger,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = buildSigningInputData(psbtBytes, targetAddress, fromAmount)
        return utxo.getSignedTransaction(inputData, signatures)
    }

    /**
     * Build the serialized [Bitcoin.SigningInput] with a frozen [Bitcoin.TransactionPlan] (branch
     * id set) derived directly from the Sapling PSBT bytes. [fromAmount] is the quoted swap amount
     * (zatoshi) used to bound the miner fee. Exposed `internal` for unit tests.
     */
    internal fun buildSigningInputData(
        psbtBytes: ByteArray,
        targetAddress: String,
        fromAmount: BigInteger,
    ): ByteArray {
        if (psbtBytes.isEmpty()) {
            throw SwapKitZcashSignerException("SwapKit ZEC PSBT payload is empty")
        }

        // 1. Parse BIP-174 framing + the Sapling-v4 body.
        val header = SwapKitPsbtParser.parseFramingHeader(psbtBytes)
        val parsedTx = parseSaplingUnsignedTx(header.unsignedTxBytes)

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
                SaplingInput(
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
            parsedTx.outputs,
            parsedTx.lockTime,
            fromAmount,
            targetAddress,
        )
    }

    private fun assembleSigningInput(
        inputs: List<SaplingInput>,
        outputs: List<SaplingOutput>,
        lockTime: Long,
        fromAmount: BigInteger,
        targetAddressHint: String,
    ): ByteArray {
        if (inputs.isEmpty() || outputs.isEmpty()) {
            throw SwapKitZcashSignerException("SwapKit ZEC PSBT has empty inputs or outputs")
        }
        if (outputs.size > 2) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT has ${outputs.size} outputs; ZEC signer expects 1 deposit + " +
                    "optional 1 change"
            )
        }
        val outputKeyHashes =
            outputs.mapIndexed { idx, out ->
                assertP2PKHAndExtractKeyHash(out.scriptPubKey, idx, "output")
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
        // shows
        // the fee and the deposit address isn't bound, so an under-allocated change output would
        // otherwise burn the remainder to the miner). Mirrors the legacy signer.
        if (BigInteger.valueOf(fee) > fromAmount) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT miner fee $fee exceeds the quoted swap amount $fromAmount; " +
                    "refusing to sign"
            )
        }

        val depositAmount = outputs[0].amount
        val changeAmount = outputs.drop(1).sumOf { it.amount }
        // Derive t1 addresses from the parsed output hash160s (NOT from `targetAddress` — the route
        // may allocate a transparent deposit address that differs from the SwapKit hint). Fall back
        // to the hint only if derivation fails.
        val depositAddress = zcashAddress(outputKeyHashes[0]) ?: targetAddressHint
        val changeAddress =
            if (outputs.size >= 2) {
                zcashAddress(outputKeyHashes[1]) ?: targetAddressHint
            } else {
                zcashAddress(inputs[0].keyHash) ?: targetAddressHint
            }

        val utxos =
            inputs.map { input ->
                Bitcoin.UnspentTransaction.newBuilder()
                    .setAmount(input.amount)
                    .setOutPoint(
                        Bitcoin.OutPoint.newBuilder()
                            .setHash(ByteString.copyFrom(input.prevTxIdLE))
                            .setIndex(input.prevIndex.toInt())
                            .setSequence(input.sequence.toInt())
                            .build()
                    )
                    .setScript(ByteString.copyFrom(input.scriptPubKey))
                    .build()
            }

        // ZEC ZIP-243 needs the branch id on the plan — WalletCore reads it during preimage
        // construction. Mirror the native send path's value so digest derivation is identical.
        val plan =
            Bitcoin.TransactionPlan.newBuilder()
                .setAmount(depositAmount)
                .setAvailableAmount(totalIn)
                .setFee(fee)
                .setChange(changeAmount)
                .setError(SigningError.OK)
                .setBranchId(ByteString.fromHex(ZCASH_BRANCH_ID_HEX))
                .addAllUtxos(utxos)
                .build()

        val signingInput =
            Bitcoin.SigningInput.newBuilder()
                .setHashType(BitcoinScript.hashTypeForCoin(coinType))
                .setByteFee(1L)
                .setUseMaxAmount(false)
                .setAmount(depositAmount)
                .setCoinType(coinType.value())
                // Reproduce the PSBT's lockTime so the rebuilt tx_id matches the one the NEAR route
                // tracks; WalletCore otherwise defaults it to 0.
                .setLockTime(lockTime.toInt())
                .setToAddress(depositAddress)
                .setChangeAddress(changeAddress)
                .addAllUtxo(utxos)
                .setPlan(plan)

        inputs.forEach { input ->
            val redeem = BitcoinScript.buildPayToPublicKeyHash(input.keyHash)
            signingInput.putScripts(
                Numeric.toHexStringNoPrefix(input.keyHash),
                ByteString.copyFrom(redeem.data()),
            )
        }

        return signingInput.build().toByteArray()
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

    private data class SaplingInput(
        val prevTxIdLE: ByteArray,
        val prevIndex: Long,
        val sequence: Long,
        val amount: Long,
        val scriptPubKey: ByteArray,
        val keyHash: ByteArray,
    )

    private data class SaplingOutput(val amount: Long, val scriptPubKey: ByteArray)

    private data class ParsedSaplingTxInput(
        val prevTxIdLE: ByteArray,
        val prevIndex: Long,
        val sequence: Long,
    )

    private data class ParsedSaplingTx(
        val lockTime: Long,
        val inputs: List<ParsedSaplingTxInput>,
        val outputs: List<SaplingOutput>,
    )

    /**
     * Parse the Sapling-v4 unsigned-tx body. Rejects NU5 (v5) outright — a different sighash
     * construction (ZIP-244) — and any tx with shielded value flowing (we can't sign shielded
     * bundles with MPC).
     */
    private fun parseSaplingUnsignedTx(data: ByteArray): ParsedSaplingTx {
        val cursor = PsbtCursor(data)
        val version = cursor.readUInt32LE()
        val versionGroupID = cursor.readUInt32LE()
        if (version != SAPLING_TX_VERSION || versionGroupID != SAPLING_VERSION_GROUP_ID) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC unsupported Zcash version 0x${version.toString(16)} / group " +
                    "0x${versionGroupID.toString(16)} (only Sapling-v4 transparent is supported)"
            )
        }
        val inCount = cursor.readCompactSize()
        val inputs =
            (0L until inCount).map {
                val prevBytes = cursor.readBytes(32)
                val prevIndex = cursor.readUInt32LE()
                // Unsigned-tx inputs must carry empty scriptSigs; reject a non-empty one rather
                // than
                // silently skip it (the frozen plan rebuilds the tx from these fields, so dropped
                // bytes would change the broadcast tx_id the route is tracked by).
                val sigLen = cursor.readCompactSize()
                if (sigLen != 0L) {
                    throw SwapKitZcashSignerException(
                        "SwapKit ZEC unsigned-tx input #$it scriptSig must be empty"
                    )
                }
                val sequence = cursor.readUInt32LE()
                ParsedSaplingTxInput(prevBytes, prevIndex, sequence)
            }
        val outCount = cursor.readCompactSize()
        val outputs =
            (0L until outCount).map {
                val amount = cursor.readUInt64LE()
                val scriptLen = cursor.readCompactSize()
                SaplingOutput(amount, cursor.readBytes(cursor.asLength(scriptLen)))
            }
        val lockTime = cursor.readUInt32LE()
        val expiryHeight = cursor.readUInt32LE()
        // WalletCore's `Bitcoin.SigningInput` has no expiryHeight field, so a non-zero expiryHeight
        // can't be threaded into the rebuilt tx — WalletCore would emit it with expiryHeight 0 and
        // a
        // different tx_id than the PSBT the NEAR route is tracked by. Reject it loudly rather than
        // silently mistrack. (lockTime, by contrast, IS settable — see assembleSigningInput.)
        if (expiryHeight != 0L) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT has expiryHeight $expiryHeight; only 0 (no expiry) is supported " +
                    "because WalletCore can't reproduce a non-zero expiry in the rebuilt tx"
            )
        }
        // Sapling-v4 transparent-only: all shielded fields must be zero.
        val valueBalance = cursor.readUInt64LE()
        val nShieldedSpend = cursor.readCompactSize()
        val nShieldedOutput = cursor.readCompactSize()
        val nJoinSplit = cursor.readCompactSize()
        if (
            valueBalance != 0L || nShieldedSpend != 0L || nShieldedOutput != 0L || nJoinSplit != 0L
        ) {
            throw SwapKitZcashSignerException(
                "SwapKit ZEC PSBT carries a shielded bundle; only transparent-only txs are supported"
            )
        }
        // Transparent-only Sapling v4 ends right after the (all-zero) shielded counts — there is no
        // joinSplit/binding-sig payload. Trailing bytes would be dropped from the rebuilt tx and
        // diverge it from the quoted PSBT body, so consume the body exactly.
        if (!cursor.isAtEnd) {
            throw SwapKitZcashSignerException("SwapKit ZEC unsigned-tx body has trailing bytes")
        }
        return ParsedSaplingTx(lockTime, inputs, outputs)
    }

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

    /** ZEC transparent `t1…` address from a 20-byte hash160 (two-byte prefix `0x1C 0xB8`). */
    private fun zcashAddress(hash: ByteArray): String? =
        Base58.encode(byteArrayOf(0x1C, 0xB8.toByte()) + hash)

    private fun sha256d(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(data))
    }

    private companion object {
        private const val KEY_NON_WITNESS_UTXO = "00"
        private const val KEY_WITNESS_UTXO = "01"

        /** Overwinter flag (high bit) + version 4 — `0x80000004`. */
        private const val SAPLING_TX_VERSION = 0x80000004L
        /** Sapling consensus group id. */
        private const val SAPLING_VERSION_GROUP_ID = 0x892F2085L

        /**
         * Branch id matching the existing native ZEC send. WalletCore reads it as the ZIP-243
         * personalised-Blake2b branch identifier; diverging to the Sapling-v4-spec `0x76b809bb`
         * would produce a digest the network rejects. Sourced from [ZCASH_ZIP243_BRANCH_ID_HEX] so
         * both signing paths stay locked together.
         */
        private const val ZCASH_BRANCH_ID_HEX = ZCASH_ZIP243_BRANCH_ID_HEX
    }
}
