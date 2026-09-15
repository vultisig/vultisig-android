package com.vultisig.wallet.data.usecases

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class CompressQrUseCaseTest {

    private val compress = CompressQrUseCaseImpl()
    private val decompress = DecompressQrUseCaseImpl()

    private val payload =
        """{"sessionId":"3f0a9c2e-7b1d-4e6a-9c8f-1d2e3f4a5b6c","serviceName":"Vultisig-Android-1234","useVultisigRelay":true}"""
            .encodeToByteArray()

    @Test
    fun `compress then decompress round-trips`() {
        assertArrayEquals(payload, decompress(compress(payload)))
    }

    @Test
    fun `decompress reads a payload compressed by an older Android build`() {
        assertArrayEquals(payload, decompress(COMMONS_COMPRESS_PAYLOAD.hexToByteArray()))
    }

    @Test
    fun `decompress reads a payload compressed by iOS`() {
        assertArrayEquals(payload, decompress(FOUNDATION_LZMA_PAYLOAD.hexToByteArray()))
    }

    @Test
    fun `decompress ignores bytes after the first xz stream`() {
        assertArrayEquals(payload, decompress(compress(payload) + byteArrayOf(0x41, 0x42, 0x43)))
    }

    private companion object {
        /** `payload` as commons-compress 1.28.0 `CompressorStreamFactory.XZ` wrote it. */
        const val COMMONS_COMPRESS_PAYLOAD =
            "fd377a585a000004e6d6b4460200210116000000742fe5a30100717b2273657373696f6e4964223a2233" +
                "663061396332652d376231642d346536612d396338662d316432653366346135623663222c227365" +
                "72766963654e616d65223a2256756c74697369672d416e64726f69642d31323334222c2275736556" +
                "756c746973696752656c6179223a747275657d0000005086b837f82c316600018a0172000000c4a2" +
                "2880b1c467fb020000000004595a"

        /** `payload` as Foundation's `NSData.compressed(using: .lzma)` wrote it. */
        const val FOUNDATION_LZMA_PAYLOAD =
            "fd377a585a000000ff12d9410200210116000000742fe5a3e00071006b5d003d888a669460dbebdc9d" +
                "c0e40147d93979fcd6536076945e6de67685c73f3957f0037a5b74b431dfab90e6f0fdce7b3d8c16" +
                "9ea4d4a975a5c91941ecf4aeda54778e2251c8e32ee1b22d95ef2c73d6938b14b0eced8387eaa7d6" +
                "45d394219fec752f0875aee3154159f800000000017f7202c02a3606729e7a010000000000595a"
    }
}
