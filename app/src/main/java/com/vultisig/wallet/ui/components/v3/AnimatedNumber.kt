package com.vultisig.wallet.ui.components.v3

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle


@Composable
fun <T> AnimatedNumber(
    modifier: Modifier = Modifier,
    text: T,
    style: TextStyle,
    color: Color,
    formatter: (T) -> String = { it.toString() },
) where T : Number, T : Comparable<T> {

    var oldNumber by remember { mutableStateOf(text) }

    SideEffect {
        oldNumber = text
    }

    Row(modifier = modifier) {
        val numberString = formatter(text)
        val oldNumberString = formatter(oldNumber)
        for (i in numberString.indices) {
            val oldChar = oldNumberString.getOrNull(i)
            val newChar = numberString[i]

            val char = if (oldChar == newChar) {
                oldNumberString[i]
            } else {
                numberString[i]
            }
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    if (text > oldNumber) {
                        slideUp()
                    } else {
                        slideDown()
                    }
                },
                label = "animatedNumberChar"
            ) { targetChar ->
                Text(
                    text = targetChar.toString(),
                    style = style,
                    color = color,
                )
            }
        }
    }
}


