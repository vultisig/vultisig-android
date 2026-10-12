package com.vultisig.wallet.data.crypto.ton

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Every expected BOC here was emitted by `@ton/core`'s `Builder` + `Cell.toBoc()`, so the writer is
 * pinned bit-for-bit to the library the SDK, the extension, and every TON dApp build with — and
 * every body it produces is then read back through [TonBocParser], the reader the keysign verify
 * screen trusts.
 */
internal class TonCellBuilderTest {

    private val owner = "0:5fc1eb0442d7baf8e9e756846ba17836161b3990a42d7c821318fdb6086f7cf9"

    @Test
    fun `serializes a byte-aligned single cell exactly like ton core`() {
        val cell = TonCellBuilder().storeUInt(0x47d54391L, 32).storeUInt(0, 64).build()

        assertEquals(
            "b5ee9c7241010101000e00001847d543910000000000000000e9c18654",
            TonBocSerializer.serialize(cell).toHexString(),
        )
        assertEquals(
            "b5ee9c7201010101000e00001847d543910000000000000000",
            TonBocSerializer.serialize(cell, withCrc32c = false).toHexString(),
        )
    }

    @Test
    fun `pads a non-byte-aligned cell with the completion tag`() {
        val cell = TonCellBuilder().storeUInt(0b101, 3).build()

        assertEquals(
            "b5ee9c72410101010003000001b083bd2ec7",
            TonBocSerializer.serialize(cell).toHexString(),
        )
    }

    @Test
    fun `encodes coins as VarUInteger 16 with the minimal byte length`() {
        fun coins(value: String) =
            TonBocSerializer.serialize(TonCellBuilder().storeCoins(BigInteger(value)).build())
                .toHexString()

        assertEquals("b5ee9c72410101010003000001088597e2ff", coins("0"))
        assertEquals("b5ee9c724101010100040000031ff87c3fbc10", coins("255"))
        assertEquals("b5ee9c72410101010005000005201008b3d22573", coins("256"))
        assertEquals(
            "b5ee9c7241010101000c00001390100000000000000008b53c4a94",
            coins("18446744073709551616"),
        )
    }

    @Test
    fun `encodes a masterchain address with its signed workchain`() {
        val cell =
            TonCellBuilder()
                .storeAddress("-1:3333333333333333333333333333333333333333333333333333333333333333")
                .build()

        assertEquals(
            "b5ee9c724101010100240000439fe6666666666666666666666666666666666666666666666666666666666666667081557149",
            TonBocSerializer.serialize(cell).toHexString(),
        )
    }

    @Test
    fun `serializes a two-cell tree with the ref after its parent`() {
        val flags = TonCellBuilder().storeBit(false).storeBit(false).build()
        val burn =
            TonCellBuilder()
                .storeUInt(0x595f07bcL, 32)
                .storeUInt(0, 64)
                .storeCoins(BigInteger.valueOf(4_300_000_000L))
                .storeAddress(owner)
                .storeMaybeRef(flags)
                .build()

        assertEquals(
            "te6cckEBAgEAOQABZllfB7wAAAAAAAAAAFAQBMywCAC/g9YIha918dPOrQjXQvBsLDZzIUha+QQmMftsEN758wEAASBjtrI+",
            TonBocSerializer.toBase64(burn),
        )
    }

    @Test
    fun `every built cell reads back through the parser`() {
        val child = TonCellBuilder().storeBit(true).storeBit(false).build()
        val root =
            TonCellBuilder()
                .storeUInt(0xdeadbeefL, 32)
                .storeCoins(BigInteger.valueOf(123_456_789L))
                .storeAddress(owner)
                .storeMaybeRef(child)
                .storeBit(true)
                .build()

        val slice = TonBocParser.parse(TonBocSerializer.toBase64(root)).beginParse()

        assertEquals(0xdeadbeefL, slice.loadUInt(32))
        assertEquals(BigInteger.valueOf(123_456_789L), slice.loadCoins())
        assertEquals(owner, slice.loadAddress())
        val ref = slice.loadMaybeRef()?.beginParse()
        assertEquals(true, ref?.loadBit())
        assertEquals(false, ref?.loadBit())
        assertEquals(0, ref?.remainingBits)
        assertEquals(true, slice.loadBit())
        assertEquals(0, slice.remainingBits)
    }

    @Test
    fun `refuses values that do not fit their width`() {
        assertFailsWith<TonCellException> { TonCellBuilder().storeUInt(256, 8) }
        assertFailsWith<TonCellException> { TonCellBuilder().storeUInt(-1, 8) }
        assertFailsWith<TonCellException> {
            TonCellBuilder().storeUIntBig(BigInteger.ONE.shiftLeft(64), 64)
        }
        assertFailsWith<TonCellException> { TonCellBuilder().storeCoins(BigInteger.valueOf(-1)) }
        assertFailsWith<TonCellException> {
            TonCellBuilder().storeCoins(BigInteger.ONE.shiftLeft(120))
        }
    }

    @Test
    fun `refuses a cell over 1023 bits or 4 refs`() {
        val builder = TonCellBuilder()
        repeat(1023) { builder.storeBit(false) }
        assertFailsWith<TonCellException> { builder.storeBit(false) }

        val refs = TonCellBuilder()
        val leaf = TonCellBuilder().build()
        repeat(4) { refs.storeRef(leaf) }
        assertFailsWith<TonCellException> { refs.storeRef(leaf) }
    }

    @Test
    fun `refuses a malformed address`() {
        assertFailsWith<TonCellException> { TonCellBuilder().storeAddress("EQabc") }
        assertFailsWith<TonCellException> { TonCellBuilder().storeAddress("0:1234") }
        assertFailsWith<TonCellException> { TonCellBuilder().storeAddress("300:${"0".repeat(64)}") }
    }

    @Test
    fun `converts friendly addresses to raw and passes raw through`() {
        assertEquals(owner, tonFriendlyToRaw("EQBfwesEQte6-OnnVoRroXg2Fhs5kKQtfIITGP22CG98-SSR"))
        assertEquals(owner, tonFriendlyToRaw("UQBfwesEQte6-OnnVoRroXg2Fhs5kKQtfIITGP22CG98-XlU"))
        assertEquals(owner, tonFriendlyToRaw("EQBfwesEQte6+OnnVoRroXg2Fhs5kKQtfIITGP22CG98+SSR"))
        assertEquals(owner, tonFriendlyToRaw(owner))
        assertEquals(owner, tonFriendlyToRaw(owner.uppercase().replaceFirst("0:", "0:")))
        assertEquals(
            "-1:3333333333333333333333333333333333333333333333333333333333333333",
            tonFriendlyToRaw("Ef8zMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzM0vF"),
        )
        assertNull(tonFriendlyToRaw("not an address"))
        assertNull(tonFriendlyToRaw("0:1234"))
        assertNull(tonFriendlyToRaw(""))
    }
}
