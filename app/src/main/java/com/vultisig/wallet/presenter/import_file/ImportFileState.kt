package com.vultisig.wallet.presenter.import_file

import android.net.Uri

data class ImportFileState(val fileUri: Uri? = null, val fileName: String? = null, val fileContent: ByteArray? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImportFileState

        if (fileUri != other.fileUri) return false
        if (fileName != other.fileName) return false
        if (fileContent != null) {
            if (other.fileContent == null) return false
            if (!fileContent.contentEquals(other.fileContent)) return false
        } else if (other.fileContent != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileUri?.hashCode() ?: 0
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (fileContent?.contentHashCode() ?: 0)
        return result
    }


}