@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import java.math.BigInteger
import java.util.Base64

private const val SUI_ADDRESS_LENGTH = 32

/**
 * Best-effort pure-Kotlin decoder for a Sui `TransactionData::V1` BCS payload — a dApp-supplied
 * Programmable Transaction Block (PTB). Used only to render a human-readable summary on the keysign
 * verify screen; WalletCore signs the original bytes verbatim regardless of whether this succeeds,
 * so a decode failure must never block signing — callers should catch [IllegalArgumentException] /
 * [IllegalStateException] and fall back to the raw bytes. Byte layout cross-verified against the
 * official Sui BCS schema and the iOS/Windows decoders of the same PTB.
 */
object SuiPtbParser {

    fun parse(base64TransactionData: String): ParsedSuiTransaction {
        val bytes =
            runCatching { Base64.getDecoder().decode(base64TransactionData) }.getOrNull()
                ?: throw IllegalArgumentException("Invalid base64 Sui PTB")
        val reader = SuiBcsReader(bytes)

        check(reader.readULEB128() == 0) { "Unsupported TransactionData variant" }
        check(reader.readULEB128() == 0) { "Unsupported TransactionKind variant" }

        val inputs = reader.readVector { readCallArg(reader) }
        val commands = reader.readVector { readCommand(reader) }
        val sender = reader.readAddress()

        // GasData: only the payment count is kept (each entry's id/version/digest is otherwise
        // unused for display); the owner address is read to advance the cursor but not surfaced.
        val gasPaymentCount =
            reader
                .readVector {
                    reader.readAddress()
                    reader.readU64()
                    reader.readByteVector()
                }
                .size
        reader.readAddress()
        val gasPrice = reader.readU64()
        val gasBudget = reader.readU64()

        // TransactionExpiration follows but is never read, matching the iOS/Windows decoders of
        // this same wire format — trailing bytes are intentionally left unconsumed.
        return ParsedSuiTransaction(
            sender = sender,
            gasPrice = gasPrice,
            gasBudget = gasBudget,
            gasPaymentCount = gasPaymentCount,
            inputs = inputs,
            commands = commands,
        )
    }

    private fun readCallArg(reader: SuiBcsReader): SuiPtbInput =
        when (val tag = reader.readULEB128()) {
            0 -> SuiPtbInput.Pure(decodePureValue(reader.readByteVector()))
            1 -> readObjectArg(reader)
            else -> error("Unsupported CallArg variant $tag")
        }

    private fun readObjectArg(reader: SuiBcsReader): SuiPtbInput.Object =
        when (val tag = reader.readULEB128()) {
            0 -> {
                val objectId = reader.readAddress()
                reader.readU64()
                reader.readByteVector()
                SuiPtbInput.Object(SuiObjectKind.IMM_OR_OWNED, objectId, mutable = null)
            }
            1 -> {
                val objectId = reader.readAddress()
                reader.readU64()
                val mutable = reader.readBool()
                SuiPtbInput.Object(SuiObjectKind.SHARED, objectId, mutable = mutable)
            }
            2 -> {
                val objectId = reader.readAddress()
                reader.readU64()
                reader.readByteVector()
                SuiPtbInput.Object(SuiObjectKind.RECEIVING, objectId, mutable = null)
            }
            else -> error("Unsupported ObjectArg variant $tag")
        }

