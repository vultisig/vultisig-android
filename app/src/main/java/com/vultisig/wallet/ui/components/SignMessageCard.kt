package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

/** [Settings] is the settings Verify screen's card; every other screen keeps [Default]. */
internal enum class SignMessageCardStyle {
    Default,
    Settings,
}

/**
 * Card used on custom-message Verify and Transaction Complete screens to display a labelled value
 * (e.g. Method, Message, Signature). Renders a bordered, dark-filled rounded card with a muted
 * caption label and a value rendered in [valueColor] (defaults to the primary text color; pass the
 * accent blue for the signed-signature value to match the extension design).
 */
@Composable
internal fun SignMessageCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Theme.v2.colors.text.primary,
    style: SignMessageCardStyle = SignMessageCardStyle.Default,
) {
    SignMessageCard(title = title, modifier = modifier, style = style) {
        Text(
            text = value,
            color = valueColor,
            style =
                if (style == SignMessageCardStyle.Settings) Theme.brockmann.body.s.medium
                else Theme.brockmann.body.s.regular,
        )
    }
}

/** The same card with [content] under the caption, for values that are not one string. */
@Composable
internal fun SignMessageCard(
    title: String,
    modifier: Modifier = Modifier,
    style: SignMessageCardStyle = SignMessageCardStyle.Default,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isSettingsStyle = style == SignMessageCardStyle.Settings
    val shape = if (isSettingsStyle) Theme.v2.radius.md else Theme.v2.radius.xl
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = shape)
                .background(color = Theme.v2.colors.backgrounds.disabled, shape = shape)
                .then(
                    if (isSettingsStyle) Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    else Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ),
        verticalArrangement = Arrangement.spacedBy(if (isSettingsStyle) 12.dp else 8.dp),
    ) {
        Text(
            text = title,
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.supplementary.caption,
        )

        content()
    }
}
