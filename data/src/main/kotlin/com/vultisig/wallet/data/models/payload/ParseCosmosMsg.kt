@file:OptIn(ExperimentalEncodingApi::class)

package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


private enum class WireType(val value: Int) {
    VARINT(0),
    FIXED64(1),
    BYTES(2),
    FIXED32(5)
}


private data class TxBody(
    val messages: MutableList<ProtobufAny> = mutableListOf(),
    var memo: String = "",
    var timeoutHeight: Long = 0L,
    var unordered: Boolean = false,
    var timeoutTimestamp: Timestamp? = null,
    val extensionOptions: MutableList<ProtobufAny> = mutableListOf(),
    val nonCriticalExtensionOptions: MutableList<ProtobufAny> = mutableListOf(),
)


private data class ProtobufAny(
    var typeUrl: String = "",
    var value: ByteArray = ByteArray(0),
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): ProtobufAny {
            val end = reader.pos + length
            val message = ProtobufAny()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.typeUrl = reader.string()
                    2 -> message.value = reader.bytes()
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProtobufAny
        if (typeUrl != other.typeUrl) return false
        if (!value.contentEquals(other.value)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = typeUrl.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}


private data class Timestamp(
    var seconds: Long = 0L,
    var nanos: Int = 0,
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): Timestamp {
            val end = reader.pos + length
            val message = Timestamp()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.seconds = reader.int64()
                    2 -> message.nanos = reader.int32()
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }
}


internal class BinaryReader(input: Any) {
    val buf: ByteArray
    var pos: Int = 0
    val len: Int

    init {
        buf = when (input) {
            is ByteArray -> input
            is BinaryReader -> input.buf
            is List<*> -> {
                input.mapNotNull { element ->
                    when (element) {
                        is Number -> element.toByte()
                        else -> throw IllegalArgumentException(
                            "List contains non-numeric element at index ${input.indexOf(element)}: $element"
                        )
                    }
                }.toByteArray()
            }

            else -> throw IllegalArgumentException("Invalid input type: ${input::class.simpleName}")
        }
        len = buf.size
    }

    private fun ensureAvailable(bytes: Int) {
        if (pos + bytes > len) {
            throw IndexOutOfBoundsException(
                "Attempting to read $bytes bytes at position $pos, but only ${len - pos} bytes available"
            )
        }
    }

    fun skip(length: Int? = null) {
        if (length != null) {
            ensureAvailable(length)
            pos += length
        } else {
            var hasMore = true
            while (hasMore) {
                ensureAvailable(1)
                hasMore = (buf[pos++].toInt() and 128) != 0
            }
        }
    }

    fun skipType(wireType: Int) {
        when (wireType) {
            WireType.VARINT.value -> skip()
            WireType.FIXED64.value -> skip(8)
            WireType.BYTES.value -> {
                val length = uint32()
                skip(length)
            }

            3 -> {
                var wt = uint32() and 7
                while (wt != 4) {
                    skipType(wt)
                    wt = uint32() and 7
                }
            }
            WireType.FIXED32.value -> skip(4)
            else -> throw IllegalStateException("Invalid wire type $wireType at offset $pos")
        }
    }

    fun uint32(): Int {
        var value = 0
        var shift = 0

        while (shift < 32) {
            ensureAvailable(1)
            val b = buf[pos++].toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)

            if ((b and 0x80) == 0) {
                return value
            }

            shift += 7
        }

        throw IllegalStateException("Invalid varint encoding: overflow in uint32 at position ${pos - 1}")
    }

    fun int32(): Int {
        return uint32()
    }


    fun int64(): Long {
        val (lo, hi) = varint64Read()
        return int64FromParts(lo, hi)
    }

    fun uint64(): Long {
        val (lo, hi) = varint64Read()
        return uint64FromParts(lo, hi)
    }

    fun bool(): Boolean {
        val (lo, hi) = varint64Read()
        return lo != 0 || hi != 0
    }

    fun bytes(): ByteArray {
        val length = uint32()

        ensureAvailable(length)
        val start = pos
        pos += length
        return buf.copyOfRange(start, start + length)
    }

    fun string(): String {
        val bytes = bytes()
        return try {
            bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("Invalid UTF-8 encoding in string at position $pos", e)
        }
    }

    private fun varint64Read(): Pair<Int, Int> {
        var lo = 0
        var hi = 0
        var shift = 0
        var bytesRead = 0

        while (shift < 28) {
            ensureAvailable(1)
            val b = buf[pos++].toInt() and 0xFF
            bytesRead++
            lo = lo or ((b and 0x7F) shl shift)

            if ((b and 0x80) == 0) {
                return Pair(lo, hi)
            }

            shift += 7
        }

        ensureAvailable(1)
        var b = buf[pos++].toInt() and 0xFF
        bytesRead++
        lo = lo or ((b and 0x0F) shl 28)
        hi = (b and 0x70) ushr 4

        if ((b and 0x80) == 0) {
            return Pair(lo, hi)
        }


        shift = 3
        while (shift < 32) {
            ensureAvailable(1)
            b = buf[pos++].toInt() and 0xFF
            bytesRead++

            if (bytesRead > 10) {
                throw IllegalStateException("Invalid varint encoding: more than 10 bytes at position ${pos - 1}")
            }

            hi = hi or ((b and 0x7F) shl shift)

            if ((b and 0x80) == 0) {
                return Pair(lo, hi)
            }

            shift += 7
        }

        throw IllegalStateException("Invalid varint encoding: overflow in varint64 at position ${pos - 1}")
    }

    private fun int64FromParts(lo: Int, hi: Int): Long {
        return (lo.toLong() and 0xFFFFFFFFL) or (hi.toLong() shl 32)
    }

    private fun uint64FromParts(lo: Int, hi: Int): Long {
        return (lo.toLong() and 0xFFFFFFFFL) or (hi.toLong() shl 32)
    }
}

