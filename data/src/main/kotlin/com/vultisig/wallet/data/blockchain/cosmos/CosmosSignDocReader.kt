package com.vultisig.wallet.data.blockchain.cosmos

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingHelper
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import java.math.BigInteger

/**
 * Reads operation, amount, and counterparty out of the protobuf `TxBody` a co-signer receives.
 *
 * Mirrors the iOS `CosmosSignDocReader`. Malformed, mixed, partial, or oversized bodies are refused
 * as a whole rather than partially described: every length and varint here is peer-supplied, and a
 * body this cannot read completely is one the co-signer is better served by the screen it already
 * had than by a confident wrong verb.
 *
 * A hand-written reader rather than a generated one because the Cosmos message set is not in
 * `commondata` — and because the refusals, not the parsing, are what this exists for.
 */
internal object CosmosSignDocReader {

    /** What a SignDoc body turned out to be. */
    data class Reading(
        val operation: DecodedOperation,
        val amount: DecodedAmount,
        val counterparty: DecodedCounterparty?,
    )

    // MARK: - Refusal limits

    /** Real staking bodies are well below this. */
    private const val MAX_BODY_BYTES = 64 * 1024

    /** Messages in one body. A batched rewards claim sends one per validator. */
    private const val MAX_MESSAGES = 64

    /** Bounds unknown-field scans. */
    private const val MAX_FIELDS = 512

    /** `cosmos.bank.v1beta1.MsgSend`, the one non-staking message this names. */
    private const val MSG_SEND_TYPE_URL = "/cosmos.bank.v1beta1.MsgSend"

    /**
     * Reads one complete SignDoc body, or refuses it.
     *
     * @param body the decoded `SignDirect.bodyBytes` — already base64-decoded by
     *   [com.vultisig.wallet.data.models.transaction_decoding.OpaqueSignedContent.CosmosSignDirect].
     */
    fun read(body: ByteArray): Reading? {
        if (body.isEmpty() || body.size > MAX_BODY_BYTES) return null

        val messages = messageBodies(body)?.takeIf { it.isNotEmpty() } ?: return null

        // Every message must decode, and they must agree on one operation. A body that mixes verbs
        // has no single one to name, and naming either would describe only part of what is signed.
        val readings = ArrayList<Reading>(messages.size)
        for (message in messages) {
            readings.add(read(message) ?: return null)
        }

        val first = readings.first()
        if (readings.any { it.operation != first.operation }) return null

        // A homogeneous batch has one verb but no single amount or counterparty.
        if (readings.size > 1) {
            return Reading(
                operation = first.operation,
                amount = DecodedAmount.Unstated,
                counterparty = null,
            )
        }
        return first
    }

    /** One `Any` in `TxBody.messages`. */
    private data class AnyMessage(val url: String, val value: ByteArray)

    /** Reads every `Any` in `TxBody.messages`; a partial result is refused. */
    private fun messageBodies(body: ByteArray): List<AnyMessage>? {
        val reader = ByteReader(body)
        val messages = mutableListOf<AnyMessage>()
        var fields = 0

        while (!reader.isAtEnd) {
            if (++fields > MAX_FIELDS) return null
            val tag = reader.readTag() ?: return null

            if (tag.field == 1L && tag.wire == WireType.LengthDelimited) {
                if (messages.size >= MAX_MESSAGES) return null
                val any = reader.readLengthDelimited() ?: return null
                messages.add(anyContents(any) ?: return null)
            } else {
                if (!reader.skip(tag.wire)) return null
            }
        }

        return messages
    }

