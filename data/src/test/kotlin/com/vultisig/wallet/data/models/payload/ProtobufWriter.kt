package com.vultisig.wallet.data.models.payload
internal class ProtobufWriter {
    private val buffer = mutableListOf<Byte>()

    fun writeString(fieldNumber: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeTag(fieldNumber, 2)
        writeVarint(bytes.size)
        buffer.addAll(bytes.toList())
    }

    fun writeBytes(fieldNumber: Int, value: ByteArray) {
        writeTag(fieldNumber, 2)
        writeVarint(value.size)
        buffer.addAll(value.toList())
    }

    fun writeUInt64(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, 0)
        writeVarint64(value)
    }

    fun writeMessage(fieldNumber: Int, builder: (ProtobufWriter) -> Unit) {
        val nested = ProtobufWriter()
        builder(nested)
        val nestedBytes = nested.toByteArray()
        writeTag(fieldNumber, 2)
        writeVarint(nestedBytes.size)
        buffer.addAll(nestedBytes.toList())
    }

    private fun writeTag(fieldNumber: Int, wireType: Int) {
        writeVarint((fieldNumber shl 3) or wireType)
    }

    private fun writeVarint(value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            buffer.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        buffer.add((v and 0x7F).toByte())
    }

    private fun writeVarint64(value: Long) {
        var v = value
        while (v and 0x7F.inv().toLong() != 0L) {
            buffer.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        buffer.add((v and 0x7F).toByte())
    }

    fun toByteArray(): ByteArray = buffer.toByteArray()
}