package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.models.Chain
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class Multicall3Test {

    @Test
    fun `addressFor returns canonical address for confirmed chains`() {
        assertEquals(Multicall3.CANONICAL_ADDRESS, Multicall3.addressFor(Chain.Ethereum))
        assertEquals(Multicall3.CANONICAL_ADDRESS, Multicall3.addressFor(Chain.Arbitrum))
        assertEquals(Multicall3.CANONICAL_ADDRESS, Multicall3.addressFor(Chain.Base))
    }

    @Test
    fun `addressFor returns null for chains without canonical Multicall3`() {
        // zkSync deploys Multicall3 at a non-canonical address; Hyperliquid is unconfirmed — both
        // must fall back to per-token reads.
        assertNull(Multicall3.addressFor(Chain.ZkSync))
        assertNull(Multicall3.addressFor(Chain.Hyperliquid))
        // Non-EVM chains never have Multicall3.
        assertNull(Multicall3.addressFor(Chain.Solana))
    }

    @Test
    fun `encodeAggregate3 carries the aggregate3 selector and array header`() {
        val encoded = Multicall3.encodeAggregate3(listOf(Multicall3.balanceOfCall(TOKEN, ADDRESS)))

        assertTrue(encoded.startsWith("0x82ad56cb"), "expected aggregate3 selector")
        val body = encoded.removePrefix("0x").substring(8) // drop 4-byte selector
        assertEquals(word(32), body.substring(0, 64), "offset to dynamic array")
        assertEquals(word(1), body.substring(64, 128), "array length")
        // The encoded calldata for balanceOf(ADDRESS) must be embedded.
        assertTrue(encoded.contains("70a08231"), "expected balanceOf selector in calldata")
        assertTrue(encoded.contains(ADDRESS.removePrefix("0x").lowercase()), "padded owner address")
    }

    @Test
    fun `decodeAggregate3 reads success flags and balance words`() {
        val response =
            "0x" +
                aggregate3Response(
                    listOf(true to BigInteger.valueOf(5), true to BigInteger.valueOf(10))
                )

        val results = Multicall3.decodeAggregate3(response)

        assertEquals(2, results.size)
        assertTrue(results[0].success)
        assertEquals(
            BigInteger.valueOf(5),
            Multicall3.decodeUint256WordOrNull(results[0].returnData),
        )
        assertTrue(results[1].success)
        assertEquals(
            BigInteger.valueOf(10),
            Multicall3.decodeUint256WordOrNull(results[1].returnData),
        )
    }

    @Test
    fun `decodeAggregate3 surfaces failed calls with empty return data`() {
        val response =
            "0x" +
                aggregate3Response(
                    listOf(
                        false to null, // failed call, empty bytes
                        true to BigInteger.valueOf(10),
                    )
                )

        val results = Multicall3.decodeAggregate3(response)

        assertEquals(2, results.size)
        assertFalse(results[0].success)
        assertTrue(results[1].success)
        assertEquals(
            BigInteger.valueOf(10),
            Multicall3.decodeUint256WordOrNull(results[1].returnData),
        )
    }

    @Test
    fun `decodeUint256WordOrNull treats blank data as a failed read, not zero`() {
        // Empty/malformed data is an undecodable success word: null so the caller omits it and
        // keeps the cached balance, rather than persisting a fake 0 (#5308).
        assertNull(Multicall3.decodeUint256WordOrNull("0x"))
        // A genuine zero balance is a full 32-byte zero word and still decodes to ZERO.
        assertEquals(BigInteger.ZERO, Multicall3.decodeUint256WordOrNull("0x" + word(0)))
        assertEquals(BigInteger.valueOf(255), Multicall3.decodeUint256WordOrNull("0x" + word(255)))
    }

    private companion object {
        const val ADDRESS = "0x1111111111111111111111111111111111111111"
        const val TOKEN = "0x2222222222222222222222222222222222222222"

        fun word(value: Long): String = BigInteger.valueOf(value).toString(16).padStart(64, '0')

        fun word(value: BigInteger): String = value.toString(16).padStart(64, '0')

        /**
         * Builds the ABI encoding of `(bool success, bytes returnData)[]` as Multicall3 returns.
         */
        fun aggregate3Response(entries: List<Pair<Boolean, BigInteger?>>): String {
            val elements =
                entries.map { (success, value) ->
                    buildString {
                        append(word(if (success) 1L else 0L))
                        append(word(64L)) // offset to bytes within tuple
                        if (value != null) {
                            append(word(32L)) // bytes length
                            append(word(value)) // 32-byte balance word
                        } else {
                            append(word(0L)) // empty bytes
                        }
                    }
                }

            val header = StringBuilder()
            header.append(word(32L)) // offset to array
            header.append(word(entries.size.toLong())) // array length

            val heads = StringBuilder()
            val tails = StringBuilder()
            var offsetBytes = entries.size * 32
            for (element in elements) {
                heads.append(word(offsetBytes.toLong()))
                tails.append(element)
                offsetBytes += element.length / 2
            }
            return header.toString() + heads + tails
        }
    }
}
