package com.vultisig.wallet.data.chains.helpers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import vultisig.keysign.v1.CustomMessagePayload

class BitcoinSignedMessageTest {

    private val prefix = byteArrayOf(0x18) + "Bitcoin Signed Message:\n".toByteArray()

    private fun compactSize(length: Int): ByteArray =
        when {
            length < 0xfd -> byteArrayOf(length.toByte())
            length <= 0xffff -> byteArrayOf(0xfd.toByte(), length.toByte(), (length shr 8).toByte())
            else ->
                byteArrayOf(
                    0xfe.toByte(),
                    length.toByte(),
                    (length shr 8).toByte(),
                    (length shr 16).toByte(),
                    (length shr 24).toByte(),
                )
        }

    private fun hex(bytes: ByteArray) = "0x" + bytes.toHexString()

    private fun envelopeBytes(message: String): ByteArray {
        val body = message.toByteArray()
        return prefix + compactSize(body.size) + body
    }

    private fun envelope(message: String) = hex(envelopeBytes(message))

    private fun payload(
        message: String,
        method: String = "sign_message",
        chain: String? = "Bitcoin",
    ) = CustomMessagePayload(method = method, message = message, chain = chain)

    private fun digestOf(message: String) =
        SigningHelper.getKeysignMessages(payload(envelope(message))).single()

    @Test
    fun `digest matches the bitcoinjs-message fixtures`() {
        digestOf("") shouldBe "80e795d4a4caadd7047af389d9f7f220562feb6196032e2131e10563352c4bcc"
        digestOf("Vires is Numeris") shouldBe
            "f8a5affbef4a3241b19067aa694562f64f513310817297089a8929a930f4f933"
        digestOf("Virès is Numéris") shouldBe
            "af3d51b82a6694d76af5b49401d6f824d66cfce6f96213e606e7da95fe675f25"
    }

    @ParameterizedTest
    @CsvSource(
        "252, b7b164ef991d52735c6bb888642ad7eb6b6939dc984a7fceff4376be041d142f",
        "253, df167ad249ff5837e6acada677118b2ecc6757ab4cdade39caead99ef0220230",
        "65535, fade4e6ebe191b9dcf869e37c4ab6a2d5f9ffc1160fbfb84370afb579af7de8d",
        "65536, d5db7ae9446693355e5674d5d17e7b0a29f13fc174055077d9613e9ab2b462fe",
    )
    fun `digest matches the extension at each CompactSize boundary`(length: Int, expected: String) {
        digestOf("a".repeat(length)) shouldBe expected
    }

    @Test
    fun `a 300-character message uses the 3-byte CompactSize`() {
        val message = "a".repeat(300)
        envelopeBytes(message)[prefix.size] shouldBe 0xfd.toByte()
        digestOf(message) shouldBe
            "3ec158a43b80359df647352dac1d37dbf26a94e5f06e5790760290c75cd11dc0"
        BitcoinSignedMessage.displayText(envelope(message)) shouldBe message
    }

    @Test
    fun `chain match is case-insensitive`() {
        val expected = digestOf("hello")
        SigningHelper.getKeysignMessages(payload(envelope("hello"), chain = "bitcoin")) shouldBe
            listOf(expected)
        BitcoinSignedMessage.isBitcoinMessage(
            payload(envelope("hello"), chain = "BITCOIN")
        ) shouldBe true
    }

    @Test
    fun `digest runs ahead of method-based branches`() {
        val expected = digestOf("hello")
        listOf("personal_sign", "eth_signTypedData_v4", "anything").forEach { method ->
            SigningHelper.getKeysignMessages(payload(envelope("hello"), method = method)) shouldBe
                listOf(expected)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "0xdeadbeef", "0xnot hex", "hello 🌍\nسلام"])
    fun `round-trips a message through the envelope and display`(message: String) {
        val wrapped = envelope(message)
        BitcoinSignedMessage.isWellFormed(wrapped) shouldBe true
        BitcoinSignedMessage.displayText(wrapped) shouldBe message
    }

    @Test
    fun `display falls back to hex for invalid UTF-8`() {
        val inner = byteArrayOf(0xff.toByte(), 0xfe.toByte())
        BitcoinSignedMessage.displayText(hex(prefix + compactSize(inner.size) + inner)) shouldBe
            "0xfffe"
    }

    private fun assertRefused(message: String) {
        BitcoinSignedMessage.isWellFormed(message) shouldBe false
        BitcoinSignedMessage.digest(message) shouldBe null
        BitcoinSignedMessage.displayText(message) shouldBe null
        shouldThrow<IllegalStateException> {
            SigningHelper.getKeysignMessages(payload(message))
        }
    }

    @Test
    fun `refuses a bare message`() {
        assertRefused(hex("hello".toByteArray()))
    }

    @Test
    fun `refuses plain text, including a raw unencoded envelope`() {
        assertRefused("hello")
        assertRefused(String(envelopeBytes("hello"), Charsets.ISO_8859_1))
    }

    @Test
    fun `refuses a 32-byte digest`() {
        assertRefused(hex(ByteArray(32) { 1 }))
    }

    @Test
    fun `refuses a truncated envelope`() {
        val bytes = envelopeBytes("hello")
        assertRefused(hex(bytes.copyOf(bytes.size - 1)))
    }

    @Test
    fun `refuses the prefix alone`() {
        assertRefused(hex(prefix))
    }

    @Test
    fun `refuses trailing bytes`() {
        assertRefused(hex(envelopeBytes("hello") + 0))
    }

    @Test
    fun `refuses non-minimal length encodings`() {
        val body = "hello".toByteArray()
        assertRefused(hex(prefix + byteArrayOf(0xfd.toByte(), 5, 0) + body))
        assertRefused(hex(prefix + byteArrayOf(0xfe.toByte(), 5, 0, 0, 0) + body))
        val longBody = ByteArray(300) { 'a'.code.toByte() }
        assertRefused(hex(prefix + byteArrayOf(0xfe.toByte(), 0x2c, 0x01, 0, 0) + longBody))
    }

    @Test
    fun `refuses the 8-byte CompactSize marker`() {
        assertRefused(
            hex(prefix + byteArrayOf(0xff.toByte(), 5, 0, 0, 0, 0, 0, 0, 0) + "hello".toByteArray())
        )
    }

    @Test
    fun `refuses a truncated CompactSize`() {
        assertRefused(hex(prefix + byteArrayOf(0xfd.toByte(), 5)))
    }

    @Test
    fun `refuses the EIP-191 prefix`() {
        assertRefused(hex("\u0019Ethereum Signed Message:\n5hello".toByteArray()))
    }

    @Test
    fun `refuses malformed hex`() {
        val valid = envelope("hello")
        assertRefused(valid + "0")
        assertRefused(valid + "gg")
        assertRefused(valid.dropLast(2) + "+1")
        assertRefused(valid.dropLast(2) + "٣٣")
    }

    @Test
    fun `other chains are not treated as Bitcoin messages`() {
        BitcoinSignedMessage.isBitcoinMessage(payload("hello", chain = "Litecoin")) shouldBe false
        BitcoinSignedMessage.isBitcoinMessage(payload("hello", chain = null)) shouldBe false
    }
}
