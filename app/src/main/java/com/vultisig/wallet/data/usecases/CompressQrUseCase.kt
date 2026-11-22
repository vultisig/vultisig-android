package com.vultisig.wallet.data.usecases

import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.SingleXZInputStream
import org.tukaani.xz.XZOutputStream

// Links carry small envelopes. Larger signing payloads are fetched from the bounded relay API.
internal const val MAX_SIGNING_LINK_CHARS = 64 * 1024

private const val MAX_COMPRESSED_QR_BYTES = 1024 * 1024
private const val MAX_QR_PROTO_BYTES = 4 * 1024 * 1024

// The default Android encoder and the iOS wire format use an 8 MiB dictionary. XZ's decoder
// limit also includes overhead and is expressed in KiB.
private const val MAX_XZ_DECODER_MEMORY_KIB = 16 * 1024

internal interface CompressQrUseCase : (ByteArray) -> ByteArray

internal class CompressQrUseCaseImpl @Inject constructor() : CompressQrUseCase {

    override fun invoke(input: ByteArray): ByteArray {
        require(input.size <= MAX_QR_PROTO_BYTES) { "Signing payload is too large" }
        return ByteArrayOutputStream().use { outputStream ->
            XZOutputStream(outputStream, LZMA2Options()).use { it.write(input) }
            require(outputStream.size() <= MAX_COMPRESSED_QR_BYTES) {
                "Compressed signing payload is too large"
            }
            outputStream.toByteArray()
        }
    }
}

internal interface DecompressQrUseCase : (ByteArray) -> ByteArray

internal class DecompressQrUseCaseImpl @Inject constructor() : DecompressQrUseCase {

    override fun invoke(input: ByteArray): ByteArray = decompress(input)

    internal fun decompress(
        input: ByteArray,
        inputLimit: Int = MAX_COMPRESSED_QR_BYTES,
        outputLimit: Int = MAX_QR_PROTO_BYTES,
        decoderMemoryLimitKiB: Int = MAX_XZ_DECODER_MEMORY_KIB,
    ): ByteArray {
        if (input.size > inputLimit) throw IOException("Compressed signing payload is too large")

        return SingleXZInputStream(input.inputStream(), decoderMemoryLimitKiB).use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                // Read one extra byte to distinguish an exact fit from an oversized payload.
                val remaining = outputLimit - output.size()
                val count = stream.read(buffer, 0, minOf(buffer.size, remaining + 1))
                if (count < 0) break
                if (count > remaining) throw IOException("Signing payload is too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
}
