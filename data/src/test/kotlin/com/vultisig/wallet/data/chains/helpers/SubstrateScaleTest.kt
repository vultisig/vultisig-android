package com.vultisig.wallet.data.chains.helpers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/** Pins `Compact<T>` to `@polkadot/util`'s `compactToU8a` at every mode boundary. */
class SubstrateScaleTest {

    private fun compact(value: Long) = hex(SubstrateScale.compact(BigInteger.valueOf(value)))

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `single-byte mode covers 0 to 63`() {
        compact(0) shouldBe "00"
        compact(1) shouldBe "04"
        compact(63) shouldBe "fc"
    }

    @Test
    fun `two-byte mode covers 64 to 16383`() {
        compact(64) shouldBe "0101"
        compact(71) shouldBe "1d01"
        compact(16_383) shouldBe "fdff"
    }

    @Test
    fun `four-byte mode covers 16384 to 2^30 - 1`() {
        compact(16_384) shouldBe "02000100"
        compact(1_234_567) shouldBe "1e5a4b00"
        compact(1_073_741_823) shouldBe "feffffff"
    }

    @Test
    fun `big-integer mode carries the minimal little-endian magnitude behind a length byte`() {
        compact(1_073_741_824) shouldBe "0300000040"
        hex(SubstrateScale.compact(BigInteger.TWO.pow(40))) shouldBe "0b000000000001"
        hex(SubstrateScale.compact(BigInteger.TWO.pow(127))) shouldBe
            "3300000000000000000000000000000080"
        hex(SubstrateScale.compact(BigInteger.TWO.pow(128) - BigInteger.ONE)) shouldBe
            "33ffffffffffffffffffffffffffffffff"
    }

    @Test
    fun `a negative value has no compact spelling`() {
        shouldThrow<IllegalArgumentException> { SubstrateScale.compact(BigInteger.valueOf(-1)) }
    }

    @Test
    fun `u32 is little-endian`() {
        hex(SubstrateScale.u32LE(26)) shouldBe "1a000000"
        hex(SubstrateScale.u32LE(1_003_256)) shouldBe "f84e0f00"
        hex(SubstrateScale.u32LE(0xFFFF_FFFFL)) shouldBe "ffffffff"
    }
}
