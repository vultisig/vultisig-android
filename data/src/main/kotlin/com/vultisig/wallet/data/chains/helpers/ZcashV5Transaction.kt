package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.utils.Numeric
import java.io.ByteArrayOutputStream
import org.bouncycastle.crypto.digests.Blake2bDigest
import wallet.core.jni.BitcoinScript
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Bitcoin

/**
 * Transparent-only Zcash v5 transactions (ZIP-225 format, ZIP-244 digests) built from the
 * [Bitcoin.SigningInput] WalletCore planned, in WalletCore's input and output order.
 */
internal object ZcashV5Transaction {

    /**
     * The ZIP-244 signature digest of every input of [inputData], in input order. [lockScript]
     * turns an address into its scriptPubKey (WalletCore's, unless a JVM test supplies its own).
     */
    fun preImageHashes(
        inputData: ByteArray,
        lockScript: (String) -> ByteArray = ::zcashLockScript,
    ): List<ByteArray> {
        val tx = build(inputData, lockScript)
        return tx.inputs.indices.map(tx::sighash)
    }

    /**
     * The signed v5 transaction for [inputData]. [derSignatures] holds one already-verified DER
     * signature per input, in input order, each made by the 33-byte compressed [publicKey]. The
     * transaction hash is the ZIP-244 txid in display order, which is not `sha256d(raw)`.
     */
    fun compile(
        inputData: ByteArray,
        derSignatures: List<ByteArray>,
        publicKey: ByteArray,
        lockScript: (String) -> ByteArray = ::zcashLockScript,
    ): SignedTransactionResult {
        val tx = build(inputData, lockScript)
        check(derSignatures.size == tx.inputs.size) {
            "Zcash v5 compile needs ${tx.inputs.size} signatures, got ${derSignatures.size}"
        }
        val scriptSigs =
            derSignatures.map { der ->
                // Both pushes are under OP_PUSHDATA1, where the push opcode equals the length.
                bytes {
                    writeCompactBytes(der + SIGHASH_ALL.toByte())
                    writeCompactBytes(publicKey)
                }
            }
        return SignedTransactionResult(
            rawTransaction = Numeric.toHexStringNoPrefix(tx.serialize(scriptSigs)),
            transactionHash = Numeric.toHexStringNoPrefix(tx.txid().reversedArray()),
        )
    }

    private fun zcashLockScript(address: String): ByteArray =
        BitcoinScript.lockScriptForAddress(address, CoinType.ZCASH).data()

    private fun build(inputData: ByteArray, lockScript: (String) -> ByteArray): Tx {
        val input = Bitcoin.SigningInput.parseFrom(inputData)
        val plan = input.plan
        val branchId = plan.branchId.toByteArray()
        require(branchId.size == BRANCH_ID_SIZE) {
            "Zcash consensus branch id must be $BRANCH_ID_SIZE bytes, got ${branchId.size}"
        }
        val inputs =
            plan.utxosList.map { utxo ->
                val script = utxo.script.toByteArray()
                require(isP2pkh(script)) {
                    "Zcash v5 signing supports transparent P2PKH inputs only, got script " +
                        Numeric.toHexStringNoPrefix(script)
                }
                Input(
                    prevout =
                        bytes {
                            write(utxo.outPoint.hash.toByteArray())
                            writeU32(utxo.outPoint.index)
                        },
                    amount = utxo.amount,
                    script = script,
                    sequence = utxo.outPoint.sequence,
                )
            }
        require(inputs.isNotEmpty()) { "Zcash v5 transaction plan has no inputs" }
        val outputs = buildList {
            add(Output(plan.amount, outputScript(input.toAddress, lockScript)))
            if (plan.change > 0) {
                add(Output(plan.change, outputScript(input.changeAddress, lockScript)))
            }
            val memo = plan.outputOpReturn.toByteArray()
            if (memo.isNotEmpty()) add(Output(0, opReturnScript(memo)))
        }
        return Tx(branchId, input.lockTime, inputs, outputs)
    }

