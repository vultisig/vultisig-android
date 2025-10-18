package com.vultisig.wallet.ui.components.v2.tokenitem

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.util.dashedBorder
import com.vultisig.wallet.ui.theme.Theme

internal data class TokenSelectionGridUiModel(
    val name: String,
    val logo: ImageModel,
    val isChecked: Boolean,
)


@Composable
internal fun TokenSelectionGridItem(
    modifier: Modifier = Modifier,
    uiModel: TokenSelectionGridUiModel,
    onCheckedChange: (Boolean) -> Unit = {},
) {

    val checked = uiModel.isChecked
    Column(
        modifier = modifier.toggleable(
            value = checked,
            onValueChange = onCheckedChange,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(74.dp)
                .clip(
                    shape = RoundedCornerShape(size = 24.dp)
                )
                .background(
                    color = if (checked) Theme.colors.backgrounds.secondary else Theme.colors.backgrounds.disabled
                )
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = checked,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                RoundedBorderWithLeaf()
            }

            SubcomposeAsyncImage(
                model = uiModel.logo,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                error = {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiModel.name.firstOrNull()?.toString().orEmpty(),
                            color = Theme.colors.text.primary,
                            style = Theme.brockmann.body.m.medium
                        )
                    }
                }
            )
        }

        UiSpacer(
            size = 10.dp
        )

        Text(
            text = uiModel.name,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.primary,
            modifier = Modifier
                .widthIn(max = 74.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun GridPlus(
    modifier: Modifier = Modifier,
    title: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(74.dp)
                .clip(
                    shape = RoundedCornerShape(size = 24.dp)
                )
                .background(
                    color = Theme.colors.backgrounds.disabled
                )
                .dashedBorder(
                    width = 1.dp,
                    color = Theme.colors.borders.normal,
                    cornerRadius = 24.dp,
                    dashLength = 4.dp,
                    intervalLength = 4.dp,
                )
        ) {
            UiIcon(
                drawableResId = R.drawable.plus,
                contentDescription = null,
                size = 28.dp,
                tint = Theme.colors.primary.accent4
            )
        }

        UiSpacer(
            size = 10.dp
        )

        Text(
            text = title,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.primary,
            modifier = Modifier
                .widthIn(max = 74.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview
@Composable
fun PreviewRChainSelectionItem() {
    TokenSelectionGridItem(
        uiModel = TokenSelectionGridUiModel(
            name = Coins.Bitcoin.BTC.ticker,
            logo = Coins.Bitcoin.BTC.chain.logo,
            isChecked = true
        )
    )
}

@Preview
@Composable
fun PreviewChainSelectionItem2() {
    TokenSelectionGridItem(
        uiModel = TokenSelectionGridUiModel(
            name = Coins.Bitcoin.BTC.ticker,
            logo = Coins.Bitcoin.BTC.chain.logo,
            isChecked = false
        )
    )
}