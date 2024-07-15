package com.vultisig.wallet.common

import org.bouncycastle.jcajce.provider.digest.Keccak
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun ByteArray.toKeccak256(): String {
    val digest = Keccak.Digest256()
    val hash = digest.digest(this)
    return Numeric.toHexString(hash)
}

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.zipAndBase64Encode(): String {
    val byteStream = ByteArrayOutputStream()
    GZIPOutputStream(byteStream).use { it.write(this) }
    val gzippedBytes = byteStream.toByteArray()
    return Base64.encode(gzippedBytes)
}

fun ByteArray.unzip(): String {
    val bais = ByteArrayInputStream(this)
    val gzis = GZIPInputStream(bais)
    val baos = ByteArrayOutputStream()

    val buffer = ByteArray(1024)
    var len: Int
    while (gzis.read(buffer).also { len = it } != -1) {
        baos.write(buffer, 0, len)
    }

    return baos.toString()
}

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.zipZlibAndBase64Encode(): String {
    val deflater = Deflater(5, true)
    deflater.setInput(this)

    val outputStream = ByteArrayOutputStream(this.size)
    deflater.finish()
    val buffer = ByteArray(1024)
    while (!deflater.finished()) {
        val count = deflater.deflate(buffer) // returns the generated code's length
        outputStream.write(buffer, 0, count)
    }
    outputStream.close()
    return Base64.encode(outputStream.toByteArray())
}

fun ByteArray.unzipZlib(): ByteArray {
    val inflater = Inflater(true)
    val outputStream = ByteArrayOutputStream()

    return outputStream.use {
        val buffer = ByteArray(1024)

        inflater.setInput(this)

        var count = -1
        while (count != 0) {
            count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        inflater.end()
        outputStream.toByteArray()
    }
}

internal fun ByteArray.buildString(): String {
    val sb = StringBuilder()
    for (char in this) {
        sb.append(char.toInt().toChar())
    }
    return sb.toString()
}