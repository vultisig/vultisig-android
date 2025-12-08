package com.vultisig.wallet.data.utils

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class BigDecimalSerializerImplTest {

    private val serializer = BigDecimalSerializerImpl()
    private val json = Json

    @Serializable
    data class TestData(
        @Serializable(with = BigDecimalSerializerImpl::class)
        val amount: BigDecimal
    )

    @Test
    fun `serialize simple decimal`() {
        val data = TestData(BigDecimal("123.45"))
        val result = json.encodeToString(TestData.serializer(), data)
        assertEquals("""{"amount":"123.45"}""", result)
    }

    @Test
    fun `deserialize simple decimal`() {
        val jsonString = """{"amount":"123.45"}"""
        val result = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigDecimal("123.45"), result.amount)
    }

    @Test
    fun `deserialize simple decimal2`() {
        val jsonString = """{"amount":123.45}"""
        val result = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigDecimal("123.45"), result.amount)
    }

    @Test
    fun `deserialize simple decimal3`() {
        val jsonString = """{"amount":999999999999999999999999999999.99}"""
        val result = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigDecimal("999999999999999999999999999999.99"), result.amount)
    }

    @Test
    fun `deserialize simple decimal4`() {
        val jsonString = """{"amount":0.00000000000000000000000000000000000000000000000001}"""
        val result = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigDecimal("0.00000000000000000000000000000000000000000000000001"), result.amount)
    }

    @Test
    fun `deserialize simple decimal5`() {
        val jsonString = """{"amount":1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.12345678901234567890123456789012345678901234567890}"""
        val result = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigDecimal("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.12345678901234567890123456789012345678901234567890"), result.amount)
    }

    @Test
    fun `preserves high precision values`() {
        val highPrecision = BigDecimal("123.456789012345678901234567890")
        val data = TestData(highPrecision)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(highPrecision, deserialized.amount)
    }

    @Test
    fun `preserves very large numbers`() {
        val largeNumber = BigDecimal("999999999999999999999999999999.99")
        val data = TestData(largeNumber)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(largeNumber, deserialized.amount)
    }

    @Test
    fun `preserves very small numbers`() {
        val smallNumber = BigDecimal("0.000000000000000000000000001")
        val data = TestData(smallNumber)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(smallNumber, deserialized.amount)
    }

    @Test
    fun `handles zero`() {
        val data = TestData(BigDecimal.ZERO)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigDecimal.ZERO, deserialized.amount)
    }

    @Test
    fun `handles negative numbers`() {
        val negative = BigDecimal("-987.654")
        val data = TestData(negative)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(negative, deserialized.amount)
    }

    @Test
    fun `handles scientific notation input`() {
        val jsonString = """{"amount":"1.23E+10"}"""
        val result = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigDecimal("1.23E+10"), result.amount)
    }

    @Test
    fun `serializes without scientific notation`() {
        val data = TestData(BigDecimal("12300000000"))
        val result = json.encodeToString(TestData.serializer(), data)
        assertEquals("""{"amount":"12300000000"}""", result)
    }

    @Test
    fun `preserves trailing zeros`() {
        val withTrailingZeros = BigDecimal("100.00")
        val data = TestData(withTrailingZeros)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(withTrailingZeros, deserialized.amount)
    }

    @Test
    fun `round trip maintains equality`() {
        val testValues = listOf(
            "0",
            "1",
            "-1",
            "0.1",
            "999.999",
            "1234567890.0987654321",
            "-0.00001"
        )

        testValues.forEach { value ->
            val original = BigDecimal(value)
            val data = TestData(original)
            val serialized = json.encodeToString(TestData.serializer(), data)
            val deserialized = json.decodeFromString(TestData.serializer(), serialized)
            assertEquals(original, deserialized.amount, "Failed for value: $value")
        }
    }

    @Test
    fun `throws on invalid input`() {
        val invalidJson = """{"amount":"not-a-number"}"""
        assertThrows<NumberFormatException> {
            json.decodeFromString(TestData.serializer(), invalidJson)
        }
    }

    @Test
    fun `descriptor has correct properties`() {
        assertEquals("BigDecimal", serializer.descriptor.serialName)
        assertEquals(PrimitiveKind.STRING, serializer.descriptor.kind)
    }

    // Cryptocurrency-level precision tests

    @Test
    fun `handles Bitcoin precision (8 decimals)`() {
        val btcAmount = BigDecimal("0.12345678")
        val data = TestData(btcAmount)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(btcAmount, deserialized.amount)
    }

    @Test
    fun `handles satoshi level precision`() {
        val satoshi = BigDecimal("0.00000001") // 1 satoshi
        val data = TestData(satoshi)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(satoshi, deserialized.amount)
    }

    @Test
    fun `handles Ethereum precision (18 decimals)`() {
        val ethAmount = BigDecimal("1.123456789012345678")
        val data = TestData(ethAmount)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(ethAmount, deserialized.amount)
    }

    @Test
    fun `handles Wei level precision (smallest Ethereum unit)`() {
        val wei = BigDecimal("0.000000000000000001") // 1 wei
        val data = TestData(wei)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(wei, deserialized.amount)
    }

    @Test
    fun `handles large cryptocurrency wallet balance`() {
        val largeBtcBalance = BigDecimal("21000000.00000000") // Max BTC supply
        val data = TestData(largeBtcBalance)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(largeBtcBalance, deserialized.amount)
    }

    @Test
    fun `handles USDT USDC stablecoin precision (6 decimals)`() {
        val usdtAmount = BigDecimal("1000.123456")
        val data = TestData(usdtAmount)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(usdtAmount, deserialized.amount)
    }

    @Test
    fun `handles very small altcoin fractions`() {
        val tinyAmount = BigDecimal("0.000000000000000000123456789") // 27 decimals
        val data = TestData(tinyAmount)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(tinyAmount, deserialized.amount)
    }

    @Test
    fun `handles high-value crypto transaction`() {
        val highValue = BigDecimal("123456.123456789012345678") // Mix of large value + high precision
        val data = TestData(highValue)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(highValue, deserialized.amount)
    }

    @Test
    fun `handles exchange rate conversions with precision`() {
        val exchangeRate = BigDecimal("0.000045678901234567") // e.g., SHIB/USD rate
        val data = TestData(exchangeRate)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(exchangeRate, deserialized.amount)
    }

    @Test
    fun `handles gas fee calculations (Gwei)`() {
        val gwei = BigDecimal("0.000000123") // Gas price in ETH (123 Gwei)
        val data = TestData(gwei)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(gwei, deserialized.amount)
    }

    @Test
    fun `round trip crypto amounts maintain exact precision`() {
        val cryptoValues = listOf(
            "0.00000001", // 1 satoshi
            "0.000000000000000001", // 1 wei
            "21000000.00000000", // BTC max supply
            "1.123456789012345678", // ETH amount
            "1000.123456", // USDT amount
            "0.000000000123456789", // Micro altcoin
            "999999.999999999999999999" // Large precise amount
        )

        cryptoValues.forEach { value ->
            val original = BigDecimal(value)
            val data = TestData(original)
            val serialized = json.encodeToString(TestData.serializer(), data)
            val deserialized = json.decodeFromString(TestData.serializer(), serialized)
            assertEquals(original, deserialized.amount, "Failed for crypto value: $value")
        }
    }

    @Test
    fun `handles DeFi liquidity pool calculations`() {
        val liquidityAmount = BigDecimal("1234567.123456789012345678901234")
        val data = TestData(liquidityAmount)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(liquidityAmount, deserialized.amount)
    }

    // Very very small numbers tests

    @Test
    fun `handles extremely small fraction - 50 decimal places`() {
        val tinyFraction = BigDecimal("0.00000000000000000000000000000000000000000000000001")
        val data = TestData(tinyFraction)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(tinyFraction, deserialized.amount)
    }

    @Test
    fun `handles Planck constant level precision (scientific applications)`() {
        // Planck constant: 6.62607015 × 10^-34
        val planck = BigDecimal("0.000000000000000000000000000000000662607015")
        val data = TestData(planck)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(planck, deserialized.amount)
    }

    @Test
    fun `handles extremely low price altcoins`() {
        // Price like 0.00000000000012345 USD per token
        val microPrice = BigDecimal("0.00000000000012345")
        val data = TestData(microPrice)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(microPrice, deserialized.amount)
    }

    @Test
    fun `handles atomic unit calculations (100 decimals)`() {
        val atomic = BigDecimal("0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001")
        val data = TestData(atomic)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(atomic, deserialized.amount)
    }

    @Test
    fun `handles negative extremely small numbers`() {
        val negativeSmall = BigDecimal("-0.000000000000000000000000000001")
        val data = TestData(negativeSmall)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(negativeSmall, deserialized.amount)
    }

    // Very very big numbers tests

    @Test
    fun `handles googol (10^100)`() {
        val googol = BigDecimal("10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
        val data = TestData(googol)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(googol, deserialized.amount)
    }

    @Test
    fun `handles global GDP in smallest currency unit`() {
        // World GDP ~$100 trillion with cents = 10^16 cents
        val globalGdp = BigDecimal("10000000000000000.00")
        val data = TestData(globalGdp)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(globalGdp, deserialized.amount)
    }

    @Test
    fun `handles total cryptocurrency market cap in Wei`() {
        // $3 trillion market cap represented in Wei-like units (18 decimals)
        val marketCapWei = BigDecimal("3000000000000.000000000000000000")
        val data = TestData(marketCapWei)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(marketCapWei, deserialized.amount)
    }

    @Test
    fun `handles number with 150 total digits`() {
        // 100 digits before decimal, 50 after
        val massive = BigDecimal("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.12345678901234567890123456789012345678901234567890")
        val data = TestData(massive)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(massive, deserialized.amount)
    }

    @Test
    fun `handles avogadro number with decimals`() {
        // 6.022 × 10^23 with precision
        val avogadro = BigDecimal("602214076000000000000000.123456789")
        val data = TestData(avogadro)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(avogadro, deserialized.amount)
    }

    @Test
    fun `handles astronomical distances in Planck lengths`() {
        // Universe size in Planck lengths (massive number)
        val cosmological = BigDecimal("10000000000000000000000000000000000000000000000000000000000000.123")
        val data = TestData(cosmological)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(cosmological, deserialized.amount)
    }

    @Test
    fun `handles extremely large with extreme precision`() {
        // Large value with many decimal places (realistic for scientific calculations)
        val extremeBoth = BigDecimal("999999999999999999999999999999.999999999999999999999999999999")
        val data = TestData(extremeBoth)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(extremeBoth, deserialized.amount)
    }

    @Test
    fun `handles negative googol with precision`() {
        val negativeGoogol = BigDecimal("-10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000.123456789")
        val data = TestData(negativeGoogol)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(negativeGoogol, deserialized.amount)
    }

    @Test
    fun `handles financial derivatives notional value`() {
        // Global derivatives market ~$1 quadrillion with precision
        val derivatives = BigDecimal("1000000000000000.123456789012345678")
        val data = TestData(derivatives)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(derivatives, deserialized.amount)
    }

    @Test
    fun `handles hyperinflation currency amounts`() {
        // Zimbabwe dollar level amounts
        val hyperinflation = BigDecimal("100000000000000000000000000.50")
        val data = TestData(hyperinflation)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(hyperinflation, deserialized.amount)
    }

    @Test
    fun `round trip extreme values maintain exact precision`() {
        val extremeValues = listOf(
            "0.00000000000000000000000000000000000000000000000001", // 50 decimals
            "0.000000000000000000000000000000000662607015", // Planck-like
            "10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000", // Googol
            "999999999999999999999999999999.999999999999999999999999999999", // Large + precise
            "-10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000.123456789", // Negative googol
            "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890.12345678901234567890123456789012345678901234567890", // 150 digits
            "0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001" // 100 decimals
        )

        extremeValues.forEach { value ->
            val original = BigDecimal(value)
            val data = TestData(original)
            val serialized = json.encodeToString(TestData.serializer(), data)
            val deserialized = json.decodeFromString(TestData.serializer(), serialized)
            assertEquals(original, deserialized.amount, "Failed for extreme value: $value")
        }
    }

    @Test
    fun `handles scientific notation very small values`() {
        val scientificSmall = BigDecimal("1.23456789E-100")
        val data = TestData(scientificSmall)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(scientificSmall, deserialized.amount)
    }
}