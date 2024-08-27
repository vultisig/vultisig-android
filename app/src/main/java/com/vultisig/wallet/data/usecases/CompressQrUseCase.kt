package com.vultisig.wallet.data.usecases

import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.CompressorStreamProvider
import java.io.ByteArrayOutputStream
import javax.inject.Inject

private const val COMPRESSION_ALGO = CompressorStreamFactory.XZ

internal interface CompressQrUseCase : (ByteArray) -> ByteArray

internal class CompressQrUseCaseImpl @Inject constructor(
    private val compressorStreamProvider: CompressorStreamProvider
) : CompressQrUseCase {

    override fun invoke(input: ByteArray): ByteArray =
        ByteArrayOutputStream().use { outputStream ->
            compressorStreamProvider
                .createCompressorOutputStream(COMPRESSION_ALGO, outputStream)
                .use {
                    it.write(input)
                }
            outputStream.toByteArray()
        }


}

internal interface DecompressQrUseCase : (ByteArray) -> ByteArray

internal class DecompressQrUseCaseImpl @Inject constructor(
    private val compressorStreamProvider: CompressorStreamProvider
) : DecompressQrUseCase {

    override fun invoke(input: ByteArray): ByteArray =
        input.inputStream().use { inputStream ->
            compressorStreamProvider.createCompressorInputStream(
                COMPRESSION_ALGO, inputStream, false
            ).use {
                it.readBytes()
            }
        }

}
