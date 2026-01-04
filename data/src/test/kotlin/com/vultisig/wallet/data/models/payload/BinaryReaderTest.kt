package com.vultisig.wallet.data.models.payload

import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class BinaryReaderTest {

    private fun createReader(bytes: ByteArray): BinaryReader {
        return BinaryReader(bytes)
    }

    @Test
    fun `test uint32 single byte`() {
        // Value 5: 0x05
        val reader = createReader(byteArrayOf(0x05))
        assertEquals(5, reader.uint32())
    }

    @Test
    fun `test uint32 two bytes`() {
        // Value 300: 0xAC 0x02
        val reader = createReader(byteArrayOf(0xAC.toByte(), 0x02))
        assertEquals(300, reader.uint32())
    }

    @Test
    fun `test uint32 maximum value`() {
        // Value 4294967295 (max uint32): 0xFF 0xFF 0xFF 0xFF 0x0F
        val reader = createReader(byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F
        ))
        assertEquals(-1, reader.uint32()) // In Kotlin, this wraps to -1 for signed Int
    }

    @Test
    fun `test uint32 invalid encoding - too many bytes`() {
        // More than 5 bytes should throw
        val reader = createReader(byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        ))
        assertFailsWith<IllegalStateException> {
            reader.uint32()
        }
    }

    @Test
    fun `test uint32 premature EOF`() {
        // Incomplete varint (continuation bit set but no next byte)
        val reader = createReader(byteArrayOf(0x80.toByte()))
        assertFailsWith<IndexOutOfBoundsException> {
            reader.uint32()
        }
    }

    @Test
    fun `test int64 positive value`() {
        // Value 150: 0x96 0x01
        val reader = createReader(byteArrayOf(0x96.toByte(), 0x01))
        assertEquals(150L, reader.int64())
    }

    @Test
    fun `test int64 large value`() {
        // Value 9223372036854775807 (max int64)
        val reader = createReader(byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F
        ))
        assertEquals(Long.MAX_VALUE, reader.int64())
    }

    @Test
    fun `test uint64 maximum value`() {
        // Maximum uint64: 0xFFFFFFFFFFFFFFFF
        val reader = createReader(byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x01
        ))
        assertEquals(-1L, reader.uint64()) // Wraps in signed Long
    }

    @Test
    fun `test varint64 invalid encoding - too many bytes`() {
        // More than 10 bytes should throw
        val reader = createReader(ByteArray(11) { 0xFF.toByte() })
        assertFailsWith<IllegalStateException> {
            reader.int64()
        }
    }

    @Test
    fun `test bool true`() {
        val reader = createReader(byteArrayOf(0x01))
        assertTrue(reader.bool())
    }

    @Test
    fun `test bool false`() {
        val reader = createReader(byteArrayOf(0x00))
        assertFalse(reader.bool())
    }

    @Test
    fun `test bytes normal`() {
        val data = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F) // "Hello"
        // Length-prefixed: 0x05 followed by data
        val reader = createReader(byteArrayOf(0x05) + data)
        assertArrayEquals(data, reader.bytes())
    }

    @Test
    fun `test bytes empty`() {
        val reader = createReader(byteArrayOf(0x00))
        assertArrayEquals(byteArrayOf(), reader.bytes())
    }

    @Test
    fun `test bytes exceeds buffer`() {
        // Claims 10 bytes but only 5 available
        val reader = createReader(byteArrayOf(0x0A, 0x01, 0x02, 0x03, 0x04, 0x05))
        assertFailsWith<IndexOutOfBoundsException> {
            reader.bytes()
        }
    }

    @Test
    fun `test string valid UTF-8`() {
        val text = "Hello, 世界"
        val utf8Bytes = text.toByteArray(Charsets.UTF_8)
        val reader = createReader(byteArrayOf(utf8Bytes.size.toByte()) + utf8Bytes)
        assertEquals(text, reader.string())
    }

    @Test
    fun `test string empty`() {
        val reader = createReader(byteArrayOf(0x00))
        assertEquals("", reader.string())
    }

    @Test
    fun `test skip with length`() {
        val reader = createReader(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        reader.skip(3)
        assertEquals(3, reader.pos)
    }

    @Test
    fun `test skip varint`() {
        val reader = createReader(byteArrayOf(0xAC.toByte(), 0x02, 0xFF.toByte()))
        reader.skip() // Skips the varint (0xAC 0x02)
        assertEquals(2, reader.pos)
    }

    @Test
    fun `test skip exceeds buffer`() {
        val reader = createReader(byteArrayOf(0x01, 0x02))
        assertFailsWith<IndexOutOfBoundsException> {
            reader.skip(5)
        }
    }

    @Test
    fun `test skipType VARINT`() {
        val reader = createReader(byteArrayOf(0xAC.toByte(), 0x02, 0xFF.toByte()))
        reader.skipType(0) // WireType.VARINT
        assertEquals(2, reader.pos)
    }

    @Test
    fun `test skipType FIXED64`() {
        val reader = createReader(ByteArray(10) { it.toByte() })
        reader.skipType(1) // WireType.FIXED64
        assertEquals(8, reader.pos)
    }

    @Test
    fun `test skipType FIXED32`() {
        val reader = createReader(ByteArray(10) { it.toByte() })
        reader.skipType(5) // WireType.FIXED32
        assertEquals(4, reader.pos)
    }

    @Test
    fun `test skipType BYTES`() {
        // Length-delimited: length=3, then 3 bytes of data
        val reader = createReader(byteArrayOf(0x03, 0x01, 0x02, 0x03, 0xFF.toByte()))
        reader.skipType(2) // WireType.BYTES
        assertEquals(4, reader.pos)
    }

    @Test
    fun `test skipType invalid wire type`() {
        val reader = createReader(byteArrayOf(0x01))
        assertFailsWith<IllegalStateException> {
            reader.skipType(6) // Invalid wire type
        }
    }

    @Test
    fun `test multiple operations on same reader`() {
        val reader = createReader(byteArrayOf(
            0x05,                    // uint32: 5 (1 byte)
            0x03,                    // string length: 3 (1 byte)
            0x61, 0x62, 0x63,       // "abc" (3 bytes)
            0x01                     // bool: true (1 byte)
        ))

        assertEquals(5, reader.uint32())       // pos = 1
        assertEquals("abc", reader.string())   // pos = 5 (1 + 1 + 3)
        assertTrue(reader.bool())              // pos = 6 (5 + 1)
        assertEquals(6, reader.pos)            // Total: 6 bytes
    }

    @Test
    fun `test reader position tracking`() {
        val reader = createReader(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        assertEquals(0, reader.pos)

        reader.skip(2)
        assertEquals(2, reader.pos)

        reader.skip(1)
        assertEquals(3, reader.pos)
    }

    @Test
    fun `test boundary condition - zero length input`() {
        val reader = createReader(byteArrayOf())
        assertEquals(0, reader.len)
        assertEquals(0, reader.pos)
    }

    @Test
    fun `test reading at exact buffer end`() {
        val reader = createReader(byteArrayOf(0x01, 0x02))
        reader.skip(2)
        assertEquals(reader.len, reader.pos)
    }
}