@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.components.topbar

import androidx.annotation.DrawableRes
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun VsTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBackClick: () -> Unit,
    @DrawableRes iconRight: Int? = null,
    onIconRightClick: (() -> Unit)? = null,
) {
    VsTopAppBar(
        modifier = modifier,
        title = title,
        iconLeft = R.drawable.ic_caret_left,
        onIconLeftClick = onBackClick,
        iconRight = iconRight,
        onIconRightClick = onIconRightClick,
    )
}

@Composable
fun VsTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    @DrawableRes iconLeft: Int? = null,
    onIconLeftClick: (() -> Unit)? = null,
    @DrawableRes iconRight: Int? = null,
    onIconRightClick: (() -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        title = {
            if (title != null) {
                Text(
                    text = title,
                    style = Theme.montserrat.heading5, // TODO update typography
                    color = Theme.colors.text.primary,
                    textAlign = TextAlign.Center,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Theme.colors.backgrounds.primary,
        ),
        navigationIcon = {
            if (iconLeft != null) {
                IconButton(
                    onClick = clickOnce {
                        onIconLeftClick?.invoke()
                    }
                ) {
                    UiIcon(
                        drawableResId = iconLeft,
                        contentDescription = null,
                        tint = Theme.colors.text.primary,
                        size = 24.dp,
                    )
                }
            }
        },
        actions = {
            if (iconRight != null) {
                IconButton(
                    onClick = clickOnce {
                        onIconRightClick?.invoke()
                    }
                ) {
                    UiIcon(
                        drawableResId = iconRight,
                        contentDescription = null,
                        tint = Theme.colors.text.primary,
                        size = 24.dp,
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Preview
@Composable
private fun VsTopAppBarPreview() {
    VsTopAppBar(
        title = "Title",
        iconLeft = R.drawable.ic_caret_left,
        iconRight = R.drawable.ic_question_mark,
    )
}