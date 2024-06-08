package com.vultisig.wallet.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun digitStringToWords(@StringRes id: Int, vararg formatArgs: Any): String {
    val resources = LocalContext.current.resources;
    val formattedString = resources.getString(id, *formatArgs)
    return formattedString.map {
        when (it) {
            '0' -> "zero"
            '1' -> "one"
            '2' -> "two"
            '3' -> "three"
            '4' -> "four"
            '5' -> "five"
            '6' -> "six"
            '7' -> "seven"
            '8' -> "eight"
            '9' -> "nine"
            else -> it.toString()
        }
    }.joinToString("")
}
