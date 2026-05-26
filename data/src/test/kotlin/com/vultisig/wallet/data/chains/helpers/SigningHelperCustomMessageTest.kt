@file:OptIn(ExperimentalStdlibApi::class)

package com.vultisig.wallet.data.chains.helpers

import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.common.toKeccak256ByteArray
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
    fun `hex-encoded message is decoded then keccak256-hashed for ECDSA chain`() {
        // "Vultisig" as hex
        val hexMessage = "56756c7469736967"
        val payload =
            CustomMessagePayload(method = "personal_sign", message = hexMessage, chain = "Ethereum")

        val messages = SigningHelper.getKeysignMessages(payload)

        val expected = hexMessage.toHexBytes().toKeccak256ByteArray().toHexString()
        messages shouldBe listOf(expected)
    }

    @Test
    fun `EdDSA chain message skips keccak256 and passes bytes through directly`() {
        // Solana is an EdDSA chain — the initiator sends a pre-computed digest
        val hexMessage = "abcdef0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d"
        val payload = CustomMessagePayload(method = "sign", message = hexMessage, chain = "Solana")

        val messages = SigningHelper.getKeysignMessages(payload)

        // No keccak256 applied — raw bytes are used as-is
        val expected = hexMessage.toHexBytes().toHexString()
        messages shouldBe listOf(expected)
    }

    @Test
    fun `ECDSA and EdDSA produce different keysign messages for the same hex input`() {
        val hexMessage = "abcdef0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d"

        val ecdsaPayload =
            CustomMessagePayload(method = "sign", message = hexMessage, chain = "Ethereum")
        val eddsaPayload =
            CustomMessagePayload(method = "sign", message = hexMessage, chain = "Solana")

        val ecdsaMessages = SigningHelper.getKeysignMessages(ecdsaPayload)
        val eddsaMessages = SigningHelper.getKeysignMessages(eddsaPayload)

        // ECDSA path applies keccak256; EdDSA path does not — so the hashes must differ
        ecdsaMessages shouldNotBe eddsaMessages
    }

    @Test
    fun `null chain defaults to ECDSA and applies keccak256`() {
        val payload = CustomMessagePayload(method = "sign", message = "test message", chain = null)

        val messages = SigningHelper.getKeysignMessages(payload)

        val expected = "test message".toByteArray().toKeccak256ByteArray().toHexString()
        messages shouldBe listOf(expected)
    }
}
