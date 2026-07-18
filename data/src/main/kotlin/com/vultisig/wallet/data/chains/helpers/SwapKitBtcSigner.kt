package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.utils.Numeric
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import tss.KeysignResponse
import vultisig.keysign.v1.BitcoinInput
import vultisig.keysign.v1.BitcoinOutput
import vultisig.keysign.v1.SignBitcoin
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.Hash
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType

internal class SwapKitBtcSignerException(message: String) : Exception(message)

/**
 * Bridges SwapKit's pre-built BTC PSBT onto Android's existing structured-PSBT signing path. Ported
 * from iOS' `SwapKitBTCSigner` + the compile half of `BitcoinPsbtSigner`. SwapKit's `/v3/swap`
 * returns a base64 BIP-174 PSBT (already base64-decoded into `SwapKitSwapPayloadJson.txPayload`).
 * We decode it into a [SignBitcoin], reuse [UtxoHelper]'s BIP-143 sighashes plus the vault +
 * destination binding, and assemble the signed segwit transaction from the MPC signatures.
 *
 * Scope: P2WPKH + P2SH-P2WPKH inputs, SIGHASH_ALL only — matches [UtxoHelper.computeOurSighashes]
 * and the SwapKit BTC providers observed (NEAR / GARDEN / FLASHNET), which place only the user's
 * UTXOs in the inputs (every input is `is_ours`). PSBT framing primitives live in
 * [SwapKitPsbtParser]; the BIP-144 unsigned-tx body parser stays here.
 *
 * [coinType] is BITCOIN by default and LITECOIN for the LTC route. Litecoin is also native segwit
 * (P2WPKH / P2SH-P2WPKH) so the BIP-143 sighash + segwit serialization are byte-identical; only the
 * vault address / lock-script derivation (used for the change-output binding) differs, and those go
 * through [coinType]. Mirrors iOS, where BTC and LTC share `SwapKitBTCSigner`.
 */
