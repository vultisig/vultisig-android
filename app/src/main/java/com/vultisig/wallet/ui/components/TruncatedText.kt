package com.vultisig.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.max

@Composable
fun MiddleEllipsisText(
    text:String,
): String {
    val configuration= LocalConfiguration.current
    val screenWidthDp= configuration.screenWidthDp

    // Assume each character approximately takes 11 dp (this can be adjusted based on our font)
    val charWidthDp=11

    val maxLength= max(1,screenWidthDp/charWidthDp)
    return truncateMiddle(text,maxLength)
}

fun truncateMiddle(text :String,maxLength:Int,ellipsis: String="..." ):String{
    if(text.length<maxLength) return text
    val keepLength=maxLength-ellipsis.length
    val startLength=keepLength/2
    val endLength=keepLength-startLength
    return text.substring(0,startLength)+ellipsis+text.substring(text.length-endLength)
}