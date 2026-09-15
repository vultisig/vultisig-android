package com.vultisig.wallet.data.usecases

import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.SingleXZInputStream
import org.tukaani.xz.XZOutputStream

internal interface CompressQrUseCase : (ByteArray) -> ByteArray

internal class CompressQrUseCaseImpl @Inject constructor() : CompressQrUseCase {

    override fun invoke(input: ByteArray): ByteArray =
        ByteArrayOutputStream().use { outputStream ->
            XZOutputStream(outputStream, LZMA2Options()).use { it.write(input) }
            outputStream.toByteArray()
        }
}

internal interface DecompressQrUseCase : (ByteArray) -> ByteArray

internal class DecompressQrUseCaseImpl @Inject constructor() : DecompressQrUseCase {

    override fun invoke(input: ByteArray): ByteArray =
        SingleXZInputStream(input.inputStream()).use { it.readBytes() }
}
