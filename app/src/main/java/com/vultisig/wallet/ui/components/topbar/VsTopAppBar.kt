@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.components.topbar

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.vultisig.wallet.ui.components.DashedProgressIndicator
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
    VsTopAppBar(
        title = title,
        iconLeft = iconLeft,
        onIconLeftClick = onIconLeftClick,
        actions = {
            if (iconRight != null) {
                VsTopAppBarAction(
                    icon = iconRight,
                    onClick = onIconRightClick,
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun VsTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    @DrawableRes iconLeft: Int? = null,
    onIconLeftClick: (() -> Unit)? = null,
    @SuppressLint("ComposableLambdaParameterNaming")
    actions: @Composable RowScope.() -> Unit,
) {
    VsTopAppBar(
        title = title,
        navigationContent = {
            if (iconLeft != null) {
                VsTopAppBarAction(
                    icon = iconLeft,
                    onClick = onIconLeftClick,
                )
            }
        },
        actions = actions,
        modifier = modifier,
    )
}

@Composable
fun VsTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    navigationContent: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            if (title != null) {
                Text(
                    text = title,
                    style = Theme.brockmann.body.l.medium,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Theme.v2.colors.backgrounds.primary,
        ),
        navigationIcon = navigationContent,
        actions = actions,
        modifier = modifier,
    )
}

@Composable
fun VsTopAppBarAction(
    @DrawableRes icon: Int,
    onClick: (() -> Unit)?,
) {
    IconButton(
        onClick = clickOnce {
            onClick?.invoke()
        }
    ) {
        UiIcon(
            drawableResId = icon,
            contentDescription = null,
            tint = Theme.v2.colors.text.primary,
            size = 24.dp,
        )
    }
}

@Composable
fun VsTopAppProgressBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    navigationContent: @Composable () -> Unit = {},
    progress: Int = 3,
    total: Int = 12,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        CenterAlignedTopAppBar(
            title = {
                if (title != null) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        text = title,
                        style = Theme.brockmann.headings.title3,
                        color = Theme.v2.colors.text.primary,
                        textAlign = TextAlign.Start,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Theme.v2.colors.backgrounds.primary,
            ),
            navigationIcon = {
                navigationContent()
            },
            actions = actions,
            modifier = modifier.padding(end = 16.dp),
        )
        DashedProgressIndicator(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            progress = progress,
            totalNumberOfBars = total,
        )
    }
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