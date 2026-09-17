package com.vultisig.wallet.data.chains.helpers

import com.google.protobuf.ByteString
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wallet.core.jni.proto.Common.SigningError
import wallet.core.jni.proto.EthereumRlp.EncodingOutput

/**
 * The envelope step runs on wallet-core's parsed [EncodingOutput], so it can be exercised on the
 * JVM without the native `EthereumRlp.encode` call. What matters is that a failed encode — which
 * wallet-core reports through `error` with an empty `encoded` — never collapses into a 1-byte
 * `[0x02]` payload that looks like a successful (tiny) transaction to the L1 fee oracle.
 */
internal class EthereumRlpEncoderTest {

    @Test
    fun `prefixes a successful encode with the type-2 marker`() {
        val rlp = byteArrayOf(0xC3.toByte(), 0x01, 0x02, 0x03)
        val output =
            EncodingOutput.newBuilder()
                .setError(SigningError.OK)
                .setEncoded(ByteString.copyFrom(rlp))
                .build()

        val envelope = EthereumRlpEncoder.toEip1559Envelope(output)

        assertContentEquals(byteArrayOf(0x02) + rlp, envelope)
    }

    @Test
    fun `fails instead of returning a 1-byte payload when wallet-core reports an error`() {
        val output =
            EncodingOutput.newBuilder()
                .setError(SigningError.Error_invalid_address)
                .setErrorMessage("Invalid address")
                .build()

        val e =
            assertThrows<IllegalArgumentException> { EthereumRlpEncoder.toEip1559Envelope(output) }

        assertTrue(e.message!!.contains("Error_invalid_address"), e.message)
        assertTrue(e.message!!.contains("Invalid address"), e.message)
    }
}
