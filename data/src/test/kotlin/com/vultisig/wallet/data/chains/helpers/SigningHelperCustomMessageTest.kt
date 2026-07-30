@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.common.toKeccak256ByteArray
import com.vultisig.wallet.data.common.toSha256ByteArray
import com.vultisig.wallet.data.common.toSha512ByteArray
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CustomMessagePayload

class SigningHelperCustomMessageTest {

    @Test
    fun `plain text message is keccak256-hashed for ECDSA chain`() {
        val payload =
            CustomMessagePayload(
                method = "personal_sign",
                message = "Hello Vultisig",
                chain = "Ethereum",
            )

        val messages = SigningHelper.getKeysignMessages(payload)

        val expected = "Hello Vultisig".toByteArray().toKeccak256ByteArray().toHexString()
        messages shouldBe listOf(expected)
    }

    @Test
    fun `0x-prefixed hex message is decoded then keccak256-hashed for ECDSA chain`() {
        val hexMessage = "0x56756c7469736967"
        val payload =
            CustomMessagePayload(method = "personal_sign", message = hexMessage, chain = "Ethereum")

        val messages = SigningHelper.getKeysignMessages(payload)

        val expected = hexMessage.toHexBytes().toKeccak256ByteArray().toHexString()
        messages shouldBe listOf(expected)
    }

    @Test
    fun `hex-looking message without 0x prefix is treated as UTF-8 for ECDSA chain`() {
        // "56756c7469736967" is all hex characters but has no 0x prefix, so it must be hashed as
        // UTF-8 text to match iOS/Windows; decoding it as hex would diverge cross-platform.
        val message = "56756c7469736967"
        val payload =
            CustomMessagePayload(method = "personal_sign", message = message, chain = "Ethereum")

        val messages = SigningHelper.getKeysignMessages(payload)

        val expected = message.toByteArray().toKeccak256ByteArray().toHexString()
        messages shouldBe listOf(expected)
    }

    @Test
    fun `EdDSA chain message skips keccak256 and passes bytes through directly`() {
        val hexMessage = "0xabcdef0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d"
        val payload = CustomMessagePayload(method = "sign", message = hexMessage, chain = "Solana")

        val messages = SigningHelper.getKeysignMessages(payload)

        val expected = hexMessage.toHexBytes().toHexString()
        messages shouldBe listOf(expected)
    }

    @Test
    fun `cosmos-family chain message is sha256-hashed, not keccak256`() {
        // THORChain/Maya/Cosmos custom messages (Keplr ADR-36 signArbitrary) must be sha256'd
        // to match iOS/Windows/CLI; keccak256 here 404s cross-platform co-signing.
        val hexMessage = "0xabcdef0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d"
        val payload =
            CustomMessagePayload(method = "sign", message = hexMessage, chain = "THORChain")

        val messages = SigningHelper.getKeysignMessages(payload)

        messages shouldBe listOf(hexMessage.toHexBytes().toSha256ByteArray().toHexString())
        messages shouldNotBe listOf(hexMessage.toHexBytes().toKeccak256ByteArray().toHexString())
    }

    @Test
    fun `native Cosmos chain plain-text message is sha256-hashed`() {
        val payload =
            CustomMessagePayload(method = "sign", message = "Hello Vultisig", chain = "Cosmos")

        val messages = SigningHelper.getKeysignMessages(payload)

        val expected = "Hello Vultisig".toByteArray().toSha256ByteArray().toHexString()
        messages shouldBe listOf(expected)
    }

    @Test
    fun `Ripple plain-text message is SHA-512-half hashed, not keccak256`() {
        // XRPL (GemWallet signMessage) custom messages sign SHA-512-half — the first 32 bytes of
        // SHA-512 — of the message bytes, matching the vultisig-windows initiator
        // (getCustomMessageHex.ts `ripple` branch). keccak256 here 404s cross-platform co-signing.
        val payload =
            CustomMessagePayload(method = "sign", message = "Hello Vultisig", chain = "Ripple")

        val messages = SigningHelper.getKeysignMessages(payload)

        val expected = "Hello Vultisig".toByteArray().toSha512ByteArray().copyOf(32).toHexString()
        messages shouldBe listOf(expected)
        messages shouldNotBe
            listOf("Hello Vultisig".toByteArray().toKeccak256ByteArray().toHexString())
    }

    @Test
    fun `Ripple digest is byte-identical to the vultisig-windows extension contract`() {
        // Pinned reference vectors: SHA-512-half of the message bytes, computed independently of
        // the app helpers, matching vultisig-windows getCustomMessageHex.ts (windows#4399).
        val utf8Payload =
            CustomMessagePayload(method = "sign", message = "Hello Vultisig", chain = "Ripple")
        SigningHelper.getKeysignMessages(utf8Payload) shouldBe
            listOf("3bc2707498315edae0bf63ea75bf2433a0fdee71e888461b55fe9d05662ae437")

        // "0x56756c7469736967" decodes to the ASCII bytes of "Vultisig" before hashing.
        val hexPayload =
            CustomMessagePayload(method = "sign", message = "0x56756c7469736967", chain = "Ripple")
        SigningHelper.getKeysignMessages(hexPayload) shouldBe
            listOf("f969dba15308c6b017e02c91e06e577e8c69cb291bdf44a819f184ad66e627dc")
    }

