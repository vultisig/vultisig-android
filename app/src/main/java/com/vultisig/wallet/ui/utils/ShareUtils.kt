package com.vultisig.wallet.ui.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.content.FileProvider
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.sha256
import com.vultisig.wallet.data.models.Vault
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

private const val DEFAULT_WIDTH = 500f

enum class ShareType {
    SEND, SWAP, KEYGEN, RESHARE, TOKENADDRESS
}

fun Context.share(bitmap: Bitmap, fileName: String) {
    try {
        val cachePath = File(cacheDir, "images")
        cachePath.mkdirs()
        val newFile = File(cachePath, fileName)

        FileOutputStream(newFile).use { stream ->
            val resizedBitmap = if (bitmap.width < DEFAULT_WIDTH) {
                val scaleFactor = DEFAULT_WIDTH / bitmap.width
                bitmap.getResizedBitmap(DEFAULT_WIDTH, bitmap.height * scaleFactor)
            } else bitmap

            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        }

        val contentUri = FileProvider.getUriForFile(
            this, "$packageName.provider", newFile
        )
        if (contentUri != null) {
            val shareIntent = Intent()
            shareIntent.setAction(Intent.ACTION_SEND)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareIntent.setDataAndType(contentUri, contentResolver.getType(contentUri))
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
            startActivity(
                Intent.createChooser(
                    shareIntent,
                    getString(R.string.share_qr_utils_choose_an_app)
                )
            )
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

 internal fun shareFileName(vault: Vault, shareType: ShareType): String {
    val uid =
        ("${vault.name} - ${vault.pubKeyECDSA} - " +
                "${vault.pubKeyEDDSA} - ${vault.hexChainCode}").sha256()

    return shareFileName(vault.name, uid, shareType)
}

internal fun shareVaultDetailName(vaultName: String, vaultPart: String) =
    "vultisig_detail_${vaultName}_${vaultPart}.png"

internal fun shareFileName(vaultName: String, uid: String, shareType: ShareType): String {
    val date = Date()
    val format = SimpleDateFormat(
        "yyyy-MM-dd-HH-mm-ss",
        java.util.Locale.getDefault()
    )
    val formattedDate = format.format(date)
    return "Vault${shareType.toStringValue()}-${vaultName}-${uid.takeLast(3)}-${formattedDate}.png"
}

private fun ShareType.toStringValue(): String {
    return when (this) {
        ShareType.SEND -> "Send"
        ShareType.SWAP -> "Swap"
        ShareType.KEYGEN -> "Keygen"
        ShareType.RESHARE -> "Reshare"
        ShareType.TOKENADDRESS -> "TokenAddress"
    }
}
private fun Bitmap.getResizedBitmap(
    newWidth: Float,
    newHeight: Float
): Bitmap {
    val scaleWidth = newWidth / width
    val scaleHeight = newHeight / height
    val matrix = Matrix()
    matrix.postScale(scaleWidth, scaleHeight)
    val resizedBitmap = Bitmap.createBitmap(
        this, 0, 0,
        width, height, matrix, false
    )
    return resizedBitmap
}