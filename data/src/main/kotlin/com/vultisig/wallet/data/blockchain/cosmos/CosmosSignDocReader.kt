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

    /** What a SignDoc body's messages turned out to be. */
    data class Reading(
        val operation: DecodedOperation,
        val amount: DecodedAmount,
        val counterparty: DecodedCounterparty?,
    )

    /**
     * A body that read completely: what its messages say, and the memo the body itself commits to.
     *
     * Both are read in one pass because both are signed. The memo is carried alongside rather than
     * instead of the reading: a memo can say what a transfer is for, but only the message says what
     * the chain will do, so a caller that wants to use one has to check it against the other.
     */
    data class Body(val reading: Reading, val memo: String?)

    // MARK: - Refusal limits

    /** Real staking bodies are well below this. */
    private const val MAX_BODY_BYTES = 64 * 1024

    /** Messages in one body. A batched rewards claim sends one per validator. */
    internal const val MAX_MESSAGES = 64

    /** Bounds unknown-field scans. */
    private const val MAX_FIELDS = 512

    /** `cosmos.bank.v1beta1.MsgSend`, the transfer every memo-carried operation rides on. */
    private const val MSG_SEND_TYPE_URL = "/cosmos.bank.v1beta1.MsgSend"

    /**
     * A governance vote states itself in its own message — `QBTCTransactionHelper` writes the
     * v1beta1 form and iOS's dYdX helper has wallet-core write it, so reading the message rather
     * than the memo beside it is both stronger evidence and the same verb on either initiator. The
     * v1 module is accepted too, since a dApp may build against it and the field offsets match.
     */
    private const val MSG_VOTE_TYPE_URL = "/cosmos.gov.v1beta1.MsgVote"
    private const val MSG_VOTE_V1_TYPE_URL = "/cosmos.gov.v1.MsgVote"

    /** `TxBody.memo`. */
    private const val MEMO_FIELD = 2L

    /**
     * Reads one complete SignDoc body, or refuses it.
     *
     * @param body the decoded `SignDirect.bodyBytes` — already base64-decoded by
     *   [com.vultisig.wallet.data.models.transaction_decoding.OpaqueSignedContent.CosmosSignDirect].
     */
    fun read(body: ByteArray): Body? {
        if (body.isEmpty() || body.size > MAX_BODY_BYTES) return null

        val walked = walk(body) ?: return null
        val messages = walked.messages.takeIf { it.isNotEmpty() } ?: return null

        // Every message must be of a type this names and must decode, and they must agree on one
        // operation. A message this cannot name is refused rather than described by the memo beside
        // it: the memo is peer-supplied text, and letting it speak for an unreadable message is how
        // a `MsgExec` gets presented as a switch. A body that mixes verbs has no single one to
        // name, and naming either would describe only part of what is signed.
        val readings = ArrayList<Reading>(messages.size)
        for (message in messages) {
            val shape = shape(message.url) ?: return null
            readings.add(read(message.value, shape) ?: return null)
        }

        val first = readings.first()
        if (readings.any { it.operation != first.operation }) return null

        // A homogeneous batch has one verb but no single amount or counterparty.
        if (readings.size > 1) {
            return Body(
                reading =
                    Reading(
                        operation = first.operation,
                        amount = DecodedAmount.Unstated,
                        counterparty = null,
                    ),
                memo = walked.memo,
            )
        }
        return Body(reading = first, memo = walked.memo)
    }

    /** One `Any` in `TxBody.messages`. */
    private data class AnyMessage(val url: String, val value: ByteArray)

    /** A `TxBody` walked to its end. */
    private data class Walked(val messages: List<AnyMessage>, val memo: String?)

    /** Reads every `Any` in `TxBody.messages` and the body's memo; a partial result is refused. */
    private fun walk(body: ByteArray): Walked? {
        val reader = ByteReader(body)
        val messages = mutableListOf<AnyMessage>()
        var memo: String? = null
        var fields = 0

        while (!reader.isAtEnd) {
            if (++fields > MAX_FIELDS) return null
            val tag = reader.readTag() ?: return null

            when {
                tag.field == 1L && tag.wire == WireType.LengthDelimited -> {
                    if (messages.size >= MAX_MESSAGES) return null
                    val any = reader.readLengthDelimited() ?: return null
                    messages.add(anyContents(any) ?: return null)
                }

                // Reading to the end preserves protobuf's last-one-wins semantics here too.
                tag.field == MEMO_FIELD && tag.wire == WireType.LengthDelimited ->
                    memo = reader.readUtf8() ?: return null

                else -> if (!reader.skip(tag.wire)) return null
            }
        }

        return Walked(messages, memo?.takeIf { it.isNotEmpty() })
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

    /** What the address a message names is, to the reading. */
    private enum class Named {
        /** The validator the operation is directed at. */
        Validator,

        /** The account a transfer settles with. */
        Recipient,

        /** The signer's own account: proof the message is well-formed, not a counterparty. */
        Signer,
    }

    /** Where one message type keeps the address and the `Coin` this reads. */
    private data class Shape(
        val operation: DecodedOperation,
        val addressField: Long,
        val amountField: Long?,
        val named: Named,
    )

    /** Names only the message types whose values are decoded here; anything else is refused. */
    private fun shape(url: String): Shape? =
        when (url) {
            // delegator 1, validator 2, amount 3 (Coin)
            CosmosStakingHelper.MSG_DELEGATE_TYPE_URL ->
                Shape(DecodedOperation.Delegate, 2L, amountField = 3L, named = Named.Validator)

            CosmosStakingHelper.MSG_UNDELEGATE_TYPE_URL ->
                Shape(DecodedOperation.Undelegate, 2L, amountField = 3L, named = Named.Validator)

            // Source is field 2, destination field 3 — the destination is the relevant
            // counterparty, and reading the wrong one names the validator being left.
            CosmosStakingHelper.MSG_BEGIN_REDELEGATE_TYPE_URL ->
                Shape(DecodedOperation.Redelegate, 3L, amountField = 4L, named = Named.Validator)

            // A reward withdrawal carries no Coin: the chain settles what has accrued.
            CosmosStakingHelper.MSG_WITHDRAW_DELEGATOR_REWARD_TYPE_URL ->
                Shape(
                    DecodedOperation.ClaimRewards,
                    2L,
                    amountField = null,
                    named = Named.Validator,
                )

            // from 1, to 2, amount 3 (repeated Coin)
            MSG_SEND_TYPE_URL ->
                Shape(DecodedOperation.Transfer, 2L, amountField = 3L, named = Named.Recipient)

            // proposal 1, voter 2, option 3. The voter is the signer, and a ballot moves nothing.
            MSG_VOTE_TYPE_URL,
            MSG_VOTE_V1_TYPE_URL ->
                Shape(DecodedOperation.Vote, 2L, amountField = null, named = Named.Signer)

            else -> null
        }

    /** Pulls the named address and an optional `Coin` out of one message body. */
    private fun read(body: ByteArray, shape: Shape): Reading? {
        val reader = ByteReader(body)
        var address: String? = null
        val coins = mutableListOf<Pair<String, BigInteger>>()
        var fields = 0

        while (!reader.isAtEnd) {
            if (++fields > MAX_FIELDS) return null
            val tag = reader.readTag() ?: return null

            when {
                tag.field == shape.addressField && tag.wire == WireType.LengthDelimited ->
                    address = reader.readUtf8() ?: return null

                shape.amountField != null &&
                    tag.field == shape.amountField &&
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
            operation = shape.operation,
            amount = amount,
            counterparty =
                when (shape.named) {
                    Named.Validator -> DecodedCounterparty.Validator(address)
                    Named.Recipient -> DecodedCounterparty.Contract(address)
                    Named.Signer -> null
                },
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