private fun createBaseTxBody(): TxBody {
    return TxBody(
        messages = mutableListOf(),
        memo = "",
        timeoutHeight = 0L,
        unordered = false,
        timeoutTimestamp = null,
        extensionOptions = mutableListOf(),
        nonCriticalExtensionOptions = mutableListOf()
    )
}


private data class Coin(
    var denom: String = "",
    var amount: String = "",
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): Coin {
            val end = reader.pos + length
            val message = Coin()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.denom = reader.string()
                    2 -> message.amount = reader.string()
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }
}

private enum class SignMode(val value: Int) {
    SIGN_MODE_UNSPECIFIED(0),
    SIGN_MODE_DIRECT(1),
    SIGN_MODE_TEXTUAL(2),
    SIGN_MODE_DIRECT_AUX(3),
    SIGN_MODE_LEGACY_AMINO_JSON(127),
    SIGN_MODE_EIP_191(191);

    companion object {
        fun fromValue(value: Int): SignMode {
            return entries.find { it.value == value } ?: SIGN_MODE_UNSPECIFIED
        }
    }
}

private data class CompactBitArray(
    var extraBitsStored: Int = 0,
    var elems: ByteArray = ByteArray(0),
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): CompactBitArray {
            val end = reader.pos + length
            val message = CompactBitArray()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.extraBitsStored = reader.uint32()
                    2 -> message.elems = reader.bytes()
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompactBitArray
        if (extraBitsStored != other.extraBitsStored) return false
        if (!elems.contentEquals(other.elems)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = extraBitsStored
        result = 31 * result + elems.contentHashCode()
        return result
    }
}

private data class ModeInfoSingle(
    var mode: SignMode = SignMode.SIGN_MODE_UNSPECIFIED,
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): ModeInfoSingle {
            val end = reader.pos + length
            val message = ModeInfoSingle()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.mode = SignMode.fromValue(reader.int32())
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }
}

private data class ModeInfoMulti(
    var bitarray: CompactBitArray? = null,
    val modeInfos: MutableList<ModeInfo> = mutableListOf(),
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): ModeInfoMulti {
            val end = reader.pos + length
            val message = ModeInfoMulti()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.bitarray = CompactBitArray.decode(reader, reader.uint32())
                    2 -> message.modeInfos.add(ModeInfo.decode(reader, reader.uint32()))
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }
}

private data class ModeInfo(
    var single: ModeInfoSingle? = null,
    var multi: ModeInfoMulti? = null,
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): ModeInfo {
            val end = reader.pos + length
            val message = ModeInfo()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.single = ModeInfoSingle.decode(reader, reader.uint32())
                    2 -> message.multi = ModeInfoMulti.decode(reader, reader.uint32())
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }
}

private data class SignerInfo(
    var publicKey: ProtobufAny? = null,
    var modeInfo: ModeInfo? = null,
    var sequence: Long = 0L,
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): SignerInfo {
            val end = reader.pos + length
            val message = SignerInfo()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.publicKey = ProtobufAny.decode(reader, reader.uint32())
                    2 -> message.modeInfo = ModeInfo.decode(reader, reader.uint32())
                    3 -> message.sequence = reader.uint64()
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }
}

private data class AuthInfoFee(
    val amount: MutableList<Coin> = mutableListOf(),
    var gasLimit: Long = 0L,
    var payer: String = "",
    var granter: String = "",
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): AuthInfoFee {
            val end = reader.pos + length
            val message = AuthInfoFee()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.amount.add(Coin.decode(reader, reader.uint32()))
                    2 -> message.gasLimit = reader.uint64()
                    3 -> message.payer = reader.string()
                    4 -> message.granter = reader.string()
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }
}