    @Test
    fun `Sui digest is byte-identical to the vultisig-windows extension contract`() {
        // Pinned reference vectors produced by the canonical initiator implementation
        // (@vultisig/core-chain `getSuiPersonalMessageDigest`, which getCustomMessageHex.ts's
        // `sui` branch calls): blake2b_256([0x03,0x00,0x00] || uleb128(len) || message).
        val utf8Payload =
            CustomMessagePayload(
                method = "sui_signPersonalMessage",
                message = "Hello Vultisig",
                chain = "Sui",
            )
        SigningHelper.getKeysignMessages(utf8Payload) shouldBe
            listOf("4a140032aaf9e5699dc678b2caf2687349a2a6ac1103cdba6a2d3f462ca21182")

        // "0x56756c7469736967" decodes to the ASCII bytes of "Vultisig" before wrapping.
        val hexPayload =
            CustomMessagePayload(
                method = "sui_signPersonalMessage",
                message = "0x56756c7469736967",
                chain = "Sui",
            )
        SigningHelper.getKeysignMessages(hexPayload) shouldBe
            listOf("245f3ecbbcca443e43d26989bab5ba55cf03c6db0f9b28a4e12d072b894e83d6")
    }

    @Test
    fun `Sui message longer than 127 bytes uses a multi-byte uleb128 length prefix`() {
        // 200 bytes forces the BCS length prefix to spill to two bytes (0xC8 0x01). A single-byte
        // encoding would silently truncate the length and diverge from the initiator on exactly
        // the messages most likely to be real — dApp login challenges are routinely > 127 bytes.
        val payload =
            CustomMessagePayload(
                method = "sui_signPersonalMessage",
                message = "0x" + "41".repeat(200),
                chain = "Sui",
            )

        SigningHelper.getKeysignMessages(payload) shouldBe
            listOf("a90b2a8fe4149193029c26b2e2000730da6e64f6dee07ccf939142d02ef7ceb6")
    }

    @Test
    fun `Sui does not fall through to the raw EdDSA passthrough`() {
        // Sui's TssKeysignType is EdDSA, so without an explicit carve-out it would sign the raw
        // message bytes and never converge with the Windows initiator (#5442).
        val message = "Hello Vultisig"
        val payload =
            CustomMessagePayload(
                method = "sui_signPersonalMessage",
                message = message,
                chain = "Sui",
            )

        val messages = SigningHelper.getKeysignMessages(payload)

        messages shouldNotBe listOf(message.toByteArray().toHexString())
        messages shouldNotBe
            SigningHelper.getKeysignMessages(
                CustomMessagePayload(method = "sign", message = message, chain = "Solana")
            )
    }

    @Test
    fun `ECDSA and EdDSA produce different keysign messages for the same hex input`() {
        val hexMessage = "0xabcdef0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d"

        val ecdsaMessages =
            SigningHelper.getKeysignMessages(
                CustomMessagePayload(method = "sign", message = hexMessage, chain = "Ethereum")
            )
        val eddsaMessages =
            SigningHelper.getKeysignMessages(
                CustomMessagePayload(method = "sign", message = hexMessage, chain = "Solana")
            )

        ecdsaMessages shouldNotBe eddsaMessages
    }

    @Test
    fun `null chain defaults to ECDSA and applies keccak256`() {
        val payload = CustomMessagePayload(method = "sign", message = "test message", chain = null)

        val messages = SigningHelper.getKeysignMessages(payload)

        val expected = "test message".toByteArray().toKeccak256ByteArray().toHexString()
        messages shouldBe listOf(expected)
    }

    @Test
    fun `eth_signTypedData_v4 routes to hash function and returns hex of result`() {
        val stubbedHash = ByteArray(32) { it.toByte() }
        val payload =
            CustomMessagePayload(
                method = "eth_signTypedData_v4",
                message = """{"domain":{},"message":{}}""",
                chain = "Ethereum",
            )

        val messages = SigningHelper.getKeysignMessages(payload, typedDataHasher = { stubbedHash })

        messages shouldBe listOf(stubbedHash.toHexString())
    }

    @Test
    fun `eth_signTypedData_v4 method matching is case-insensitive`() {
        val stubbedHash = ByteArray(32) { 0xAB.toByte() }
        val payload =
            CustomMessagePayload(
                method = "ETH_SIGNTYPEDDATA_V4",
                message = """{"domain":{},"message":{}}""",
                chain = "Ethereum",
            )

        val messages = SigningHelper.getKeysignMessages(payload, typedDataHasher = { stubbedHash })

        messages shouldBe listOf(stubbedHash.toHexString())
    }

    @Test
    fun `eth_signTypedData_v4 throws when hash function returns null`() {
        val payload =
            CustomMessagePayload(
                method = "eth_signTypedData_v4",
                message = "invalid-typed-data",
                chain = "Ethereum",
            )

        shouldThrow<IllegalStateException> {
            SigningHelper.getKeysignMessages(payload, typedDataHasher = { null })
        }
    }

    @Test
    fun `eth_signTypedData_v4 throws when hash function returns empty array`() {
        val payload =
            CustomMessagePayload(
                method = "eth_signTypedData_v4",
                message = "invalid-typed-data",
                chain = "Ethereum",
            )

        shouldThrow<IllegalStateException> {
            SigningHelper.getKeysignMessages(payload, typedDataHasher = { ByteArray(0) })
        }
    }
}