    /** Reads to the end, which preserves protobuf's last-one-wins semantics. */
    private fun anyContents(any: ByteArray): AnyMessage? {
        val reader = ByteReader(any)
        var url: String? = null
        var value: ByteArray? = null
        var fields = 0

        while (!reader.isAtEnd) {
            if (++fields > MAX_FIELDS) return null
            val tag = reader.readTag() ?: return null

            when {
                tag.field == 1L && tag.wire == WireType.LengthDelimited ->
                    url = reader.readUtf8() ?: return null

                tag.field == 2L && tag.wire == WireType.LengthDelimited ->
                    value = reader.readLengthDelimited() ?: return null

                else -> if (!reader.skip(tag.wire)) return null
            }
        }

        // A missing value is legal protobuf and decodes as an empty message; a missing type URL
        // names nothing at all.
        return AnyMessage(url ?: return null, value ?: ByteArray(0))
    }

    // MARK: - The messages this can corroborate

    /** Names only the message types whose values are decoded here. */
    private fun read(message: AnyMessage): Reading? =
        when (message.url) {
            // delegator 1, validator 2, amount 3 (Coin)
            CosmosStakingHelper.MSG_DELEGATE_TYPE_URL ->
                readAddressed(
                    message.value,
                    DecodedOperation.Delegate,
                    validatorField = 2L,
                    amountField = 3L,
                )

            CosmosStakingHelper.MSG_UNDELEGATE_TYPE_URL ->
                readAddressed(
                    message.value,
                    DecodedOperation.Undelegate,
                    validatorField = 2L,
                    amountField = 3L,
                )

            // Source is field 2, destination field 3 — the destination is the relevant
            // counterparty, and reading the wrong one names the validator being left.
            CosmosStakingHelper.MSG_BEGIN_REDELEGATE_TYPE_URL ->
                readAddressed(
                    message.value,
                    DecodedOperation.Redelegate,
                    validatorField = 3L,
                    amountField = 4L,
                )

            // A reward withdrawal carries no Coin: the chain settles what has accrued.
            CosmosStakingHelper.MSG_WITHDRAW_DELEGATOR_REWARD_TYPE_URL ->
                readAddressed(
                    message.value,
                    DecodedOperation.ClaimRewards,
                    validatorField = 2L,
                    amountField = null,
                )

            // from 1, to 2, amount 3 (repeated Coin)
            MSG_SEND_TYPE_URL ->
                readAddressed(
                    message.value,
                    DecodedOperation.Transfer,
                    validatorField = 2L,
                    amountField = 3L,
                )

            else -> null
        }

    /** Pulls the named address and an optional `Coin` out of one message body. */
    private fun readAddressed(
        body: ByteArray,
        operation: DecodedOperation,
        validatorField: Long,
        amountField: Long?,
    ): Reading? {
        val reader = ByteReader(body)
        var address: String? = null
        val coins = mutableListOf<Pair<String, BigInteger>>()
        var fields = 0

        while (!reader.isAtEnd) {
            if (++fields > MAX_FIELDS) return null
            val tag = reader.readTag() ?: return null

            when {
                tag.field == validatorField && tag.wire == WireType.LengthDelimited ->
                    address = reader.readUtf8() ?: return null

                amountField != null &&
                    tag.field == amountField &&
                    tag.wire == WireType.LengthDelimited -> {
                    val raw = reader.readLengthDelimited() ?: return null
                    coins.add(coin(raw) ?: return null)
                }

                else -> if (!reader.skip(tag.wire)) return null
            }
        }

        // An absent address proves nothing about who the operation is directed at.
        if (address.isNullOrEmpty()) return null

        // A multi-denom send has no single amount to name.
        val amount =
            if (coins.size == 1)
                DecodedAmount.Units(coins[0].second, DecodedAsset.Denom(coins[0].first))
            else DecodedAmount.Unstated

        return Reading(
            operation = operation,
            amount = amount,
            counterparty =
                if (operation == DecodedOperation.Transfer) DecodedCounterparty.Contract(address)
                else DecodedCounterparty.Validator(address),
        )
    }