private data class Tip(
    val amount: MutableList<Coin> = mutableListOf(),
    var tipper: String = "",
) {
    companion object {
        fun decode(reader: BinaryReader, length: Int): Tip {
            val end = reader.pos + length
            val message = Tip()

            while (reader.pos < end) {
                val tag = reader.uint32()
                when (tag ushr 3) {
                    1 -> message.amount.add(Coin.decode(reader, reader.uint32()))
                    2 -> message.tipper = reader.string()
                    else -> reader.skipType(tag and 7)
                }
            }

            return message
        }
    }
}

private data class AuthInfo(
    val signerInfos: MutableList<SignerInfo> = mutableListOf(),
    var authInfoFee: AuthInfoFee? = null,
    var tip: Tip? = null,
)

private fun createBaseAuthInfo(): AuthInfo {
    return AuthInfo(
        signerInfos = mutableListOf(),
        authInfoFee = null,
        tip = null
    )
}


private fun ProtobufAny.toMessage() = Message(
    typeUrl = typeUrl,
    value = Base64.encode(value)
)


private fun AuthInfoFee.toFee() = Fee(
    amount = amount.map { Amount(denom = it.denom, amount = it.amount) }
)

fun parseCosmosMessage(signDirect: SignDirectProto): CosmosMessage {
    require(signDirect.chainId.isNotBlank()) { "Chain ID cannot be blank" }
    require(signDirect.accountNumber.isNotBlank()) { "Account number cannot be blank" }
    require(signDirect.bodyBytes.isNotBlank()) { "Body bytes cannot be blank" }
    require(signDirect.authInfoBytes.isNotBlank()) { "Auth info bytes cannot be blank" }

    return try {
        val decodedTxBody = decodeTxBodySafe(signDirect.bodyBytes)
        val decodedAuthInfo = decodeAuthInfoSafe(signDirect.authInfoBytes)

        CosmosMessage(
            chainId = signDirect.chainId,
            accountNumber = signDirect.accountNumber,
            sequence = decodedAuthInfo.signerInfos.firstOrNull()?.sequence?.toString() ?: "0",
            memo = decodedTxBody.memo,
            messages = decodedTxBody.messages.map { it.toMessage() },
            authInfoFee = decodedAuthInfo.authInfoFee?.toFee() ?: Fee(amount = emptyList())
        )
    } catch (e: IndexOutOfBoundsException) {
        throw IllegalArgumentException("Invalid protobuf encoding: premature end of buffer", e)
    } catch (e: IllegalStateException) {
        throw IllegalArgumentException("Invalid protobuf encoding: ${e.message}", e)
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to parse cosmos message: ${e.message}", e)
    }
}

private fun decodeTxBodySafe(input: String): TxBody {
    require(input.isNotBlank()) { "TxBody input cannot be blank" }

    val decodedBytes = try {
        Base64.decode(input)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid base64 encoding in TxBody", e)
    }

    val reader = BinaryReader(decodedBytes)
    val message = createBaseTxBody()

    try {
        while (reader.pos < reader.len) {
            val tag = reader.uint32()
            when (tag ushr 3) {
                1 -> message.messages.add(ProtobufAny.decode(reader, reader.uint32()))
                2 -> {
                    message.memo = reader.string()
                }

                3 -> message.timeoutHeight = reader.uint64()
                4 -> message.unordered = reader.bool()
                5 -> message.timeoutTimestamp = Timestamp.decode(reader, reader.uint32())
                1023 -> message.extensionOptions.add(ProtobufAny.decode(reader, reader.uint32()))
                2047 -> message.nonCriticalExtensionOptions.add(
                    ProtobufAny.decode(
                        reader,
                        reader.uint32()
                    )
                )

                else -> reader.skipType(tag and 7)
            }
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to decode TxBody: ${e.message}", e)
    }

    require(message.messages.isNotEmpty()) { "TxBody must contain at least one message" }

    return message
}

private fun decodeAuthInfoSafe(input: String): AuthInfo {
    require(input.isNotBlank()) { "AuthInfo input cannot be blank" }

    val decodedBytes = try {
        Base64.decode(input)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid base64 encoding in AuthInfo", e)
    }

    val reader = BinaryReader(decodedBytes)
    val message = createBaseAuthInfo()

    try {
        while (reader.pos < reader.len) {
            val tag = reader.uint32()
            when (tag ushr 3) {
                1 -> message.signerInfos.add(SignerInfo.decode(reader, reader.uint32()))
                2 -> message.authInfoFee = AuthInfoFee.decode(reader, reader.uint32())
                3 -> message.tip = Tip.decode(reader, reader.uint32())
                else -> reader.skipType(tag and 7)
            }
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to decode AuthInfo: ${e.message}", e)
    }

    return message
}

@Serializable
data class CosmosMessage(
    val chainId: String,
    val accountNumber: String,
    val sequence: String,
    val memo: String,
    val messages: List<Message>,
    @SerialName("fee")
    val authInfoFee: Fee,
)

@Serializable
data class Message(
    val typeUrl: String,
    val value: String,
)

@Serializable
data class Fee(
    val amount: List<Amount>,
)

@Serializable
data class Amount(
    val denom: String,
    val amount: String,
)