package com.vultisig.wallet.ui.components.v2.texts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
internal fun highlightedText(
    mainText: String,
    highlightedWords: List<String>,
    mainTextStyle: TextStyle,
    mainTextColor: Color,
    highlightTextStyle: TextStyle,
    highlightTextColor: Color
): AnnotatedString = buildAnnotatedString {
    var currentIndex = 0
    while (currentIndex < mainText.length) {
        // Find the next highlighted word
        val nextHighlight = highlightedWords
            .mapNotNull { word ->
                val index = mainText.indexOf(word, startIndex = currentIndex, ignoreCase = true)
                if (index != -1) index to word else null
            }
            .minByOrNull { it.first } // closest match

        if (nextHighlight == null) {
            // Append remaining normal text
            withStyle(mainTextStyle.copy(color = mainTextColor).toSpanStyle()) {
                append(mainText.substring(currentIndex))
            }
            break
        } else {
            val (index, word) = nextHighlight
            // Add text before highlight
            if (index > currentIndex) {
                withStyle(mainTextStyle.copy(color = mainTextColor).toSpanStyle()) {
                    append(mainText.substring(currentIndex, index))
                }
            }
            // Add highlighted word
            withStyle(highlightTextStyle.copy(color = highlightTextColor).toSpanStyle()) {
                append(word)
            }
            currentIndex = index + word.length
        }
    }
}