    /** `cosmos.base.v1beta1.Coin` — denom 1, amount 2, both strings. */
    private fun coin(body: ByteArray): Pair<String, BigInteger>? {
        val reader = ByteReader(body)
        var denom: String? = null
        var amount: BigInteger? = null
        var fields = 0

        while (!reader.isAtEnd) {
            if (++fields > MAX_FIELDS) return null
            val tag = reader.readTag() ?: return null

            when {
                tag.field == 1L && tag.wire == WireType.LengthDelimited ->
                    denom = reader.readUtf8() ?: return null

                tag.field == 2L && tag.wire == WireType.LengthDelimited -> {
                    val text = reader.readUtf8() ?: return null
                    val value = text.toBigIntegerOrNull() ?: return null
                    if (value.signum() < 0) return null
                    amount = value
                }

                else -> if (!reader.skip(tag.wire)) return null
            }
        }

        val resolvedDenom = denom?.takeIf { it.isNotEmpty() } ?: return null
        return resolvedDenom to (amount ?: return null)
    }

    private fun String.toBigIntegerOrNull(): BigInteger? =
        runCatching { BigInteger(this) }.getOrNull()

    // MARK: - Bounds-checked protobuf reader

    /** Protobuf wire types, from the low three bits of a tag. */
    private enum class WireType(val value: Long) {
        Varint(0),
        Fixed64(1),
        LengthDelimited(2),
        StartGroup(3),
        EndGroup(4),
        Fixed32(5);

        companion object {
            fun from(value: Long): WireType? = entries.firstOrNull { it.value == value }
        }
    }

    private data class Tag(val field: Long, val wire: WireType)

    /**
     * Reads peer-supplied bytes without ever indexing past the end. Every accessor answers null on
     * malformed input instead of throwing, so a truncated body is a refusal rather than a crash on
     * a screen the user is mid-ceremony on.
     */
    private class ByteReader(private val bytes: ByteArray) {
        private var index = 0

        val isAtEnd: Boolean
            get() = index >= bytes.size

        /** Base-128 varint that refuses `Long` overflow. */
        fun readVarint(): Long? {
            var value = 0L
            var shift = 0

            while (shift < 64) {
                if (index >= bytes.size) return null
                val byte = bytes[index].toInt() and 0xFF
                index++

                val payload = (byte and 0x7F).toLong()
                // Only one payload bit fits at shift 63.
                if (shift == 63 && payload > 1) return null

                value = value or (payload shl shift)
                if (byte and 0x80 == 0) return value
                shift += 7
            }

            return null
        }

        fun readTag(): Tag? {
            val tag = readVarint() ?: return null
            val wire = WireType.from(tag and 0x07) ?: return null
            return Tag(field = tag ushr 3, wire = wire)
        }

        fun readLengthDelimited(): ByteArray? {
            val length = readVarint() ?: return null
            if (length < 0 || length > Int.MAX_VALUE.toLong()) return null
            val count = length.toInt()
            if (bytes.size - index < count) return null

            val end = index + count
            val slice = bytes.copyOfRange(index, end)
            index = end
            return slice
        }

        /** A length-delimited field read as UTF-8, refusing anything that is not valid UTF-8. */
        fun readUtf8(): String? {
            val raw = readLengthDelimited() ?: return null
            val text = raw.toString(Charsets.UTF_8)
            // `toString` substitutes U+FFFD rather than failing, so round-trip to catch it.
            return text.takeIf { it.toByteArray(Charsets.UTF_8).contentEquals(raw) }
        }

        fun skip(wire: WireType): Boolean =
            when (wire) {
                WireType.Varint -> readVarint() != null
                WireType.Fixed64 -> advance(8)
                WireType.LengthDelimited -> readLengthDelimited() != null
                WireType.Fixed32 -> advance(4)
                // Deprecated groups are not valid in the SignDocs this supports.
                WireType.StartGroup,
                WireType.EndGroup -> false
            }

        private fun advance(count: Int): Boolean {
            if (bytes.size - index < count) return false
            index += count
            return true
        }
    }
}
