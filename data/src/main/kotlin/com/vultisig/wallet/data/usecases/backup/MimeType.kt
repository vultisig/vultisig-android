package com.vultisig.wallet.data.usecases.backup

import com.vultisig.wallet.data.usecases.backup.MimeType.OCTET_STREAM
import com.vultisig.wallet.data.usecases.backup.MimeType.ZIP

enum class MimeType(val value: String) {
    OCTET_STREAM(OCTET_STREAM_VALUE),
    ZIP(ZIP_VALUE);
}

fun String.toMimeType() = when (this) {
    OCTET_STREAM_VALUE -> OCTET_STREAM
    ZIP_VALUE -> ZIP
    else -> error("invalid mimetype")
}


private const val OCTET_STREAM_VALUE = "application/octet-stream"
private const val ZIP_VALUE = "application/zip"