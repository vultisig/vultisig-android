package com.vultisig.wallet.ui.models.sign

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class CustomMessageDecoderBitcoinTest {

    private val decoder =
        CustomMessageDecoder(
            fourByteRepository = mockk(),
            vaultRepository = mockk(),
            tokenMetadataResolver = mockk(),
            json = Json,
        )

    private fun envelope(message: ByteArray): String {
        val bytes =
            byteArrayOf(0x18) +
                "Bitcoin Signed Message:\n".toByteArray() +
                message.size.toByte() +
                message
        return "0x" + bytes.toHexString()
    }

    private suspend fun decodeBitcoin(message: String, method: String = "sign_message") =
        decoder.decode(method = method, message = message, chain = "Bitcoin")

    @Test
    fun `shows the text inside the envelope`() = runTest {
        decodeBitcoin(envelope("hello".toByteArray())) shouldBe DecodedCustomMessage.Text("hello")
    }

    @Test
    fun `chain match is case-insensitive`() = runTest {
        decoder.decode("sign_message", envelope("hello".toByteArray()), "bitcoin") shouldBe
            DecodedCustomMessage.Text("hello")
    }

    @Test
    fun `shows hex when the inner bytes are not valid UTF-8`() = runTest {
        decodeBitcoin(envelope(byteArrayOf(0xff.toByte(), 0xfe.toByte()))) shouldBe
            DecodedCustomMessage.Text("0xfffe")
    }

    @Test
    fun `reads the envelope ahead of the typed-data method`() = runTest {
        decodeBitcoin(envelope("hello".toByteArray()), method = "eth_signTypedData_v4") shouldBe
            DecodedCustomMessage.Text("hello")
    }

    @Test
    fun `gives no reading for a payload that is not an envelope`() = runTest {
        decodeBitcoin("0xdeadbeef") shouldBe null
        decodeBitcoin("hello") shouldBe null
    }
}
