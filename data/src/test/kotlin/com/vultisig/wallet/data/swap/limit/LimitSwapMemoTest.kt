package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class LimitSwapMemoTest {

    @Serializable
    private data class Fixture(
        val name: String,
        @SerialName("source_chain_kind") val sourceChainKind: String,
        @SerialName("affiliate_included") val affiliateIncluded: Boolean,
        val inputs: Inputs,
        @SerialName("expected_memo") val expectedMemo: String,
    )

    @Serializable
    private data class Inputs(
        @SerialName("source_asset") val sourceAsset: String,
        @SerialName("source_amount") val sourceAmount: Long,
        @SerialName("target_asset") val targetAsset: String,
        @SerialName("dest_addr") val destAddr: String,
        @SerialName("target_price") val targetPrice: JsonPrimitive,
        @SerialName("expiry_hours") val expiryHours: Int,
    )

    private val fixtures: List<Fixture> by lazy {
        val text =
            requireNotNull(javaClass.getResourceAsStream("/limit-swap/limit-swap-memos.json")) {
                    "Missing limit-swap memo fixture resource"
                }
                .bufferedReader()
                .use { it.readText() }
        Json.decodeFromString(text)
    }

    // The published SDK vectors are the source of truth for LIM math, interval blocks, and the
    // UTXO byte-cap affiliate drop. The only intentional deviation is the affiliate thorname
    // (`va` here vs the SDK's `v0`), asserted separately below.
    @TestFactory
    fun `matches SDK limit-swap memo fixtures`(): List<DynamicTest> =
        fixtures.map { fixture ->
            DynamicTest.dynamicTest(fixture.name) {
                val memo =
                    LimitSwapMemo.build(
                        sourceAsset = fixture.inputs.sourceAsset,
                        sourceAmount = BigInteger.valueOf(fixture.inputs.sourceAmount),
                        targetAsset = fixture.inputs.targetAsset,
                        destAddr = fixture.inputs.destAddr,
                        targetPrice = fixture.inputs.targetPrice.content,
                        expiryHours = fixture.inputs.expiryHours,
                    )
                assertEquals(fixture.expectedMemo, memo)
                assertEquals(fixture.affiliateIncluded, memo.contains(":va:50"))
            }
        }

    @Test
    fun `computes LIM with integer math and floors sub-1e8 remainders`() {
        assertEquals(
            BigInteger.valueOf(152_415_787L),
            LimitSwapMemo.getLimitAmount(BigInteger.valueOf(123_456_789L), "1.23456789"),
        )
    }

    @Test
    fun `accepts a tiny 8-decimal target price`() {
        assertEquals(
            BigInteger.ONE,
            LimitSwapMemo.getLimitAmount(BigInteger.valueOf(100_000_000L), "0.00000001"),
        )
    }

    @Test
    fun `rejects a source-price combination whose LIM floors to 0`() {
        // A zero trade target is read by THORChain as an unprotected market order.
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                LimitSwapMemo.getLimitAmount(BigInteger.valueOf(1_000_000L), "0.00000001")
            }
        assertTrue(error.message.orEmpty().contains("floors to 0"))
    }

    @Test
    fun `rejects a target price with more than 8 fractional digits`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                LimitSwapMemo.getLimitAmount(BigInteger.valueOf(100_000_000L), "1.123456789")
            }
        assertTrue(error.message.orEmpty().contains("at most 8 fractional digits"))
    }

    @Test
    fun `rejects a non-positive target price`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                LimitSwapMemo.getLimitAmount(BigInteger.valueOf(100_000_000L), "0")
            }
        assertTrue(error.message.orEmpty().contains("greater than 0"))
    }

    @Test
    fun `rejects unsupported source and target asset prefixes`() {
        assertThrows(IllegalArgumentException::class.java) {
            LimitSwapMemo.build(
                sourceAsset = "NOPE.NOPE",
                sourceAmount = BigInteger.valueOf(100_000_000L),
                targetAsset = "BTC.BTC",
                destAddr = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                targetPrice = "0.04",
                expiryHours = 24,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LimitSwapMemo.build(
                sourceAsset = "ETH.ETH",
                sourceAmount = BigInteger.valueOf(100_000_000L),
                targetAsset = "NOPE.NOPE",
                destAddr = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                targetPrice = "0.04",
                expiryHours = 24,
            )
        }
    }

    @Test
    fun `rejects an unsupported expiry`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                LimitSwapMemo.build(
                    sourceAsset = "ETH.ETH",
                    sourceAmount = BigInteger.valueOf(100_000_000L),
                    targetAsset = "BTC.BTC",
                    destAddr = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                    targetPrice = "0.04",
                    expiryHours = 6,
                )
            }
        assertTrue(error.message.orEmpty().contains("expiry_hours"))
    }

    @Test
    fun `rejects a destination address invalid for the target chain`() {
        // An EVM address cannot receive a BTC-target payout.
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                LimitSwapMemo.build(
                    sourceAsset = "ETH.ETH",
                    sourceAmount = BigInteger.valueOf(100_000_000L),
                    targetAsset = "BTC.BTC",
                    destAddr = "0x742d35Cc6634C0532925a3b844Bc9e7595f12345",
                    targetPrice = "0.04",
                    expiryHours = 24,
                )
            }
        assertTrue(error.message.orEmpty().contains("not a valid"))
    }

    // A shape-only regex would pass all four of these too; the point is that the checksum path
    // does not reject the ones that are genuinely valid. bc1p/ltc1p carry a bech32m checksum
    // (BIP-350), so validating every segwit form against the plain bech32 constant would strand
    // taproot payouts.
    @TestFactory
    fun `accepts checksum-valid segwit destinations`(): List<DynamicTest> =
        listOf(
                "BTC.BTC" to "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                "BTC.BTC" to "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0",
                "LTC.LTC" to "ltc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn3s44dy",
                "LTC.LTC" to "ltc1pqqqsyqcyq5rqwzqfpg9scrgwpugpzysnzs23v9ccrydpk8qarc0sts9tf8",
            )
            .map { (targetAsset, destAddr) ->
                DynamicTest.dynamicTest(destAddr) {
                    val memo = buildEthSourcedMemo(targetAsset, destAddr)
                    assertTrue(memo.contains(destAddr), "memo dropped the destination: $memo")
                }
            }

    @TestFactory
    fun `rejects bech32 destinations a shape-only check would let through`(): List<DynamicTest> =
        listOf(
                // Final checksum character flipped.
                "corrupted checksum" to "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlq",
                // Mixed case is invalid per BIP-173 even when the checksum itself is sound.
                "mixed case" to "BC1Qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                // Checksum-valid, but a 21-byte v0 program is neither P2WPKH nor P2WSH.
                "bad v0 program length" to "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysnzsf6edgu",
                // Taproot payload carrying a plain-bech32 checksum instead of bech32m.
                "v1 with bech32 checksum" to
                    "bc1pqqqsyqcyq5rqwzqfpg9scrgwpugpzysnzs23v9ccrydpk8qarc0sagmhkq",
            )
            .map { (name, destAddr) ->
                DynamicTest.dynamicTest(name) {
                    assertThrows(IllegalArgumentException::class.java) {
                        buildEthSourcedMemo("BTC.BTC", destAddr)
                    }
                }
            }

    private fun buildEthSourcedMemo(targetAsset: String, destAddr: String): String =
        LimitSwapMemo.build(
            sourceAsset = "ETH.ETH",
            sourceAmount = BigInteger.valueOf(100_000_000L),
            targetAsset = targetAsset,
            destAddr = destAddr,
            targetPrice = "0.04",
            expiryHours = 24,
        )

    @Test
    fun `rejects a destination address containing a memo separator`() {
        assertThrows(IllegalArgumentException::class.java) {
            LimitSwapMemo.build(
                sourceAsset = "ETH.ETH",
                sourceAmount = BigInteger.valueOf(100_000_000L),
                targetAsset = "THOR.RUNE",
                destAddr = "thor1abc:def",
                targetPrice = "10",
                expiryHours = 24,
            )
        }
    }

    @Test
    fun `applies the 80-byte UTXO cap for every UTXO source`() {
        listOf("BTC.BTC", "BCH.BCH", "DASH.DASH", "DOGE.DOGE", "LTC.LTC", "ZEC.ZEC").forEach {
            source ->
            val memo =
                LimitSwapMemo.build(
                    sourceAsset = source,
                    sourceAmount = BigInteger.valueOf(100_000_000L),
                    targetAsset = "ETH.ETH",
                    destAddr = "0x742d35Cc6634C0532925a3b844Bc9e7595f12345",
                    targetPrice = "16",
                    expiryHours = 24,
                )
            assertTrue(
                memo.toByteArray(Charsets.UTF_8).size <= LimitSwapMemo.UTXO_BYTE_LIMIT,
                "memo for $source exceeds the UTXO cap: $memo",
            )
        }
    }

    @Test
    fun `assertMemoByteLength measures UTF-8 bytes not string length`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                LimitSwapMemo.assertMemoByteLength(
                    "€".repeat(27),
                    LimitSwapMemo.UTXO_BYTE_LIMIT,
                    "utxo",
                )
            }
        assertTrue(error.message.orEmpty().contains("81 bytes"))
        assertTrue(error.message.orEmpty().contains("exceeding utxo limit 80"))
    }

    @Test
    fun `assertLimitSwapMemo accepts memos with and without an affiliate`() {
        LimitSwapMemo.assertLimitSwapMemo(
            "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:4000000/7200/0:va:50"
        )
        LimitSwapMemo.assertLimitSwapMemo(
            "=<:THOR.RUNE:thor1x2whgc2nt665y0kc44uywhynazvp0l8tp0vtu6:1000000000/14400/0"
        )
    }

    @Test
    fun `assertLimitSwapMemo rejects a market swap memo`() {
        // A `=>` (or `=:`) memo would sign a value-bearing deposit with no price protection.
        assertThrows(IllegalArgumentException::class.java) {
            LimitSwapMemo.assertLimitSwapMemo(
                "=:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"
            )
        }
    }

    @Test
    fun `assertLimitSwapMemo rejects a zero LIM`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                LimitSwapMemo.assertLimitSwapMemo(
                    "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:0/7200/0:va:50"
                )
            }
        assertTrue(error.message.orEmpty().contains("zero minimum-received"))
    }

    @Test
    fun `assertLimitSwapMemo rejects a malformed trade target and bad segment count`() {
        assertThrows(IllegalArgumentException::class.java) {
            LimitSwapMemo.assertLimitSwapMemo("=<:BTC.BTC:dest:notatarget:va:50")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LimitSwapMemo.assertLimitSwapMemo("=<:BTC.BTC:dest")
        }
    }

    @Test
    fun `parse recovers target asset, destination, LIM and expiry blocks`() {
        val memo = "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:4000000/14400/0:va:50"
        val parsed = checkNotNull(LimitSwapMemo.parse(memo)) { "failed to parse limit memo: $memo" }
        assertEquals("BTC.BTC", parsed.targetAsset)
        assertEquals("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh", parsed.destAddr)
        assertEquals(BigInteger.valueOf(4_000_000L), parsed.limit)
        assertEquals(14_400, parsed.expiryBlocks)
    }

    @Test
    fun `parse decodes the compressed LIM an iOS-placed order carries`() {
        // iOS shrinks the LIM to `<mantissa>e<exponent>` whenever that is shorter (`compressLim`),
        // so an order this device is asked to cosign spells the same floor a different way. Both
        // spellings must parse to the same integer, or the cosigner reads an enforced floor as a
        // market swap's expected output.
        val compressed =
            checkNotNull(
                LimitSwapMemo.parse(
                    "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:16e8/14400/0:va:50"
                )
            )
        val plain =
            checkNotNull(
                LimitSwapMemo.parse(
                    "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:1600000000/14400/0:va:50"
                )
            )

        assertEquals(BigInteger.valueOf(1_600_000_000L), compressed.limit)
        assertEquals(plain, compressed)
    }

    @Test
    fun `assertLimitSwapMemo accepts a compressed LIM and still rejects a zero one`() {
        LimitSwapMemo.assertLimitSwapMemo(
            "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:16e8/7200/0:va:50"
        )
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                LimitSwapMemo.assertLimitSwapMemo(
                    "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:0e8/7200/0:va:50"
                )
            }
        assertTrue(error.message.orEmpty().contains("zero minimum-received"))
    }

    @TestFactory
    fun `decodeLim reads both spellings and refuses everything else`(): List<DynamicTest> {
        val cases =
            listOf(
                "1600000000" to BigInteger.valueOf(1_600_000_000L),
                "16e8" to BigInteger.valueOf(1_600_000_000L),
                "16E8" to BigInteger.valueOf(1_600_000_000L),
                "544e6" to BigInteger.valueOf(544_000_000L),
                "0" to BigInteger.ZERO,
                // Decoded, not read as a floor: the callers reject a zero trade target with their
                // own message, because THORChain treats it as an unprotected market order.
                "0e8" to BigInteger.ZERO,
                "" to null,
                "e8" to null,
                "16e" to null,
                "1e2e3" to null,
                "16 e8" to null,
                "١٦" to null,
                // Raising ten to an exponent a memo picked must stay bounded, as must the digit
                // run handed to BigInteger; and THORNode itself refuses a LIM past 256 bits.
                "16e81" to null,
                "1".repeat(41) to null,
                "1e80" to null,
            )
        return cases.map { (field, expected) ->
            DynamicTest.dynamicTest("decodeLim(\"$field\") = $expected") {
                assertEquals(expected, LimitSwapMemo.decodeLim(field))
            }
        }
    }

    @Test
    fun `parse returns null for a non-limit memo`() {
        assertEquals(null, LimitSwapMemo.parse("=:BTC.BTC:dest:4000000"))
    }

    @Test
    fun `parse rejects a zero LIM or malformed trade target`() {
        // Must enforce the same grammar as assertLimitSwapMemo so an invalid memo is never
        // recorded.
        assertEquals(null, LimitSwapMemo.parse("=<:BTC.BTC:dest:0/14400/0:va:50"))
        assertEquals(null, LimitSwapMemo.parse("=<:BTC.BTC:dest:4000000/14400/notaquantity"))
        assertEquals(null, LimitSwapMemo.parse("=<:BTC.BTC:dest:4000000/14400/0:va:notbps"))
    }

    @Test
    fun `buildLimitSwapMemoForCoins rescales an 18-decimal source to match the SDK fixture`() {
        val memo =
            buildLimitSwapMemoForCoins(
                fromCoin = nativeCoin(Chain.Ethereum, "ETH", 18),
                toCoin = nativeCoin(Chain.Bitcoin, "BTC", 8),
                amount = BigInteger.TEN.pow(18),
                targetPrice = BigDecimal("0.04"),
                expiryHours = 12,
                destinationAddress = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
            )
        assertEquals(
            "=<:BTC.BTC:bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh:4000000/7200/0:va:50",
            memo,
        )
    }

    @Test
    fun `targetPrice inverts the LIM math a cosigner only has the memo for`() {
        // 1 ETH sold at 0.04 BTC/ETH — the fixture memo's LIM, read back from the wire.
        val sourceAmount = toThorchainFixedPoint(BigInteger.TEN.pow(18), 18)
        assertEquals(
            BigDecimal("0.04000000"),
            LimitSwapMemo.targetPrice(BigInteger.valueOf(4_000_000L), sourceAmount),
        )
    }

    @Test
    fun `targetPrice returns null for a zero LIM or zero source amount`() {
        assertEquals(null, LimitSwapMemo.targetPrice(BigInteger.ZERO, BigInteger.TEN))
        assertEquals(null, LimitSwapMemo.targetPrice(BigInteger.TEN, BigInteger.ZERO))
        // Floors below the memo's 8-decimal grid rather than reporting a price of zero.
        assertEquals(null, LimitSwapMemo.targetPrice(BigInteger.ONE, BigInteger.TEN.pow(9)))
    }

    @Test
    fun `expiryHours reverses intervalBlocks and rejects a foreign lifetime`() {
        limitSwapExpiryHours.forEach { hours ->
            assertEquals(hours, LimitSwapMemo.expiryHours(LimitSwapMemo.intervalBlocks(hours)))
        }
        assertEquals(null, LimitSwapMemo.expiryHours(1_234))
    }

    @Test
    fun `a built memo round-trips back into its target price and lifetime`() {
        val sourceAmount = BigInteger.valueOf(10_981L) // 0.00010981 BTC
        val memo =
            buildLimitSwapMemoForCoins(
                fromCoin = nativeCoin(Chain.Bitcoin, "BTC", 8),
                toCoin = nativeCoin(Chain.Ethereum, "ETH", 18),
                amount = sourceAmount,
                targetPrice = BigDecimal("35.05144"),
                expiryHours = 12,
                destinationAddress = "0x0cb1D4a24292bB89862f599Ac5B10F42b6DE07e4",
            )
        val parsed = checkNotNull(LimitSwapMemo.parse(memo)) { "failed to parse limit memo: $memo" }
        assertEquals(12, LimitSwapMemo.expiryHours(parsed.expiryBlocks))
        // Recovered within one floored LIM unit of the placing device's price (#4154).
        val recovered =
            checkNotNull(
                LimitSwapMemo.targetPrice(parsed.limit, toThorchainFixedPoint(sourceAmount, 8))
            )
        assertTrue(
            (BigDecimal("35.05144") - recovered).abs() < BigDecimal("0.0001"),
            "recovered target price was $recovered",
        )
    }

    private fun nativeCoin(chain: Chain, ticker: String, decimals: Int) =
        Coin(
            chain = chain,
            ticker = ticker,
            logo = "",
            address = "",
            decimal = decimals,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )
}
