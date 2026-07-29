package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.utils.Numeric
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import timber.log.Timber

/**
 * One output created by a broadcast-but-unconfirmed transaction.
 *
 * @property scriptPubKeyHex Locking script of the output, hex encoded without a `0x` prefix.
 */
data class PendingUtxoOutput(val index: UInt, val amount: Long, val scriptPubKeyHex: String)

/**
 * A transaction this device broadcast that has not been seen in a block yet.
 *
 * @property spentOutpoints `hash:index` keys of the outpoints the transaction consumes.
 * @property recordedAtMillis Epoch millis the record was created at, used for TTL expiry.
 */
data class PendingSpend(
    val txHash: String,
    val spentOutpoints: Set<String>,
    val outputs: List<PendingUtxoOutput>,
    val recordedAtMillis: Long,
)

/**
 * In-memory, process-lifetime view of the transactions this device broadcast but hasn't seen
 * confirmed yet, so coin selection can subtract them from a confirmed-only UTXO list.
 *
 * Dash InstantSend-locks the inputs of a broadcast transaction within seconds, so a second send
 * started before the first confirms is rejected with `tx-txlock-conflict` — after the full
 * signing ceremony — as soon as coin selection reuses one of those inputs. Neither Dash's
 * `getaddressutxos` address index (built from mined blocks) nor the Blockchair fallback (filtered
 * to `block_id > 0`) knows about mempool spends, so the pending view is tracked here instead. See
 * issue #5453.
 *
 * Records are dropped as soon as one of their outputs turns up in the confirmed set, and expire
 * after 30 minutes so a transaction that fell out of the mempool cannot strand funds.
 */
@Singleton
class PendingUtxoRepository @Inject constructor() {

    private val spendsByChain = ConcurrentHashMap<Chain, ConcurrentHashMap<String, PendingSpend>>()

