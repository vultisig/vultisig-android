package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import com.vultisig.wallet.data.crypto.checkError
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.utils.Numeric
import com.vultisig.wallet.data.utils.getDustThreshold
import tss.KeysignResponse
import wallet.core.jni.Base58
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.TransactionCompiler
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

internal class SwapKitLegacyP2PKHSignerException(message: String) : Exception(message)

/**
 * Shared bridge between a SwapKit PSBT and WalletCore's `TransactionCompiler` for **legacy P2PKH
 * UTXO chains**: DOGE (no segwit ever), BCH (forked 2017, no segwit), DASH (no segwit). Ported from
 * iOS' `SwapKitLegacyP2PKHSigner`.
 *
 * Why this exists separately from [SwapKitBtcSigner]: that signer computes **BIP-143** sighashes,
 * which are segwit-only (`hashPrevouts` / `hashSequence` / `hashOutputs` assume witness semantics).
 * DOGE / BCH / DASH inputs are pure P2PKH (`76 a9 14 <20> 88 ac`) and need **legacy sighashing**,
 * which WalletCore's `TransactionCompiler` handles end-to-end via the per-chain [CoinType] (the
 * same path the native send helper rides). BCH adds `SIGHASH_FORKID` natively via
 * `BitcoinScript.hashTypeForCoin(BITCOINCASH)`.
 *
 * The "frozen plan" pattern is load-bearing: if we let WalletCore replan UTXO selection it would
 * compute a different tx and a different `tx_id`. NEAR Intents tracks the route by the tx_id
 * SwapKit baked into the PSBT — we sign verbatim or we break tracking. So we build the
 * [Bitcoin.SigningInput] with a frozen [Bitcoin.TransactionPlan] derived directly from the PSBT
 * bytes instead of calling `AnySigner.plan`.
 *
 * PSBT framing primitives live in [SwapKitPsbtParser]; the legacy (non-witness) unsigned-tx body
 * parser stays here.
 */