internal class SwapKitBtcSigner(
    private val vaultHexPublicKey: String,
    private val vaultHexChainCode: String,
    private val coinType: CoinType = CoinType.BITCOIN,
) {
    private val utxo = UtxoHelper(coinType, vaultHexPublicKey, vaultHexChainCode)

    /**
     * BIP-143 sighashes (sorted hex) for every `is_ours` input. Delegates to
     * [UtxoHelper.getPreSignedImageHashFromSignBitcoin], pinning the non-change deposit to
     * SwapKit's [targetAddress] and [fromAmount] and the change to the vault — a defense-in-depth
     * binding that refuses to sign a PSBT whose outputs diverge from the quoted deposit.
     */
    fun getPreSignedImageHash(
        psbtBytes: ByteArray,
        targetAddress: String,
        fromAmount: BigInteger,
    ): List<String> {
        val signBitcoin = decodeToSignBitcoin(psbtBytes)
        return utxo.getPreSignedImageHashFromSignBitcoin(
            signBitcoin = signBitcoin,
            expectedToAddress = targetAddress,
            expectedToAmount = fromAmount,
        )
    }

    /** Assemble the signed segwit transaction from the SwapKit PSBT bytes + MPC signatures. */
    fun getSignedTransaction(
        psbtBytes: ByteArray,
        signatures: Map<String, KeysignResponse>,
    ): SignedTransactionResult {
        val signBitcoin = decodeToSignBitcoin(psbtBytes)
        val pubKeyBytes = derivedPublicKeyBytes()
        val derSignatureBySighash = signatures.mapValues { it.value.derSignature }
        verifySignatures(signBitcoin, pubKeyBytes, derSignatureBySighash)
        return compileSignedTransaction(
            signBitcoin = signBitcoin,
            pubKeyBytes = pubKeyBytes,
            derSignatureBySighash = derSignatureBySighash,
        )
    }

    /**
     * Reject any MPC signature that does not verify against the derived vault pubkey over the
     * sighash it is keyed by — the same gate [UtxoHelper.getSignedTransaction] applies before it
     * compiles. These signatures can arrive from the relay (a peer's completed keysign recovered on
     * failure), so verify them cryptographically here instead of trusting the source: a mismatched
     * signature is caught locally rather than spliced into a doomed broadcast. Kept out of the pure
     * [compileSignedTransaction] so its serialization stays headless-testable without WalletCore.
     */
    private fun verifySignatures(
        signBitcoin: SignBitcoin,
        pubKeyBytes: ByteArray,
        derSignatureBySighash: Map<String, String>,
    ) {
        val publicKey = PublicKey(pubKeyBytes, PublicKeyType.SECP256K1)
        for (sighash in utxo.computeOurSighashes(signBitcoin)) {
            val key = Numeric.toHexStringNoPrefix(sighash)
            val derSignature =
                derSignatureBySighash[key]
                    ?: throw SwapKitBtcSignerException(
                        "Missing signature for sighash ${key.take(16)}…"
                    )
            if (!publicKey.verifyAsDER(Numeric.hexStringToByteArray(derSignature), sighash)) {
                throw SwapKitBtcSignerException(
                    "SwapKit BTC signature for sighash ${key.take(16)}… does not verify against the " +
                        "vault key"
                )
            }
        }
    }

    /**
     * Decode a BIP-174 PSBT blob into the structured [SignBitcoin]. Every input is `is_ours=true`
     * (SwapKit only puts the user's UTXOs in the inputs). An output is flagged `is_change=true` iff
     * its scriptPubKey equals the vault's own lock script, so the reused binding in
     * [UtxoHelper.getPreSignedImageHashFromSignBitcoin] treats the deposit as the payment and the
     * vault output as change.
     */
    internal fun decodeToSignBitcoin(psbtBytes: ByteArray): SignBitcoin {
        val vaultAddr = vaultAddress()
        return decode(psbtBytes, vaultAddr, vaultLockScriptHex(vaultAddr))
    }

    /**
     * Core decode. [vaultLockScriptHex] / [vaultAddress] drive `is_change` marking; pass both null
     * to skip it (the resulting [SignBitcoin] is enough for the pure BIP-143 sighash path, which
     * ignores outputs' change flags). Splitting the vault lookup out keeps the sighash transcoding
     * testable without the WalletCore JNI.
     */
    internal fun decode(
        psbtBytes: ByteArray,
        vaultAddress: String?,
        vaultLockScriptHex: String?,
    ): SignBitcoin {
        if (psbtBytes.isEmpty()) {
            throw SwapKitBtcSignerException("SwapKit BTC payload has no PSBT bytes")
        }
        val header = SwapKitPsbtParser.parseFramingHeader(psbtBytes)
        val parsedTx = parseUnsignedTx(header.unsignedTxBytes)
        val cursor = header.cursor
        val inputMaps = parsedTx.inputs.map { cursor.readMap() }
        // Output maps are always parsed for spec compliance / forward-compat even though no field
        // is
        // currently read from them.
        parsedTx.outputs.forEach { cursor.readMap() }

        val inputs =
            parsedTx.inputs.mapIndexed { index, txIn ->
                makeBitcoinInput(inputMaps[index], index, txIn)
            }
        val outputs =
            parsedTx.outputs.map { txOut ->
                val scriptHex = Numeric.toHexStringNoPrefix(txOut.scriptPubKey)
                val isChange =
                    vaultLockScriptHex != null &&
                        scriptHex.equals(vaultLockScriptHex, ignoreCase = true)
                BitcoinOutput(
                    amount = txOut.amount,
                    address = if (isChange) vaultAddress.orEmpty() else "",
                    scriptPubKey = scriptHex,
                    isChange = isChange,
                )
            }
        return SignBitcoin(
            version = parsedTx.version.toUInt(),
            locktime = parsedTx.locktime.toUInt(),
            inputs = inputs,
            outputs = outputs,
        )
    }

    private fun makeBitcoinInput(
        inputMap: Map<String, ByteArray>,
        index: Int,
        txIn: ParsedTxInput,
    ): BitcoinInput {
        val witnessUtxo =
            inputMap[KEY_WITNESS_UTXO]
                ?: throw SwapKitBtcSignerException(
                    "SwapKit BTC PSBT input #$index is missing PSBT_IN_WITNESS_UTXO"
                )
        val (amount, scriptPubKey) = parseWitnessUtxo(witnessUtxo, index)
        val (scriptType, redeemScript) = classifyScript(scriptPubKey, inputMap, index)
        val sighashType =
            inputMap[KEY_SIGHASH_TYPE]
                ?.let { bytes ->
                    if (bytes.size != 4) {
                        throw SwapKitBtcSignerException(
                            "SwapKit BTC PSBT input #$index sighash-type record has ${bytes.size} " +
                                "bytes, expected 4"
                        )
                    }
                    ((bytes[0].toLong() and 0xFF) or
                            ((bytes[1].toLong() and 0xFF) shl 8) or
                            ((bytes[2].toLong() and 0xFF) shl 16) or
                            ((bytes[3].toLong() and 0xFF) shl 24))
                        .toUInt()
                }
                // An explicit sighash of 0 is the default (SIGHASH_ALL); drop to null so the
                // downstream `?: SIGHASH_ALL` applies — matches iOS rather than rejecting the
                // input.
                ?.takeIf { it != 0u }
        return BitcoinInput(
            hash = txIn.prevTxIdDisplay,
            index = txIn.prevIndex.toUInt(),
            amount = amount,
            scriptPubKey = Numeric.toHexStringNoPrefix(scriptPubKey),
            scriptType = scriptType,
            isOurs = true,
            redeemScript = redeemScript,
            sequence = txIn.sequence.toUInt(),
            sighashType = sighashType,
        )
    }

    /** WITNESS_UTXO value: 8-byte LE amount + varint-prefixed scriptPubKey. */
    private fun parseWitnessUtxo(bytes: ByteArray, index: Int): Pair<Long, ByteArray> {
        val cursor = PsbtCursor(bytes)
        val amount = cursor.readUInt64LE()
        val scriptLen = cursor.readCompactSize()
        val script = cursor.readBytes(cursor.asLength(scriptLen))
        if (!cursor.isAtEnd) {
            throw SwapKitBtcSignerException(
                "SwapKit BTC PSBT input #$index WITNESS_UTXO has trailing bytes"
            )
        }
        return amount to script
    }

    /** Classify the input scriptPubKey. P2WPKH and (with a redeem script) P2SH-P2WPKH only. */
    private fun classifyScript(
        scriptPubKey: ByteArray,
        inputMap: Map<String, ByteArray>,
        index: Int,
    ): Pair<String, String?> {
        // P2WPKH: 0x00 0x14 <20-byte hash> (22 bytes).
        if (
            scriptPubKey.size == 22 &&
                scriptPubKey[0] == 0x00.toByte() &&
                scriptPubKey[1] == 0x14.toByte()
        ) {
            return "p2wpkh" to null
        }
        // P2SH: 0xa9 0x14 <20-byte hash> 0x87 (23 bytes); promotion to P2SH-P2WPKH needs the
        // redeem.
        if (
            scriptPubKey.size == 23 &&
                scriptPubKey[0] == 0xa9.toByte() &&
                scriptPubKey[1] == 0x14.toByte() &&
                scriptPubKey[22] == 0x87.toByte()
        ) {
            val redeem =
                inputMap[KEY_REDEEM_SCRIPT]
                    ?: throw SwapKitBtcSignerException(
                        "SwapKit BTC PSBT P2SH input #$index missing redeem script"
                    )
            if (!(redeem.size == 22 && redeem[0] == 0x00.toByte() && redeem[1] == 0x14.toByte())) {
                throw SwapKitBtcSignerException(
                    "SwapKit BTC PSBT P2SH redeem script on input #$index is not P2SH-P2WPKH"
                )
            }
            // The redeem script must hash to the 20-byte HASH160 the scriptPubKey commits to
            // (0xa9 0x14 <hash> 0x87); without this, a mismatched pair passes decode and only fails
            // after signing/broadcast.
            val expectedHash = scriptPubKey.copyOfRange(2, 22)
            if (!Hash.sha256RIPEMD(redeem).contentEquals(expectedHash)) {
                throw SwapKitBtcSignerException(
                    "SwapKit BTC PSBT P2SH redeem script on input #$index does not bind to its " +
                        "scriptPubKey via HASH160"
                )
            }
            return "p2sh-p2wpkh" to Numeric.toHexStringNoPrefix(redeem)
        }
        throw SwapKitBtcSignerException(
            "SwapKit BTC PSBT input #$index scriptPubKey is not P2WPKH or P2SH-P2WPKH: " +
                Numeric.toHexStringNoPrefix(scriptPubKey)
        )
    }

    private data class ParsedTxInput(
        val prevTxIdDisplay: String,
        val prevIndex: Long,
        val sequence: Long,
    )

    private data class ParsedTxOutput(val amount: Long, val scriptPubKey: ByteArray)

    private data class ParsedTx(
        val version: Long,
        val locktime: Long,
        val inputs: List<ParsedTxInput>,
        val outputs: List<ParsedTxOutput>,
    )

    /**
     * Parse the legacy (non-witness) unsigned-tx body that BIP-174 stores under global key 0x00.
     */
    private fun parseUnsignedTx(data: ByteArray): ParsedTx {
        val cursor = PsbtCursor(data)
        val version = cursor.readUInt32LE()
        val inputCount = cursor.readCompactSize()
        val inputs =
            (0L until inputCount).map {
                val prevBytes = cursor.readBytes(32)
                // Outpoint hash is internal (little-endian) on the wire; SignBitcoin/BIP-143 expect
                // big-endian display order — reverse once here.
                val prevTxIdDisplay = Numeric.toHexStringNoPrefix(prevBytes.reversedArray())
                val prevIndex = cursor.readUInt32LE()
                val scriptSigLen = cursor.readCompactSize()
                // A PSBT's unsigned tx must carry empty scriptSigs. Reject a non-empty one rather
                // than narrow an untrusted compactSize with toInt() (which could wrap past
                // Int.MAX_VALUE and desync the parser); asLength consumes it safely.
                if (scriptSigLen != 0L) {
                    throw SwapKitBtcSignerException(
                        "SwapKit BTC PSBT unsigned-tx input #$it scriptSig must be empty"
                    )
                }
                cursor.readBytes(cursor.asLength(scriptSigLen))
                val sequence = cursor.readUInt32LE()
                ParsedTxInput(prevTxIdDisplay, prevIndex, sequence)
            }
        val outputCount = cursor.readCompactSize()
        val outputs =
            (0L until outputCount).map {
                val amount = cursor.readUInt64LE()
                val scriptLen = cursor.readCompactSize()
                ParsedTxOutput(amount, cursor.readBytes(cursor.asLength(scriptLen)))
            }
        val locktime = cursor.readUInt32LE()
        // The unsigned-tx body must consume exactly; trailing bytes mean a malformed serialization
        // (e.g. a stray BIP-144 witness flag) whose dropped data would silently change what's
        // signed.
        if (!cursor.isAtEnd) {
            throw SwapKitBtcSignerException(
                "SwapKit BTC PSBT unsigned-tx body has ${data.size - cursor.offset} trailing bytes"
            )
        }
        return ParsedTx(version, locktime, inputs, outputs)
    }

    /**
     * Assemble the signed segwit tx. [derSignatureBySighash] maps each sighash hex (the message the
     * MPC engine signed) to its DER signature hex; [pubKeyBytes] is the derived vault pubkey placed
     * in every witness. Takes plain bytes/strings (not the `compileOnly` TSS type) so the broadcast
     * serialization can be pinned by a headless unit test.
     */
    internal fun compileSignedTransaction(
        signBitcoin: SignBitcoin,
        pubKeyBytes: ByteArray,
        derSignatureBySighash: Map<String, String>,
    ): SignedTransactionResult {
        val inputs = signBitcoin.inputs.filterNotNull()
        // SwapKit puts only the user's UTXOs in the PSBT, so `decode` marks every input is_ours and
        // this holds by construction. Guard it anyway: a non-ours input would get an empty witness
        // (a non-final tx), so reject mixed-ownership PSBTs loudly rather than broadcast garbage.
        require(inputs.all { it.isOurs }) {
            "SwapKitBtcSigner expects every input to be is_ours; mixed-ownership PSBTs are unsupported"
        }
        // Recompute per-input sighashes in input order (is_ours only) so each maps to the MPC
        // signature keyed by its sighash hex — the same matching UtxoHelper uses.
        val sighashes = utxo.computeOurSighashes(signBitcoin)
        val witnesses = Array(inputs.size) { emptyList<ByteArray>() }
        var sighashIndex = 0
        inputs.forEachIndexed { i, input ->
            if (!input.isOurs) return@forEachIndexed
            val sighash = sighashes[sighashIndex++]
            val key = Numeric.toHexStringNoPrefix(sighash)
            val derSignature =
                derSignatureBySighash[key]
                    ?: throw SwapKitBtcSignerException(
                        "Missing signature for sighash ${key.take(16)}…"
                    )
            val sigPlusFlag =
                Numeric.hexStringToByteArray(derSignature) + byteArrayOf(sighashFlagByte(input))
            witnesses[i] = listOf(sigPlusFlag, pubKeyBytes)
        }
        val rawTx = serializeSegwitTransaction(signBitcoin, witnesses)
        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(rawTx),
            transactionHash = txid(signBitcoin),
        )
    }

    private fun sighashFlagByte(input: BitcoinInput): Byte =
        ((input.sighashType ?: SIGHASH_ALL).toInt() and 0xFF).toByte()

    /** BIP-141 segwit serialization: marker+flag, scriptSig per input, witness stacks. */
    private fun serializeSegwitTransaction(
        signBitcoin: SignBitcoin,
        witnesses: Array<List<ByteArray>>,
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(uint32LE(signBitcoin.version.toInt()))
        buf.write(0x00) // marker
        buf.write(0x01) // flag
        buf.write(serializeInputsAndOutputs(signBitcoin))
        witnesses.forEach { stack ->
            buf.write(varInt(stack.size.toLong()))
            stack.forEach { item ->
                buf.write(varInt(item.size.toLong()))
                buf.write(item)
            }
        }
        buf.write(uint32LE(signBitcoin.locktime.toInt()))
        return buf.toByteArray()
    }

    /** Shared body (counts + per-input outpoint/scriptSig/sequence + per-output value/script). */
    private fun serializeInputsAndOutputs(signBitcoin: SignBitcoin): ByteArray {
        val inputs = signBitcoin.inputs.filterNotNull()
        val outputs = signBitcoin.outputs.filterNotNull()
        val buf = ByteArrayOutputStream()
        buf.write(varInt(inputs.size.toLong()))
        inputs.forEach { input ->
            buf.write(serializeOutpoint(input.hash, input.index.toInt()))
            val scriptSig = scriptSig(input)
            buf.write(varInt(scriptSig.size.toLong()))
            buf.write(scriptSig)
            buf.write(uint32LE((input.sequence ?: 0xFFFFFFFFu).toInt()))
        }
        buf.write(varInt(outputs.size.toLong()))
        outputs.forEach { output ->
            buf.write(uint64LE(output.amount))
            val script = Numeric.hexStringToByteArray(output.scriptPubKey)
            buf.write(varInt(script.size.toLong()))
            buf.write(script)
        }
        return buf.toByteArray()
    }

    /** Empty for native segwit; pushes the redeem script for P2SH-P2WPKH. */
    private fun scriptSig(input: BitcoinInput): ByteArray {
        if (input.scriptType.lowercase() != "p2sh-p2wpkh") return ByteArray(0)
        val redeemHex =
            input.redeemScript
                ?: throw SwapKitBtcSignerException(
                    "P2SH-P2WPKH input ${input.hash}:${input.index} missing redeem script"
                )
        val redeem = Numeric.hexStringToByteArray(redeemHex)
        return byteArrayOf((redeem.size and 0xFF).toByte()) + redeem
    }

    /** txid = reverse(double-sha256(non-witness serialization)), Bitcoin display order. */
    private fun txid(signBitcoin: SignBitcoin): String {
        val buf = ByteArrayOutputStream()
        buf.write(uint32LE(signBitcoin.version.toInt()))
        buf.write(serializeInputsAndOutputs(signBitcoin))
        buf.write(uint32LE(signBitcoin.locktime.toInt()))
        return Numeric.toHexStringNoPrefix(sha256d(buf.toByteArray()).reversedArray())
    }

    private fun serializeOutpoint(prevTxIdDisplay: String, index: Int): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(Numeric.hexStringToByteArray(prevTxIdDisplay).reversedArray())
        buf.write(uint32LE(index))
        return buf.toByteArray()
    }

    private fun derivedPublicKeyBytes(): ByteArray {
        val derivedHex =
            PublicKeyHelper.getDerivedPublicKey(
                vaultHexPublicKey,
                vaultHexChainCode,
                coinType.derivationPath(),
            )
        val bytes = Numeric.hexStringToByteArray(derivedHex)
        require(PublicKey.isValid(bytes, PublicKeyType.SECP256K1)) {
            "Invalid derived vault public key for SwapKit BTC signing"
        }
        return bytes
    }

    private fun vaultAddress(): String {
        val derivedHex =
            PublicKeyHelper.getDerivedPublicKey(
                vaultHexPublicKey,
                vaultHexChainCode,
                coinType.derivationPath(),
            )
        val publicKey = PublicKey(Numeric.hexStringToByteArray(derivedHex), PublicKeyType.SECP256K1)
        return coinType.deriveAddressFromPublicKey(publicKey)
    }

    private fun vaultLockScriptHex(address: String): String =
        Numeric.toHexStringNoPrefix(BitcoinScript.lockScriptForAddress(address, coinType).data())

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

    private fun sha256d(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(digest.digest(data))
    }

    private companion object {
        private const val SIGHASH_ALL: UInt = 1u
        private const val KEY_WITNESS_UTXO = "01"
        private const val KEY_SIGHASH_TYPE = "03"
        private const val KEY_REDEEM_SCRIPT = "04"
    }
}