    /** WalletCore yields an empty script for an address it cannot parse; never pay to that. */
    private fun outputScript(address: String, lockScript: (String) -> ByteArray): ByteArray =
        lockScript(address).also {
            require(it.isNotEmpty()) { "Invalid Zcash output address '$address': no lock script" }
        }

    private fun isP2pkh(script: ByteArray): Boolean =
        script.size == P2PKH_SCRIPT_SIZE &&
            script.copyOfRange(0, P2PKH_PREFIX.size).contentEquals(P2PKH_PREFIX) &&
            script
                .copyOfRange(P2PKH_SCRIPT_SIZE - P2PKH_SUFFIX.size, P2PKH_SCRIPT_SIZE)
                .contentEquals(P2PKH_SUFFIX)

    /** WalletCore's `Script::buildOpReturnScript`: a single push, refused above 80 bytes. */
    private fun opReturnScript(memo: ByteArray): ByteArray {
        require(memo.size <= MAX_OP_RETURN_SIZE) {
            "Zcash OP_RETURN memo is ${memo.size} bytes; the limit is $MAX_OP_RETURN_SIZE"
        }
        return bytes {
            write(OP_RETURN)
            if (memo.size >= OP_PUSHDATA1) write(OP_PUSHDATA1)
            write(memo.size)
            write(memo)
        }
    }

    private class Input(
        val prevout: ByteArray,
        val amount: Long,
        val script: ByteArray,
        val sequence: Int,
    )

    private class Output(val amount: Long, val script: ByteArray)

    private class Tx(
        val branchId: ByteArray,
        val lockTime: Int,
        val inputs: List<Input>,
        val outputs: List<Output>,
    ) {
        private val headerDigest = blake2b(PERSONAL_HEADERS, bytes { writeHeader(this@Tx) })
        private val prevoutsDigest =
            blake2b(PERSONAL_PREVOUTS, bytes { inputs.forEach { write(it.prevout) } })
        private val sequencesDigest =
            blake2b(PERSONAL_SEQUENCES, bytes { inputs.forEach { writeU32(it.sequence) } })
        private val outputsDigest =
            blake2b(
                PERSONAL_OUTPUTS,
                bytes {
                    outputs.forEach {
                        writeI64(it.amount)
                        writeCompactBytes(it.script)
                    }
                },
            )
        private val amountsDigest =
            blake2b(PERSONAL_AMOUNTS, bytes { inputs.forEach { writeI64(it.amount) } })
        private val scriptsDigest =
            blake2b(PERSONAL_SCRIPTS, bytes { inputs.forEach { writeCompactBytes(it.script) } })
        private val txHashPersonal = TX_HASH_PERSONAL_PREFIX + branchId

        fun sighash(index: Int): ByteArray {
            val input = inputs[index]
            val txinDigest =
                blake2b(
                    PERSONAL_TXIN,
                    bytes {
                        write(input.prevout)
                        writeI64(input.amount)
                        writeCompactBytes(input.script)
                        writeU32(input.sequence)
                    },
                )
            val transparentDigest =
                blake2b(
                    PERSONAL_TRANSPARENT,
                    bytes {
                        write(SIGHASH_ALL)
                        write(prevoutsDigest)
                        write(amountsDigest)
                        write(scriptsDigest)
                        write(sequencesDigest)
                        write(outputsDigest)
                        write(txinDigest)
                    },
                )
            return txDigest(transparentDigest)
        }

        fun txid(): ByteArray =
            txDigest(
                blake2b(PERSONAL_TRANSPARENT, prevoutsDigest + sequencesDigest + outputsDigest)
            )

        fun serialize(scriptSigs: List<ByteArray>): ByteArray = bytes {
            writeHeader(this@Tx)
            writeCompactSize(inputs.size)
            inputs.zip(scriptSigs).forEach { (input, scriptSig) ->
                write(input.prevout)
                writeCompactBytes(scriptSig)
                writeU32(input.sequence)
            }
            writeCompactSize(outputs.size)
            outputs.forEach {
                writeI64(it.amount)
                writeCompactBytes(it.script)
            }
            // nSpendsSapling, nOutputsSapling, nActionsOrchard
            write(ByteArray(3))
        }

        private fun txDigest(transparentDigest: ByteArray): ByteArray =
            blake2b(
                txHashPersonal,
                headerDigest + transparentDigest + EMPTY_SAPLING_DIGEST + EMPTY_ORCHARD_DIGEST,
            )
    }

