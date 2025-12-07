package com.vultisig.wallet.data.utils

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

class BigIntegerSerializerImplTest {

    private val serializer = BigIntegerSerializerImpl()
    private val json = Json

    @Serializable
    data class TestData(
        @Serializable(with = BigIntegerSerializerImpl::class)
        val value: BigInteger
    )

    @Test
    fun `serialize simple integer`() {
        val data = TestData(BigInteger("12345"))
        val result = json.encodeToString(TestData.serializer(), data)
        assertEquals("""{"value":"12345"}""", result)
    }

    @Test
    fun `deserialize simple integer`() {
        val jsonString = """{"value":"12345"}"""
        val result = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigInteger("12345"), result.value)
    }

    @Test
    fun `handles zero`() {
        val data = TestData(BigInteger.ZERO)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigInteger.ZERO, deserialized.value)
    }

    @Test
    fun `handles one`() {
        val data = TestData(BigInteger.ONE)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(BigInteger.ONE, deserialized.value)
    }

    @Test
    fun `handles negative numbers`() {
        val negative = BigInteger("-987654321")
        val data = TestData(negative)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(negative, deserialized.value)
    }

    @Test
    fun `handles numbers larger than Long MAX_VALUE`() {
        val largerThanLong = BigInteger("9223372036854775808") // Long.MAX_VALUE + 1
        val data = TestData(largerThanLong)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(largerThanLong, deserialized.value)
    }

    @Test
    fun `handles numbers smaller than Long MIN_VALUE`() {
        val smallerThanLong = BigInteger("-9223372036854775809") // Long.MIN_VALUE - 1
        val data = TestData(smallerThanLong)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(smallerThanLong, deserialized.value)
    }

    @Test
    fun `throws on invalid input`() {
        val invalidJson = """{"value":"not-a-number"}"""
        assertThrows<NumberFormatException> {
            json.decodeFromString(TestData.serializer(), invalidJson)
        }
    }

    @Test
    fun `descriptor has correct properties`() {
        assertEquals("BigInteger", serializer.descriptor.serialName)
        assertEquals(PrimitiveKind.STRING, serializer.descriptor.kind)
    }

    // Cryptocurrency-level tests

    @Test
    fun `handles Bitcoin total supply in satoshis`() {
        // 21 million BTC = 2,100,000,000,000,000 satoshis
        val totalSatoshis = BigInteger("2100000000000000")
        val data = TestData(totalSatoshis)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(totalSatoshis, deserialized.value)
    }

    @Test
    fun `handles Ethereum total supply in Wei`() {
        // ~120 million ETH in Wei (18 decimals)
        val ethInWei = BigInteger("120000000000000000000000000")
        val data = TestData(ethInWei)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(ethInWei, deserialized.value)
    }

    @Test
    fun `handles 256-bit unsigned integer (common in smart contracts)`() {
        // 2^256 - 1 (max uint256 in Solidity)
        val maxUint256 = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935")
        val data = TestData(maxUint256)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(maxUint256, deserialized.value)
    }

    @Test
    fun `handles token amounts with 18 decimals (ERC20 standard)`() {
        // 1 billion tokens with 18 decimals
        val tokenAmount = BigInteger("1000000000000000000000000000")
        val data = TestData(tokenAmount)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(tokenAmount, deserialized.value)
    }

    @Test
    fun `handles SHIB total supply`() {
        // Shiba Inu has 1 quadrillion tokens
        val shibSupply = BigInteger("1000000000000000")
        val data = TestData(shibSupply)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(shibSupply, deserialized.value)
    }

    @Test
    fun `handles very large NFT token IDs`() {
        // Some NFT collections use large random token IDs
        val largeTokenId = BigInteger("88888888888888888888888888888888")
        val data = TestData(largeTokenId)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(largeTokenId, deserialized.value)
    }

    @Test
    fun `handles blockchain block numbers far in future`() {
        val futureBlock = BigInteger("999999999999999999")
        val data = TestData(futureBlock)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(futureBlock, deserialized.value)
    }

    @Test
    fun `handles wallet balance in smallest units`() {
        // Large wallet with precise amount in Wei
        val largeBalance = BigInteger("123456789012345678901234567890")
        val data = TestData(largeBalance)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(largeBalance, deserialized.value)
    }

    @Test
    fun `handles cryptographic hash as number`() {
        // SHA-256 hash represented as number (256 bits)
        val hashAsNumber = BigInteger("109418989131512359209847712234789012345678901234567890123456789012345678901")
        val data = TestData(hashAsNumber)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(hashAsNumber, deserialized.value)
    }

    @Test
    fun `handles powers of 2 up to 512 bits`() {
        // 2^512 (used in some cryptographic contexts)
        val power512 = BigInteger("2").pow(512)
        val data = TestData(power512)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(power512, deserialized.value)
    }

    @Test
    fun `round trip crypto amounts maintain exact value`() {
        val cryptoValues = listOf(
            "2100000000000000", // BTC in satoshis
            "120000000000000000000000000", // ETH in Wei
            "115792089237316195423570985008687907853269984665640564039457584007913129639935", // max uint256
            "1000000000000000000000000000", // 1B tokens with 18 decimals
            "999999999999999999999999999999999999", // Arbitrary large number
            "12345678901234567890123456789012345678901234567890" // 50 digit number
        )

        cryptoValues.forEach { value ->
            val original = BigInteger(value)
            val data = TestData(original)
            val serialized = json.encodeToString(TestData.serializer(), data)
            val deserialized = json.decodeFromString(TestData.serializer(), serialized)
            assertEquals(original, deserialized.value, "Failed for value: $value")
        }
    }

    @Test
    fun `handles DeFi total value locked calculations`() {
        // TVL in smallest units (e.g., $100 billion in 18-decimal token)
        val tvl = BigInteger("100000000000000000000000000000")
        val data = TestData(tvl)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(tvl, deserialized.value)
    }

    @Test
    fun `handles governance voting power`() {
        // Large voting power for DAO governance
        val votingPower = BigInteger("999999999999999999999999")
        val data = TestData(votingPower)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(votingPower, deserialized.value)
    }

    @Test
    fun `handles staking rewards calculations`() {
        // Accumulated staking rewards over time
        val rewards = BigInteger("87654321098765432109876543210")
        val data = TestData(rewards)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(rewards, deserialized.value)
    }

    @Test
    fun `handles numbers with 100 digits`() {
        val hundredDigits = BigInteger("1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")
        val data = TestData(hundredDigits)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(hundredDigits, deserialized.value)
    }

    @Test
    fun `handles factorial of large numbers`() {
        // 50! = huge number used in probability calculations
        val factorial50 = BigInteger("30414093201713378043612608166064768844377641568960512000000000000")
        val data = TestData(factorial50)
        val jsonString = json.encodeToString(TestData.serializer(), data)
        val deserialized = json.decodeFromString(TestData.serializer(), jsonString)
        assertEquals(factorial50, deserialized.value)
    }
}