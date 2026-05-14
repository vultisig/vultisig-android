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
import wallet.core.jni.Hash
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
        validateAmounts(inputs, outputs)
        val available = inputs.sumOf { it.amount }
        val change = outputs.filter { it.isChange }.sumOf { it.amount }
        val sent = outputs.filterNot { it.isChange }.sumOf { it.amount }
        val fee = available - (sent + change)
        require(fee >= 0) {
            "Invalid SignBitcoin payload: outputs ($sent + change $change) exceed inputs ($available)"
        }
        return Bitcoin.TransactionPlan.newBuilder()
            .setAmount(sent)
            .setAvailableAmount(available)
            .setFee(fee)
            .setChange(change)
            .setError(SigningError.OK)
            .build()
    }

    /**
     * Rejects PSBT payloads carrying negative `int64` amounts. The proto schema lets `BitcoinInput`
     * and `BitcoinOutput` carry a signed amount, but every downstream consumer treats the value as
     * an unsigned satoshi count — `uint64LE` in [buildBip143Preimage] / [serializeOutputs] would
     * encode a negative value's two's complement as ~2^64, producing a sighash committing to an
     * amount no on-chain UTXO has. The `require(fee >= 0)` guard in
     * [getBitcoinTransactionPlanFromSignBitcoin] only catches the aggregate going negative, not a
     * payload that mixes positive and negative inputs balancing out.
     */
    private fun validateAmounts(inputs: List<BitcoinInput>, outputs: List<BitcoinOutput>) {
        inputs.forEach { input ->
            require(input.amount >= 0) {
                "PSBT input ${input.hash}:${input.index} has negative amount ${input.amount}"
            }
        }
        outputs.forEach { output ->
            require(output.amount >= 0) {
                "PSBT output to '${output.address}' has negative amount ${output.amount}"
            }
        }
    }

    /**
     * Returns the sorted hex-encoded BIP-143 sighashes for every input owned by this device in the
     * structured PSBT payload, ready to be dispatched to the MPC engine. Bypasses WalletCore tx
     * planning entirely. Only P2WPKH and P2SH-P2WPKH inputs are supported; P2TR is rejected.
     *
     * Bitcoin-only: the witness-program shapes parsed here (`0x00 0x14 <20-byte hash>`) are
     * meaningful only for the Bitcoin chain. UTXO siblings (BCH, Doge, LTC, Dash, Zcash) use legacy
     * P2PKH and must not route through this path.
     */
    fun getPreSignedImageHashFromSignBitcoin(signBitcoin: SignBitcoin): List<String> {
        require(coinType == CoinType.BITCOIN) {
            "SignBitcoin PSBT co-signing is only supported on Bitcoin, got $coinType"
        }
        verifyOwnership(signBitcoin)
        return computeOurSighashes(signBitcoin).map { Numeric.toHexStringNoPrefix(it) }.sorted()
    }

    /**
     * Defense-in-depth check that ties the PSBT to the vault before signing.
     *
     * Inputs: every input flagged `is_ours=true` by the dApp must redeem to the vault's
     * HASH160(pubkey). The wallet only ever signs for its own derivation path; a lying dApp that
     * toggles `is_ours` on an unrelated input gets a hard failure instead of a usable signature.
     *
     * Outputs: any output flagged `is_change=true` must decode to this vault's own address. Without
     * this binding, a malicious initiator can flag an attacker output `is_change=true` to skew the
     * change/fee totals fed into [getBitcoinTransactionPlanFromSignBitcoin] (mirroring the Windows
     * companion which derives `isChange` from `outputAddress === senderAddress`).
     */
    private fun verifyOwnership(signBitcoin: SignBitcoin) {
        val expectedPubKeyHash = deriveExpectedPubKeyHash()
        signBitcoin.inputs.filterNotNull().forEach { input ->
            if (!input.isOurs) return@forEach
            val actual = extractWitnessPubKeyHash(input)
            require(actual.contentEquals(expectedPubKeyHash)) {
                "PSBT input ${input.hash}:${input.index} marked is_ours=true but its witness " +
                    "pubkey hash does not match this vault's derived key"
            }
        }
        val vaultAddress = deriveVaultAddress()
        signBitcoin.outputs.filterNotNull().forEach { output ->
            if (!output.isChange) return@forEach
            require(output.address == vaultAddress) {
                "PSBT output marked is_change=true does not belong to this vault " +
                    "(address='${output.address}', expected='$vaultAddress')"
            }
        }
    }

    internal fun computeOurSighashes(signBitcoin: SignBitcoin): List<ByteArray> {
        val inputs = signBitcoin.inputs.filterNotNull()
        val outputs = signBitcoin.outputs.filterNotNull()
        validateAmounts(inputs, outputs)
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
            // hashPrevouts/hashSequence/hashOutputs above are computed with SIGHASH_ALL
            // semantics. SIGHASH_NONE/SIGHASH_SINGLE/SIGHASH_ANYONECANPAY would require
            // per-input recomputation per BIP-143; reject them here rather than emit a
            // sighash the verifier would reject.
            require(sighashFlag == SIGHASH_ALL) {
                "Unsupported sighash type 0x${sighashFlag.toString(16)} for PSBT input " +
                    "${input.hash}:${input.index}; only SIGHASH_ALL is supported"
            }
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

    private fun deriveVaultPublicKey(): PublicKey {
        val derivedPublicKey =
            PublicKeyHelper.getDerivedPublicKey(
                vaultHexPublicKey,
                vaultHexChainCode,
                coinType.derivationPath(),
            )
        return PublicKey(Numeric.hexStringToByteArray(derivedPublicKey), PublicKeyType.SECP256K1)
    }

    private fun deriveVaultAddress(): String =
        coinType.deriveAddressFromPublicKey(deriveVaultPublicKey())

    private fun deriveExpectedPubKeyHash(): ByteArray =
        BitcoinScript.lockScriptForAddress(deriveVaultAddress(), coinType)
            .matchPayToWitnessPublicKeyHash()

    private fun extractWitnessPubKeyHash(input: BitcoinInput): ByteArray =
        when (input.scriptType.lowercase()) {
            "p2wpkh" -> parseWitnessProgram(input.scriptPubKey)
            "p2sh-p2wpkh" -> {
                val redeemScriptHex =
                    input.redeemScript
                        ?: error(
                            "redeem_script required for P2SH-P2WPKH input ${input.hash}:${input.index}"
                        )
                verifyP2shCommitment(input, redeemScriptHex)
                parseWitnessProgram(redeemScriptHex)
            }
            "p2tr" ->
                error(
                    "P2TR (Taproot) signing is not supported yet for input ${input.hash}:${input.index}"
                )
            else -> error("Unsupported script_type ${input.scriptType}")
        }

    /**
     * Verifies the redeem script actually binds to the on-chain P2SH commitment by checking
     * `HASH160(redeemScript) == scriptPubKey[2..22]`. Without this, any redeem script shaped `0x00
     * 0x14 <20-byte hash>` would be accepted regardless of whether it spends the UTXO the wallet is
     * supposed to be signing for — [verifyOwnership] alone compares against the redeem-script hash,
     * not the on-chain commitment.
     */
    private fun verifyP2shCommitment(input: BitcoinInput, redeemScriptHex: String) {
        val scriptPubKey = Numeric.hexStringToByteArray(input.scriptPubKey)
        require(
            scriptPubKey.size == P2SH_SCRIPT_LEN &&
                scriptPubKey[0] == 0xa9.toByte() &&
                scriptPubKey[1] == 0x14.toByte() &&
                scriptPubKey[P2SH_SCRIPT_LEN - 1] == 0x87.toByte()
        ) {
            "PSBT input ${input.hash}:${input.index} script_pub_key is not a valid P2SH script " +
                "(expected OP_HASH160 <20-byte hash> OP_EQUAL)"
        }
        val expectedHash = scriptPubKey.copyOfRange(2, 22)
        val actualHash = Hash.sha256RIPEMD(Numeric.hexStringToByteArray(redeemScriptHex))
        require(actualHash.contentEquals(expectedHash)) {
            "PSBT input ${input.hash}:${input.index} redeem_script does not bind to its " +
                "script_pub_key via HASH160"
        }
    }

    private fun parseWitnessProgram(hex: String): ByteArray {
        val bytes = Numeric.hexStringToByteArray(hex)
        require(
            bytes.size == WITNESS_V0_PROGRAM_LEN &&
                bytes[0] == 0x00.toByte() &&
                bytes[1] == 0x14.toByte()
        ) {
            "Expected v0 witness program 0x0014<20-byte hash>, got $hex"
        }
        return bytes.copyOfRange(2, WITNESS_V0_PROGRAM_LEN)
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
// Length of a v0 witness program serialized as `0x00 0x14 <20-byte hash>` (the program, not the
// hash itself).
private const val WITNESS_V0_PROGRAM_LEN: Int = 22
// Length of a P2SH scriptPubKey: OP_HASH160 (1) + push20 (1) + 20-byte hash + OP_EQUAL (1).
private const val P2SH_SCRIPT_LEN: Int = 23
