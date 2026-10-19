package com.vultisig.wallet.data.chains.helpers

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import vultisig.keysign.v1.CustomMessagePayload

class EvmCustomMessageSignableTest {

    private fun signable(method: String, message: String, chain: String? = "Ethereum") =
        SigningHelper.isEvmSignable(CustomMessagePayload(method = method, message = message, chain = chain))

    private fun envelope(text: String, declared: Int = text.toByteArray().size) =
        "\u0019Ethereum Signed Message:\n$declared$text"

    @Test
    fun `accepts personal_sign envelope with ASCII message`() {
        signable("personal_sign", envelope("hello")) shouldBe true
    }

    @Test
    fun `accepts personal_sign envelope with multi-byte UTF-8 message`() {
        signable("personal_sign", envelope("héllo ✓")) shouldBe true
    }

    @Test
    fun `accepts hex personal_sign envelope whose message starts with digits`() {
        val hex = "0x" + envelope("123").toByteArray().joinToString("") { "%02x".format(it) }
        signable("personal_sign", hex) shouldBe true
    }

    @Test
    fun `accepts eth_signTypedData_v4`() {
        signable("eth_signTypedData_v4", "{}") shouldBe true
    }

    @Test
    fun `refuses unknown method`() {
        signable("eth_sign", envelope("hello")) shouldBe false
    }

    @Test
    fun `refuses personal_sign without envelope`() {
        signable("personal_sign", "hello") shouldBe false
    }

    @Test
    fun `refuses envelope with mismatched length`() {
        signable("personal_sign", envelope("hello", declared = 4)) shouldBe false
    }

    @Test
    fun `refuses unknown or missing chain as Ethereum`() {
        signable("foo", "0xdeadbeef", chain = null) shouldBe false
        signable("foo", "0xdeadbeef", chain = "NotAChain") shouldBe false
    }

    @Test
    fun `non-EVM chains are untouched`() {
        signable("anything", "hello", chain = "Bitcoin") shouldBe true
    }
}
