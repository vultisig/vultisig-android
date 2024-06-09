package com.vultisig.wallet.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.vultisig.wallet.R

@Composable
internal fun digitStringToWords(@StringRes id: Int, vararg formatArgs: Any): String {
    val resources = LocalContext.current.resources;
    val formattedString = resources.getString(id, *formatArgs)
    val digitWords = resources.getStringArray(R.array.digit_words)

    return formattedString.map {
        when (it) {
            in '0'..'9' -> digitWords[it.toString().toInt()]
            else -> it.toString()
        }
    }.joinToString("")
}