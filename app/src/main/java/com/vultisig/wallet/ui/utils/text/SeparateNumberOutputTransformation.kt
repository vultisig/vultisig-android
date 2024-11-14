package com.vultisig.wallet.ui.utils.text

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Stable
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParseException
import java.util.Locale

// FIXME: doesn't work for a lot of cases
@Stable
internal class SeparateNumberOutputTransformation : OutputTransformation {

    private val format = NumberFormat.getNumberInstance(Locale.getDefault())
        .apply {
            maximumFractionDigits = Int.MAX_VALUE
            maximumIntegerDigits = Int.MAX_VALUE
            if (this is DecimalFormat) {
                isParseBigDecimal = true
            }
        }

    override fun TextFieldBuffer.transformOutput() {
        val text = originalText.toString()

        try {
            val parsedNumber = format.parse(text)
            val formattedNumber = format.format(parsedNumber)

            var i = 0
            while (i < formattedNumber.length) {
                val char = charAt(i)
                if (char != formattedNumber[i]) {
                    if (char.isDigit()) {
                        insert(i, formattedNumber[i].toString())
                    } else {
                        delete(i, i + 1)
                        i--
                    }
                }
                i++
            }
        } catch (e: ParseException) {
            // ignore
        }
    }

}