package com.vultisig.wallet.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vultisig.wallet.common.UiText.DynamicString
import com.vultisig.wallet.common.UiText.FormattedText
import com.vultisig.wallet.common.UiText.StringResource


sealed class UiText {
    data class DynamicString(val text: String) : UiText()
    data class StringResource(val resId: Int) : UiText()
    data class FormattedText(
        val resId: Int,
        val formatArgs: List<Any>
    ) : UiText()

    companion object {
        val Empty = DynamicString("")
    }

}

internal fun Int.asUiText(vararg args: Any): UiText =
    FormattedText(this, args.toList())

internal fun Int.asUiText(): UiText =
    StringResource(this)

@Composable
fun UiText.asString(): String {
    val context = LocalContext.current
    return when (this) {
        is DynamicString -> text
        is StringResource -> context.getString(resId)
        is FormattedText -> context.getString(resId, *formatArgs.toTypedArray())
    }
}

fun UiText.asString(context: Context): String {
    return when (this) {
        is DynamicString -> text
        is StringResource -> context.getString(resId)
        is FormattedText -> context.getString(resId, formatArgs)
    }
}

infix fun String?.or(resId: Int): UiText {
    return if (this.isNullOrBlank())
        StringResource(resId)
    else DynamicString(this)
}