    private fun ByteArrayOutputStream.writeHeader(tx: Tx) {
        writeU32(TX_VERSION_HEADER)
        writeU32(VERSION_GROUP_ID)
        write(tx.branchId)
        writeU32(tx.lockTime)
        writeU32(EXPIRY_HEIGHT)
    }

    private fun blake2b(personalization: ByteArray, data: ByteArray): ByteArray {
        val digest = Blake2bDigest(null, DIGEST_SIZE, null, personalization)
        digest.update(data, 0, data.size)
        return ByteArray(DIGEST_SIZE).also { digest.doFinal(it, 0) }
    }

    private inline fun bytes(block: ByteArrayOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().apply(block).toByteArray()

    private fun ByteArrayOutputStream.writeU32(value: Int) {
        for (shift in 0 until Int.SIZE_BITS step Byte.SIZE_BITS) write(value ushr shift)
    }

    private fun ByteArrayOutputStream.writeI64(value: Long) {
        for (shift in 0 until Long.SIZE_BITS step Byte.SIZE_BITS) write((value ushr shift).toInt())
    }

    private fun ByteArrayOutputStream.writeCompactSize(value: Int) {
        when {
            value < COMPACT_SIZE_U16 -> write(value)
            value <= U16_MAX -> {
                write(COMPACT_SIZE_U16)
                write(value)
                write(value ushr Byte.SIZE_BITS)
            }
            else -> {
                write(COMPACT_SIZE_U32)
                writeU32(value)
            }
        }
    }

    private fun ByteArrayOutputStream.writeCompactBytes(data: ByteArray) {
        writeCompactSize(data.size)
        write(data)
    }

    private fun personalization(tag: String): ByteArray = tag.toByteArray(Charsets.US_ASCII)

    private const val DIGEST_SIZE = 32
    private const val BRANCH_ID_SIZE = 4
    private const val TX_VERSION_HEADER = 0x80000005.toInt()
    private const val VERSION_GROUP_ID = 0x26A7270A
    private const val EXPIRY_HEIGHT = 0
    private const val SIGHASH_ALL = 0x01
    private const val OP_RETURN = 0x6a
    private const val OP_PUSHDATA1 = 0x4c
    private const val MAX_OP_RETURN_SIZE = 80
    private const val P2PKH_SCRIPT_SIZE = 25
    private const val COMPACT_SIZE_U16 = 0xFD
    private const val COMPACT_SIZE_U32 = 0xFE
    private const val U16_MAX = 0xFFFF

    private val P2PKH_PREFIX = byteArrayOf(0x76, 0xa9.toByte(), 0x14)
    private val P2PKH_SUFFIX = byteArrayOf(0x88.toByte(), 0xac.toByte())

    private val PERSONAL_HEADERS = personalization("ZTxIdHeadersHash")
    private val PERSONAL_PREVOUTS = personalization("ZTxIdPrevoutHash")
    private val PERSONAL_SEQUENCES = personalization("ZTxIdSequencHash")
    private val PERSONAL_OUTPUTS = personalization("ZTxIdOutputsHash")
    private val PERSONAL_AMOUNTS = personalization("ZTxTrAmountsHash")
    private val PERSONAL_SCRIPTS = personalization("ZTxTrScriptsHash")
    private val PERSONAL_TXIN = personalization("Zcash___TxInHash")
    private val PERSONAL_TRANSPARENT = personalization("ZTxIdTranspaHash")
    private val TX_HASH_PERSONAL_PREFIX = personalization("ZcashTxHash_")
    private val EMPTY_SAPLING_DIGEST = blake2b(personalization("ZTxIdSaplingHash"), ByteArray(0))
    private val EMPTY_ORCHARD_DIGEST = blake2b(personalization("ZTxIdOrchardHash"), ByteArray(0))
}
