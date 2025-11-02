package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BalanceBanner(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    balance: String?,
    onToggleVisibility: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {

        LoadableValue(
            value = balance,
            isVisible = isVisible,
            color = Theme.v2.colors.text.primary,
            style = Theme.satoshi.price.largeTitle,
        )

        UiSpacer(
            size = 12.dp
        )

        ToggleBalanceVisibilityButton(
            isVisible = isVisible,
            onToggleVisibility = onToggleVisibility
        )
    }
}


@Composable
internal fun ToggleBalanceVisibilityButton(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onToggleVisibility: () -> Unit,
) {
    AnimatedContent(isVisible) { isVisible ->
        Row(
            modifier = modifier
                .clip(
                    shape = CircleShape,
                )
                .background(
                    color = Theme.v2.colors.text.button.dim.copy(alpha = 0.12f),
                )
                .padding(
                    horizontal = 6.dp,
                    vertical = 4.dp,
                )
                .clickOnce(
                    onClick = onToggleVisibility
                ),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically

        ) {
            UiIcon(
                drawableResId = if (isVisible) com.vultisig.wallet.R.drawable.eye_closed else com.vultisig.wallet.R.drawable.eye_open,
                size = 16.dp,
                tint = Theme.v2.colors.text.button.dim
            )

            UiSpacer(
                size = 4.dp
            )

            Text(
                text = if (isVisible) stringResource(R.string.hide_balance) else  stringResource(R.string.show_balance),
                color = Theme.v2.colors.text.button.dim,
                style = Theme.brockmann.button.medium.medium
            )
        }


    }
}

@Preview
@Composable
private fun PreviewBalanceBanner() {
    BalanceBanner(
        balance = "$53,010.77",
        isVisible = true,
        onToggleVisibility = {}
    )
}

@Preview
@Composable
private fun PreviewBalanceBanner2() {
    BalanceBanner(
        balance = "$53,010.77",
        isVisible = false,
        onToggleVisibility = {}
    )
}