package com.vultisig.wallet.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vultisig.wallet.common.UiText.DynamicString
import com.vultisig.wallet.common.UiText.StringResource


sealed class UiText {
    data class DynamicString(val text: String) : UiText()
    data class StringResource(val resId: Int) : UiText()
}

@Composable
fun UiText.asString(): String {
    val context = LocalContext.current
    return when (this) {
        is DynamicString -> text
        is StringResource -> context.getString(resId)
    }
}

fun UiText.asString(context: Context): String {
    return when (this) {
        is DynamicString -> text
        is StringResource -> context.getString(resId)
    }
}

infix fun String?.or(resId: Int): UiText {
    return if (this.isNullOrBlank())
        StringResource(resId)
    else DynamicString(this)
}