    private fun readCommand(reader: SuiBcsReader): SuiCommand =
        when (val tag = reader.readULEB128()) {
            0 -> readMoveCall(reader)
            1 ->
                SuiCommand.TransferObjects(
                    objects = reader.readVector { readArgument(reader) },
                    address = readArgument(reader),
                )
            2 ->
                SuiCommand.SplitCoins(
                    coin = readArgument(reader),
                    amounts = reader.readVector { readArgument(reader) },
                )
            3 ->
                SuiCommand.MergeCoins(
                    destination = readArgument(reader),
                    sources = reader.readVector { readArgument(reader) },
                )
            4 ->
                SuiCommand.Publish(
                    moduleCount = reader.readVector { reader.readByteVector() }.size,
                    dependencyCount = reader.readVector { reader.readAddress() }.size,
                )
            5 -> {
                val elementType = readOptionalTypeTag(reader)
                SuiCommand.MakeMoveVec(
                    elementType = elementType,
                    elementCount = reader.readVector { readArgument(reader) }.size,
                )
            }
            6 -> {
                val moduleCount = reader.readVector { reader.readByteVector() }.size
                val dependencyCount = reader.readVector { reader.readAddress() }.size
                val packageId = reader.readAddress()
                val ticket = readArgument(reader)
                SuiCommand.Upgrade(packageId, moduleCount, dependencyCount, ticket)
            }
            else -> error("Unsupported Command variant $tag")
        }

    private fun readMoveCall(reader: SuiBcsReader): SuiCommand.MoveCall {
        val packageId = reader.readAddress()
        val module = reader.readString()
        val function = reader.readString()
        val typeArguments = reader.readVector { readTypeTag(reader) }
        val arguments = reader.readVector { readArgument(reader) }
        return SuiCommand.MoveCall(packageId, module, function, typeArguments, arguments)
    }

    private fun readArgument(reader: SuiBcsReader): SuiArgument =
        when (val tag = reader.readULEB128()) {
            0 -> SuiArgument.GasCoin
            1 -> SuiArgument.Input(reader.readU16())
            2 -> SuiArgument.Result(reader.readU16())
            3 -> SuiArgument.NestedResult(reader.readU16(), reader.readU16())
            else -> error("Unsupported Argument variant $tag")
        }

    private fun readOptionalTypeTag(reader: SuiBcsReader): String? =
        when (val tag = reader.readULEB128()) {
            0 -> null
            1 -> readTypeTag(reader)
            else -> error("Unsupported Option variant $tag")
        }

    private fun readTypeTag(reader: SuiBcsReader): String =
        when (val tag = reader.readULEB128()) {
            0 -> "bool"
            1 -> "u8"
            2 -> "u64"
            3 -> "u128"
            4 -> "address"
            5 -> "signer"
            6 -> "vector<${readTypeTag(reader)}>"
            7 -> readStructTag(reader)
            8 -> "u16"
            9 -> "u32"
            10 -> "u256"
            else -> error("Unsupported TypeTag variant $tag")
        }

    private fun readStructTag(reader: SuiBcsReader): String {
        val address = reader.readAddress()
        val module = reader.readString()
        val name = reader.readString()
        val typeParams = reader.readVector { readTypeTag(reader) }
        val generics = if (typeParams.isEmpty()) "" else "<${typeParams.joinToString(", ")}>"
        return "$address::$module::$name$generics"
    }

    private fun decodePureValue(bytes: ByteArray): SuiPureValue =
        when (bytes.size) {
            1 ->
                if (bytes[0].toInt() == 0 || bytes[0].toInt() == 1) {
                    SuiPureValue("bool", (bytes[0].toInt() == 1).toString())
                } else {
                    SuiPureValue("u8", (bytes[0].toInt() and 0xFF).toString())
                }
            8 -> SuiPureValue("u64", bytes.toLittleEndianBigInteger().toString())
            16 -> SuiPureValue("u128", "0x" + bytes.toLittleEndianBigInteger().toString(16))
            32 -> SuiPureValue("address", "0x" + bytes.toHexString())
            else -> SuiPureValue("bytes(${bytes.size})", "0x" + bytes.toHexString())
        }
}

data class ParsedSuiTransaction(
    val sender: String,
    val gasPrice: BigInteger,
    val gasBudget: BigInteger,
    val gasPaymentCount: Int,
    val inputs: List<SuiPtbInput>,
    val commands: List<SuiCommand>,
)

