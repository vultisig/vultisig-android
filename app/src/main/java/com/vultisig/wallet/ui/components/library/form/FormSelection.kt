package com.vultisig.wallet.ui.components.library.form

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun <T> FormSelection(
    selected: T,
    options: List<T>,
    onSelectOption: (T) -> Unit,
    mapTypeToString: @Composable (T) -> String,
) {
    var isListExpanded by remember { mutableStateOf(false) }

    FormCard {
        SelectionCard(
            title = mapTypeToString(selected),
            actionIcon = R.drawable.ic_caret_down,
            onClick = { isListExpanded = !isListExpanded },
        )

        AnimatedVisibility(visible = isListExpanded) {
            Column {
                options.forEach { option ->
                    UiHorizontalDivider(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                    )

                    SelectionCard(
                        title = mapTypeToString(option),
                        actionIcon = if (selected == option)
                            R.drawable.check
                        else null,
                        onClick = {
                            isListExpanded = false
                            onSelectOption(option)
                        }
                    )
                }

            }
        }
    }
}

@Composable
internal fun SelectionCard(
    title: String,
    @DrawableRes actionIcon: Int? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .padding(
                vertical = 8.dp,
                horizontal = 12.dp
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = title,
            color = Theme.colors.neutrals.n100,
            style = Theme.menlo.body1,
            modifier = Modifier.weight(1f),
        )

        if (actionIcon != null) {
            UiSpacer(size = 8.dp)

            UiIcon(
                drawableResId = actionIcon,
                size = 20.dp,
            )
        }
    }
}

@Preview
@Composable
private fun FormSelectionPreview() {
    FormSelection(
        selected = "Rune",
        options = listOf("Rune", "BTC", "ETH", "BNB"),
        onSelectOption = {},
        mapTypeToString = { it }
    )
}