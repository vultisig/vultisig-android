package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.ToggleVisibilityText
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
    hideText: String = stringResource(R.string.hide_balance),
    showText: String = stringResource(R.string.show_balance),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        // Placeholder and figure cross-fade; one figure replacing another rolls its digits in
        // place.
        AnimatedContent(
            targetState = balance,
            contentKey = { it != null },
            transitionSpec = { fadeIn() togetherWith fadeOut() using null },
        ) { value ->
            if (value == null) {
                LoadableValue(
                    value = null,
                    isVisible = isVisible,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.satoshi.price.largeTitle,
                )
            } else {
                RollingText(
                    text = value,
                    isVisible = isVisible,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.satoshi.price.largeTitle,
                )
            }
        }

        UiSpacer(size = 12.dp)

        ToggleBalanceVisibilityButton(
            isVisible = isVisible,
            onToggleVisibility = onToggleVisibility,
            hideText = hideText,
            showText = showText,
        )
    }
}

/** Characters are matched from the end, so the cents stay put when the figure gains a digit. */
@Composable
private fun RollingText(text: String, isVisible: Boolean, color: Color, style: TextStyle) {
    if (!isVisible) {
        ToggleVisibilityText(text = text, isVisible = false, color = color, style = style)
        return
    }
    val isReduceMotion = rememberIsReduceMotionEnabled()
    Row {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (isReduceMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val direction = if (targetState > initialState) 1 else -1
                            (slideInVertically { it * direction } + fadeIn()) togetherWith
                                (slideOutVertically { -it * direction } + fadeOut()) using
                                SizeTransform(clip = false)
                        }
                    },
                ) { shown ->
                    Text(text = shown.toString(), color = color, style = style)
                }
            }
        }
    }
}

@Composable
internal fun ToggleBalanceVisibilityButton(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    onToggleVisibility: () -> Unit,
    hideText: String = stringResource(R.string.hide_balance),
    showText: String = stringResource(R.string.show_balance),
) {
    AnimatedContent(isVisible) { isVisible ->
        Row(
            modifier =
                modifier
                    .clip(shape = CircleShape)
                    .background(color = Theme.v2.colors.text.button.dim.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clickOnce(onClick = onToggleVisibility),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId =
                    if (isVisible) com.vultisig.wallet.R.drawable.eye_closed
                    else com.vultisig.wallet.R.drawable.eye_open,
                size = 16.dp,
                tint = Theme.v2.colors.text.button.dim,
            )

            UiSpacer(size = 4.dp)

            Text(
                text = if (isVisible) hideText else showText,
                color = Theme.v2.colors.text.button.dim,
                style = Theme.brockmann.supplementary.caption,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewBalanceBanner() {
    BalanceBanner(balance = "$53,010.77", isVisible = true, onToggleVisibility = {})
}

@Preview
@Composable
private fun PreviewBalanceBanner2() {
    BalanceBanner(balance = "$53,010.77", isVisible = false, onToggleVisibility = {})
}