data class SuiPureValue(val type: String, val display: String)

enum class SuiObjectKind {
    IMM_OR_OWNED,
    SHARED,
    RECEIVING,
}

sealed class SuiPtbInput {
    data class Pure(val value: SuiPureValue) : SuiPtbInput()

    data class Object(val kind: SuiObjectKind, val objectId: String, val mutable: Boolean?) :
        SuiPtbInput()
}

sealed class SuiArgument {
    data object GasCoin : SuiArgument()

    data class Input(val index: Int) : SuiArgument()

    data class Result(val index: Int) : SuiArgument()

    data class NestedResult(val commandIndex: Int, val resultIndex: Int) : SuiArgument()
}

sealed class SuiCommand {
    data class MoveCall(
        val packageId: String,
        val module: String,
        val function: String,
        val typeArguments: List<String>,
        val arguments: List<SuiArgument>,
    ) : SuiCommand()

    data class TransferObjects(val objects: List<SuiArgument>, val address: SuiArgument) :
        SuiCommand()

    data class SplitCoins(val coin: SuiArgument, val amounts: List<SuiArgument>) : SuiCommand()

    data class MergeCoins(val destination: SuiArgument, val sources: List<SuiArgument>) :
        SuiCommand()

    data class Publish(val moduleCount: Int, val dependencyCount: Int) : SuiCommand()

    data class MakeMoveVec(val elementType: String?, val elementCount: Int) : SuiCommand()

    data class Upgrade(
        val packageId: String,
        val moduleCount: Int,
        val dependencyCount: Int,
        val ticket: SuiArgument,
    ) : SuiCommand()
}

private fun ByteArray.toLittleEndianBigInteger(): BigInteger {
    var result = BigInteger.ZERO
    for (i in indices.reversed()) {
        result = result.shiftLeft(8).or(BigInteger.valueOf(this[i].toLong() and 0xFF))
    }
    return result
}

/**
 * Byte-cursor reader for Sui's BCS wire format: ULEB128 for enum discriminants/vector lengths,
 * fixed-width little-endian integers, and fixed 32-byte addresses. Every read is bounds-checked so
 * a truncated or hostile (dApp-supplied) buffer fails with [IllegalArgumentException] /
 * [IllegalStateException] rather than an unchecked array access.
 */
private class SuiBcsReader(private val bytes: ByteArray) {
    private var cursor = 0

    private val remaining: Int
        get() = bytes.size - cursor

    fun readByte(): Int {
        require(cursor < bytes.size) { "Truncated Sui PTB: expected a byte" }
        return bytes[cursor++].toInt() and 0xFF
    }

    fun readULEB128(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val byte = readByte()
            require(shift < 32) { "ULEB128 value too large" }
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return result
    }

    fun readU16(): Int {
        val low = readByte()
        val high = readByte()
        return low or (high shl 8)
    }

    fun readU64(): BigInteger = readBytes(8).toLittleEndianBigInteger()

    fun readBool(): Boolean = readByte() != 0

    fun readBytes(length: Int): ByteArray {
        require(length in 0..remaining) { "Truncated Sui PTB: expected $length bytes" }
        val result = bytes.copyOfRange(cursor, cursor + length)
        cursor += length
        return result
    }

    fun readByteVector(): ByteArray = readBytes(readULEB128())

    fun readAddress(): String = "0x" + readBytes(SUI_ADDRESS_LENGTH).toHexString()

    fun readString(): String = readByteVector().toString(Charsets.UTF_8)

    fun <T> readVector(readElement: () -> T): List<T> {
        val count = readULEB128()
        // Each element is at least 1 byte, so a declared count that can't fit in the remaining
        // buffer is proof of a malformed/hostile payload — reject before allocating/looping.
        require(count in 0..remaining) { "Vector count exceeds remaining buffer" }
        return List(count) { readElement() }
    }
}
