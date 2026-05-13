package com.vultisig.wallet.ui.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vultisig.wallet.ui.utils.UiText.DynamicString
import com.vultisig.wallet.ui.utils.UiText.FormattedText
import com.vultisig.wallet.ui.utils.UiText.PluralText
import com.vultisig.wallet.ui.utils.UiText.StringResource

sealed class UiText {
    data class DynamicString(val text: String) : UiText()

    data class StringResource(val resId: Int) : UiText()

    data class FormattedText(val resId: Int, val formatArgs: List<Any>) : UiText()

    data class PluralText(
        val resId: Int,
        val quantity: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UiText()

    companion object {
        val Empty: DynamicString = DynamicString("")
    }
}

internal fun Int.asUiText(vararg args: Any): UiText = FormattedText(this, args.toList())

fun Int.asUiText(): UiText = StringResource(this)

fun String.asUiText(): UiText = DynamicString(this)

@Composable
fun UiText.asString(): String {
    return when (this) {
        is DynamicString -> text
        is StringResource -> stringResource(resId)
        is FormattedText -> stringResource(resId, *formatArgs.toTypedArray())
        is PluralText -> pluralStringResource(resId, quantity, *formatArgs.toTypedArray())
    }
}

fun UiText.asString(context: Context): String {
    return when (this) {
        is DynamicString -> text
        is StringResource -> context.getString(resId)
        is FormattedText -> context.getString(resId, *formatArgs.toTypedArray())
        is PluralText ->
            context.resources.getQuantityString(resId, quantity, *formatArgs.toTypedArray())
    }
}

infix fun String?.or(resId: Int): UiText {
    return if (this.isNullOrBlank()) StringResource(resId) else DynamicString(this)
}