    /**
     * Remembers the outpoints [rawTransactionHex] spends and the outputs it creates. Silently does
     * nothing when the raw transaction can't be decoded — a missing record only means the old
     * confirmed-only behaviour, while a wrong one would hide spendable coins.
     *
     * @param rawTransactionHex Serialized legacy (non-segwit) transaction, hex encoded.
     * @param txHash Hash the broadcast returned for that transaction.
     */
    fun record(chain: Chain, rawTransactionHex: String, txHash: String) {
        val parsed = parseTransaction(rawTransactionHex)
        if (parsed == null) {
            Timber.w("could not decode broadcast %s tx, pending outpoints not tracked", chain)
            return
        }
        val (spentOutpoints, outputs) = parsed
        spendsByChain.computeIfAbsent(chain) { ConcurrentHashMap() }[txHash] =
            PendingSpend(
                txHash = txHash,
                spentOutpoints = spentOutpoints,
                outputs = outputs,
                recordedAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
    }

    /**
     * Still-unconfirmed transactions recorded for [chain], expired entries pruned.
     *
     * @param nowMillis Current epoch millis; overridable so TTL expiry is testable.
     */
    fun pendingSpends(
        chain: Chain,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): List<PendingSpend> {
        val spends = spendsByChain[chain] ?: return emptyList()
        spends.entries.removeAll { nowMillis - it.value.recordedAtMillis > PENDING_TTL_MILLIS }
        return spends.values.toList()
    }

    /**
     * Turns a confirmed-only UTXO list into the wallet's actual spendable set: outpoints already
     * consumed by a pending transaction are removed, and the pending transaction's own outputs
     * paying back to this wallet are added so a single-UTXO wallet still has something to select.
     *
     * @param confirmedUtxos UTXOs as reported by the chain's confirmed-only data source.
     * @param ownScriptPubKeyHex Lock script of the wallet address, used to spot the change output;
     *   when null or empty no pending output is offered, only the exclusion applies.
     * @param nowMillis Current epoch millis; overridable so TTL expiry is testable.
     */
    fun applyTo(
        chain: Chain,
        confirmedUtxos: List<UtxoInfo>,
        ownScriptPubKeyHex: String?,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): List<UtxoInfo> {
        val pending = pendingSpends(chain, nowMillis)
        if (pending.isEmpty()) return confirmedUtxos

        val confirmedOutpoints =
            confirmedUtxos.mapTo(mutableSetOf<String>()) { outpoint(it.hash, it.index) }
        val spentOutpoints = mutableSetOf<String>()
        val pendingOwnOutputs = mutableListOf<UtxoInfo>()

        pending.forEach { spend ->
            // An output of this transaction showing up in the confirmed set proves it was mined,
            // so its inputs are gone from that set too and the record has nothing left to add.
            if (spend.outputs.any { outpoint(spend.txHash, it.index) in confirmedOutpoints }) {
                spendsByChain[chain]?.remove(spend.txHash)
                return@forEach
            }
            spentOutpoints += spend.spentOutpoints
            if (!ownScriptPubKeyHex.isNullOrEmpty()) {
                spend.outputs
                    .filter { it.scriptPubKeyHex.equals(ownScriptPubKeyHex, ignoreCase = true) }
                    .mapTo(pendingOwnOutputs) {
                        UtxoInfo(hash = spend.txHash, amount = it.amount, index = it.index)
                    }
            }
        }

        // Filter the injected outputs too: a chain of pending sends can already have spent the
        // change of an earlier one.
        return (confirmedUtxos + pendingOwnOutputs).filterNot {
            outpoint(it.hash, it.index) in spentOutpoints
        }
    }

    private fun outpoint(hash: String, index: UInt): String = "${hash.lowercase()}:$index"

    private fun parseTransaction(
        rawTransactionHex: String
    ): Pair<Set<String>, List<PendingUtxoOutput>>? =
        try {
            val reader = TxReader(Numeric.hexStringToByteArray(rawTransactionHex))
            reader.skip(VERSION_BYTES)
            val inputCount = reader.readVarInt().toInt()
            // A zero input count is the segwit marker byte; Dash has no segwit, and misreading a
            // witness-serialized transaction as legacy would produce nonsense outpoints.
            check(inputCount in 1..MAX_ITEMS) { "unexpected input count $inputCount" }
            val spentOutpoints =
                (0 until inputCount).mapTo(mutableSetOf<String>()) {
                    val txid =
                        Numeric.toHexStringNoPrefix(reader.readBytes(HASH_BYTES).reversedArray())
                    val index = reader.readUInt32()
                    reader.skip(reader.readVarInt().toInt())
                    reader.skip(SEQUENCE_BYTES)
                    outpoint(txid, index.toUInt())
                }
            val outputCount = reader.readVarInt().toInt()
            check(outputCount in 0..MAX_ITEMS) { "unexpected output count $outputCount" }
            val outputs =
                (0 until outputCount).map { index ->
                    val amount = reader.readInt64()
                    val script = reader.readBytes(reader.readVarInt().toInt())
                    PendingUtxoOutput(
                        index = index.toUInt(),
                        amount = amount,
                        scriptPubKeyHex = Numeric.toHexStringNoPrefix(script),
                    )
                }
            spentOutpoints to outputs
        } catch (e: Exception) {
            Timber.w(e, "raw transaction could not be parsed for pending outpoint tracking")
            null
        }

    private class TxReader(private val bytes: ByteArray) {
        private var offset = 0

        fun skip(count: Int) {
            require(count >= 0 && offset + count <= bytes.size) { "truncated transaction" }
            offset += count
        }

        fun readBytes(count: Int): ByteArray {
            require(count >= 0 && offset + count <= bytes.size) { "truncated transaction" }
            return bytes.copyOfRange(offset, offset + count).also { offset += count }
        }

        fun readUInt32(): Long = readLittleEndian(4)

        fun readInt64(): Long = readLittleEndian(8)

        fun readVarInt(): Long =
            when (val first = readBytes(1).first().toInt() and 0xFF) {
                0xFD -> readLittleEndian(2)
                0xFE -> readLittleEndian(4)
                0xFF -> readLittleEndian(8)
                else -> first.toLong()
            }

        private fun readLittleEndian(count: Int): Long {
            var value = 0L
            readBytes(count).forEachIndexed { i, byte ->
                value = value or ((byte.toLong() and 0xFF) shl (8 * i))
            }
            return value
        }
    }

    private companion object {
        // Long enough to cover several Dash blocks (~2.5 min each) plus a slow mempool, short
        // enough that a transaction which never made it stops hiding its inputs for good.
        val PENDING_TTL_MILLIS = 30.minutes.inWholeMilliseconds

        const val VERSION_BYTES = 4
        const val HASH_BYTES = 32
        const val SEQUENCE_BYTES = 4
        const val MAX_ITEMS = 10_000
    }
}