internal class SwapKitLegacyP2PKHSigner(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
    private val coinType: CoinType,
) {
    private val utxo = UtxoHelper(coinType, vaultHexPublicKey, vaultHexChainCode)

    /**
     * Legacy ECDSA P2PKH sighashes (sorted hex) for every input. WalletCore handles the per-input
     * preimage construction via the frozen-plan input data and [coinType] (BCH gets SIGHASH_FORKID
     * through `hashTypeForCoin`).
     */
    fun getPreSignedImageHash(psbtBytes: ByteArray, targetAddress: String): List<String> {
        val inputData = buildSigningInputData(psbtBytes, targetAddress)
        val preHashes = TransactionCompiler.preImageHashes(coinType, inputData)
        val preSigningOutput = Bitcoin.PreSigningOutput.parseFrom(preHashes).checkError()
        return preSigningOutput.hashPublicKeysList
            .map { Numeric.toHexStringNoPrefix(it.dataHash.toByteArray()) }
            .sorted()
    }

    /**
     * Assemble the signed broadcast tx from the SwapKit PSBT and MPC signatures. Reuses
     * [UtxoHelper.getSignedTransaction] over the frozen-plan input data: it re-derives the vault
     * pubkey, verifies every ECDSA-DER signature against its per-input preimage hash, and runs
     * `TransactionCompiler.compileWithSignatures`.
     */
    fun getSignedTransaction(
        psbtBytes: ByteArray,
        targetAddress: String,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val inputData = buildSigningInputData(psbtBytes, targetAddress)
        return utxo.getSignedTransaction(inputData, signatures)
    }

    /**
     * Build the serialized [Bitcoin.SigningInput] with a **frozen** [Bitcoin.TransactionPlan]
     * derived directly from the PSBT bytes. Exposed `internal` so per-chain unit tests can pin the
     * structural shape (input count, scriptPubKey patterns, plan amount/change/fee).
     */
    internal fun buildSigningInputData(psbtBytes: ByteArray, targetAddress: String): ByteArray {
        if (psbtBytes.isEmpty()) {
            throw SwapKitLegacyP2PKHSignerException("SwapKit PSBT payload is empty")
        }

        // 1. Parse BIP-174 framing + the legacy unsigned-tx body.
        val header = SwapKitPsbtParser.parseFramingHeader(psbtBytes)
        val parsedTx = parseLegacyUnsignedTx(header.unsignedTxBytes)

        // 2. Drain the per-input and per-output maps off the cursor (outputs parsed for spec
        // compliance / forward-compat even though no per-output field is read).
        val cursor = header.cursor
        val inputMaps = parsedTx.inputs.map { cursor.readMap() }
        parsedTx.outputs.forEach { cursor.readMap() }

        // 3. Resolve per-input scriptPubKey + amount + keyHash. SwapKit ships either
        // NON_WITNESS_UTXO (full prev-tx, key 0x00 — BIP-174's recommendation for legacy P2PKH;
        // DOGE confirmed) or WITNESS_UTXO (key 0x01, BTC-style compact). Accept both.
        val inputs =
            parsedTx.inputs.mapIndexed { index, parsedInput ->
                val (amount, scriptPubKey) =
                    resolvePrevUtxo(inputMaps[index], parsedInput.prevIndex, index)
                val keyHash = assertP2PKHAndExtractKeyHash(scriptPubKey, index, "input")
                LegacyP2PKHInput(
                    prevTxIdLE = parsedInput.prevTxIdLE,
                    prevIndex = parsedInput.prevIndex,
                    sequence = parsedInput.sequence,
                    amount = amount,
                    scriptPubKey = scriptPubKey,
                    keyHash = keyHash,
                )
            }

        return assembleSigningInput(inputs, parsedTx.outputs, targetAddress)
    }

    /**
     * Build the [Bitcoin.SigningInput] with a frozen plan. WalletCore reconstructs every output
     * from `toAddress` + `changeAddress` (it does NOT consume per-output scripts from the plan), so
     * we assert every output is a P2PKH we can faithfully re-emit through the address-only API,
     * then derive both addresses from the PSBT's actual output hash160s — making the rebuilt tx
     * byte-identical to the PSBT's intended outputs (preserves the NEAR Intents route `tx_id` and
     * the deposit destination). Anything other than 1 deposit + optional 1 change P2PKH output is
     * hard-rejected.
     */
    private fun assembleSigningInput(
        inputs: List<LegacyP2PKHInput>,
        outputs: List<LegacyP2PKHOutput>,
        targetAddressHint: String,
    ): ByteArray {
        if (inputs.isEmpty() || outputs.isEmpty()) {
            throw SwapKitLegacyP2PKHSignerException("SwapKit PSBT has empty inputs or outputs")
        }
        if (outputs.size > 2) {
            throw SwapKitLegacyP2PKHSignerException(
                "SwapKit PSBT has ${outputs.size} outputs; legacy signer expects 1 deposit + " +
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
            throw SwapKitLegacyP2PKHSignerException(
                "SwapKit PSBT negative fee: inputs=$totalIn outputs=$totalOut"
            )
        }

        val depositAmount = outputs[0].amount
        val changeAmount = outputs.drop(1).sumOf { it.amount }
        // Derive addresses that round-trip back to the exact scriptPubKeys the PSBT shipped. The
        // SwapKit `targetAddress` hint is used only if hash160 → address derivation fails
        // (shouldn't
        // happen for DOGE/BCH/DASH — all decode via single-byte base58check).
        val depositAddress = legacyAddress(outputKeyHashes[0]) ?: targetAddressHint
        val changeAddress =
            if (outputs.size >= 2) {
                legacyAddress(outputKeyHashes[1]) ?: targetAddressHint
            } else {
                // No change output — point `changeAddress` at the source pubkey hash so
                // WalletCore's
                // validator accepts the input. The plan's `change = 0` means no change output is
                // actually emitted, so the address is a structural placeholder.
                legacyAddress(inputs[0].keyHash) ?: targetAddressHint
            }

        val utxos =
            inputs.map { input ->
                Bitcoin.UnspentTransaction.newBuilder()
                    .setAmount(input.amount)
                    .setOutPoint(
                        // `prevTxIdLE` is already in internal little-endian wire order — set it
                        // verbatim (do NOT reverse, unlike the native send path which starts from a
                        // display-order hash).
                        Bitcoin.OutPoint.newBuilder()
                            .setHash(ByteString.copyFrom(input.prevTxIdLE))
                            .setIndex(input.prevIndex.toInt())
                            .setSequence(input.sequence.toInt())
                            .build()
                    )
                    .setScript(ByteString.copyFrom(input.scriptPubKey))
                    .build()
            }

        // Frozen plan. Critical: do NOT replan — the replanner would re-select UTXOs and could
        // produce a different on-chain tx_id, breaking NEAR Intents route tracking.
        val plan =
            Bitcoin.TransactionPlan.newBuilder()
                .setAmount(depositAmount)
                .setAvailableAmount(totalIn)
                .setFee(fee)
                .setChange(changeAmount)
                .setError(SigningError.OK)
                .addAllUtxos(utxos)
                .build()

        val signingInput =
            Bitcoin.SigningInput.newBuilder()
                .setHashType(BitcoinScript.hashTypeForCoin(coinType))
                .setByteFee(1L) // Frozen plan supersedes — the replanner won't run.
                .setUseMaxAmount(false)
                .setAmount(depositAmount)
                .setCoinType(coinType.value())
                .setToAddress(depositAddress)
                .setChangeAddress(changeAddress)
                .setFixedDustThreshold(coinType.getDustThreshold)
                .addAllUtxo(utxos)
                .setPlan(plan)

        // scripts map: keyHash.hex → P2PKH redeem script. Mirrors the native send path.
        inputs.forEach { input ->
            val redeem = BitcoinScript.buildPayToPublicKeyHash(input.keyHash)
            signingInput.putScripts(
                Numeric.toHexStringNoPrefix(input.keyHash),
                ByteString.copyFrom(redeem.data()),
            )
        }

        return signingInput.build().toByteArray()
    }

    /**
     * SwapKit may ship either `PSBT_IN_NON_WITNESS_UTXO` (key 0x00, embedded prev-tx) or
     * `PSBT_IN_WITNESS_UTXO` (key 0x01, compact amount + scriptPubKey). Both surface the same
     * `amount` + `scriptPubKey` pair; whichever ships is accepted.
     */
    private fun resolvePrevUtxo(
        inputMap: Map<String, ByteArray>,
        prevIndex: Long,
        inputIndex: Int,
    ): Pair<Long, ByteArray> {
        inputMap[KEY_NON_WITNESS_UTXO]?.let {
            return parseNonWitnessUtxo(it, prevIndex, inputIndex)
        }
        inputMap[KEY_WITNESS_UTXO]?.let {
            return parseWitnessUtxo(it, inputIndex)
        }
        throw SwapKitLegacyP2PKHSignerException(
            "SwapKit PSBT input #$inputIndex is missing a prev-tx UTXO record"
        )
    }

    /**
     * Parse a `PSBT_IN_NON_WITNESS_UTXO` record: the full previous transaction in legacy
     * (pre-segwit) wire serialization. We extract `outputs[prevIndex]`.
     */
    private fun parseNonWitnessUtxo(
        data: ByteArray,
        prevIndex: Long,
        inputIndex: Int,
    ): Pair<Long, ByteArray> {
        val cursor = PsbtCursor(data)
        cursor.readUInt32LE() // version
        val inCount = cursor.readCompactSize()
        repeat(cursor.asLength(inCount)) {
            cursor.readBytes(32) // prev txid
            cursor.readUInt32LE() // prev index
            val sigLen = cursor.readCompactSize()
            cursor.readBytes(cursor.asLength(sigLen)) // scriptSig
            cursor.readUInt32LE() // sequence
        }
        val outCount = cursor.readCompactSize()
        if (prevIndex >= outCount) {
            throw SwapKitLegacyP2PKHSignerException(
                "SwapKit PSBT input #$inputIndex prev index $prevIndex >= output count $outCount"
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

    /** WITNESS_UTXO value: 8-byte LE amount + varint-prefixed scriptPubKey. */
    private fun parseWitnessUtxo(data: ByteArray, inputIndex: Int): Pair<Long, ByteArray> {
        val cursor = PsbtCursor(data)
        val amount = cursor.readUInt64LE()
        val scriptLen = cursor.readCompactSize()
        val script = cursor.readBytes(cursor.asLength(scriptLen))
        if (!cursor.isAtEnd) {
            throw SwapKitLegacyP2PKHSignerException(
                "SwapKit PSBT input #$inputIndex WITNESS_UTXO has trailing bytes"
            )
        }
        return amount to script
    }

    private data class LegacyP2PKHInput(
        val prevTxIdLE: ByteArray,
        val prevIndex: Long,
        val sequence: Long,
        val amount: Long,
        val scriptPubKey: ByteArray,
        val keyHash: ByteArray,
    )

    private data class LegacyP2PKHOutput(val amount: Long, val scriptPubKey: ByteArray)

    private data class ParsedLegacyTxInput(
        val prevTxIdLE: ByteArray,
        val prevIndex: Long,
        val sequence: Long,
    )

    private data class ParsedLegacyTx(
        val inputs: List<ParsedLegacyTxInput>,
        val outputs: List<LegacyP2PKHOutput>,
    )

    /**
     * Parse the legacy (non-witness) unsigned-tx body BIP-174 stores under global key 0x00. DOGE /
     * BCH / DASH have no segwit, so the body never carries a marker+flag.
     */
    private fun parseLegacyUnsignedTx(data: ByteArray): ParsedLegacyTx {
        val cursor = PsbtCursor(data)
        cursor.readUInt32LE() // version
        val inCount = cursor.readCompactSize()
        val inputs =
            (0L until inCount).map {
                val prevBytes = cursor.readBytes(32) // internal little-endian wire order
                val prevIndex = cursor.readUInt32LE()
                // A PSBT's unsigned tx must carry empty scriptSigs. Reject a non-empty one rather
                // than silently skip it — the frozen plan rebuilds the tx from these parsed fields,
                // so dropped bytes would change the broadcast tx_id the route is tracked by.
                val scriptSigLen = cursor.readCompactSize()
                if (scriptSigLen != 0L) {
                    throw SwapKitLegacyP2PKHSignerException(
                        "SwapKit PSBT unsigned-tx input #$it scriptSig must be empty"
                    )
                }
                val sequence = cursor.readUInt32LE()
                ParsedLegacyTxInput(prevBytes, prevIndex, sequence)
            }
        val outCount = cursor.readCompactSize()
        val outputs =
            (0L until outCount).map {
                val amount = cursor.readUInt64LE()
                val scriptLen = cursor.readCompactSize()
                LegacyP2PKHOutput(amount, cursor.readBytes(cursor.asLength(scriptLen)))
            }
        cursor.readUInt32LE() // locktime
        // The unsigned-tx body must consume exactly; trailing bytes (e.g. a stray BIP-144 witness
        // flag) would be silently dropped from the rebuilt tx, diverging it from the PSBT body.
        if (!cursor.isAtEnd) {
            throw SwapKitLegacyP2PKHSignerException(
                "SwapKit PSBT unsigned-tx body has trailing bytes"
            )
        }
        return ParsedLegacyTx(inputs, outputs)
    }

    /**
     * P2PKH scriptPubKey: 25 bytes — `OP_DUP OP_HASH160 PUSH20 <20-byte hash> OP_EQUALVERIFY
     * OP_CHECKSIG` = `76 a9 14 <20> 88 ac`. Returns the 20-byte hash160. [role] tags the error
     * (`input`/`output`) so non-P2PKH rejections read distinctly in logs.
     */
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
            throw SwapKitLegacyP2PKHSignerException(
                "SwapKit PSBT $role #$index scriptPubKey is not P2PKH: " +
                    Numeric.toHexStringNoPrefix(scriptPubKey)
            )
        }
        return scriptPubKey.copyOfRange(3, 23)
    }

    /**
     * Build a legacy base58-check P2PKH address for [coinType] from a 20-byte hash160. Used to
     * derive the deposit + change addresses WalletCore re-emits as output scripts. Returns null if
     * no version prefix is defined for the coin (caller falls back to the SwapKit `targetAddress`
     * hint).
     */
    private fun legacyAddress(hash: ByteArray): String? {
        val prefix = versionPrefix(coinType) ?: return null
        return Base58.encode(prefix + hash)
    }

    /**
     * Mainnet P2PKH version prefix per chain. Listed inline because [CoinType] doesn't surface the
     * prefix bytes. DOGE `0x1E` (`D…`), BCH `0x00` legacy `1…` (CashAddr derives the same hash),
     * DASH `0x4C` (`X…`).
     */
    private fun versionPrefix(coin: CoinType): ByteArray? =
        when (coin) {
            CoinType.DOGECOIN -> byteArrayOf(0x1E)
            CoinType.BITCOINCASH -> byteArrayOf(0x00)
            CoinType.DASH -> byteArrayOf(0x4C)
            else -> null
        }

    private companion object {
        private const val KEY_NON_WITNESS_UTXO = "00"
        private const val KEY_WITNESS_UTXO = "01"
    }
}
