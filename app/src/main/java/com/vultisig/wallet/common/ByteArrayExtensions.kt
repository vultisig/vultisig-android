package com.vultisig.wallet.common

import org.bouncycastle.jcajce.provider.digest.Keccak
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
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