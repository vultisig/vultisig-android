package com.vultisig.wallet.ui.components.tokenitem

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.util.dashedBorder
import com.vultisig.wallet.ui.components.loader.V2Loading
import com.vultisig.wallet.ui.components.tokenitem.TokenSelectionUiModel.*
import com.vultisig.wallet.ui.theme.Theme


internal sealed interface TokenSelectionUiModel {
    data class TokenUiSingle(
        val name: String,
        val logo: ImageModel,
    ) : TokenSelectionUiModel

    data class TokenUiPair(
        val left: TokenUiSingle,
        val right: TokenUiSingle,
    ) : TokenSelectionUiModel
}

internal data class TokenSelectionGridUiModel(
    val tokenSelectionUiModel: TokenSelectionUiModel,
    val isChecked: Boolean,
)

internal data class GridPlusUiModel(
    val title: String,
    val onClick: () -> Unit,
)


@Composable
internal fun GridItem(
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

            when (uiModel.tokenSelectionUiModel) {
                is TokenUiPair -> TokenUiGridLogo(tokens = uiModel.tokenSelectionUiModel)
                is TokenUiSingle -> TokenUiGridLogo(token = uiModel.tokenSelectionUiModel)
            }
        }

        UiSpacer(
            size = 10.dp
        )

        when (uiModel.tokenSelectionUiModel) {
            is TokenUiPair -> TokenUiGridName(tokens = uiModel.tokenSelectionUiModel)
            is TokenUiSingle -> TokenUiGridName(token = uiModel.tokenSelectionUiModel)
        }
    }
}

@Composable
internal fun GridPlus(
    modifier: Modifier = Modifier,
    model: GridPlusUiModel,
) {
    Column(
        modifier = modifier.clickable(
            onClick = model.onClick
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
                    color = Theme.colors.border.normal,
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
            text = model.title,
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
    GridItem(
        uiModel = TokenSelectionGridUiModel(
            tokenSelectionUiModel = TokenUiSingle(
                name = Coins.Bitcoin.BTC.ticker,
                logo = Coins.Bitcoin.BTC.chain.logo,
            ),
            isChecked = true
        )
    )
}


@Preview
@Composable
fun PreviewRChainSelectionItem2() {
    GridItem(
        uiModel = TokenSelectionGridUiModel(
            tokenSelectionUiModel = TokenUiSingle(
                name = Coins.Bitcoin.BTC.ticker,
                logo = Coins.Bitcoin.BTC.chain.logo,
            ),
            isChecked = true
        )
    )
}

@Preview
@Composable
fun PreviewRChainSelectionItem3() {
    GridItem(
        uiModel = TokenSelectionGridUiModel(
            tokenSelectionUiModel = TokenUiPair(
                left = TokenUiSingle(
                    name = Coins.ThorChain.RUNE.ticker,
                    logo = Coins.ThorChain.RUNE.chain.logo,
                ),
                right = TokenUiSingle(
                    name = Coins.Bitcoin.BTC.ticker,
                    logo = Coins.Bitcoin.BTC.chain.logo,
                )
            ),
            isChecked = true
        )
    )
}


@Composable
private fun TokenUiGridLogo(
    tokens: TokenUiPair,
    space: Float = 4f,
) {

    val rightToken = tokens.right
    val leftToken = tokens.left
    val halfSpace = space / 2

    Box {
        TokenUiGridLogo(
            token = leftToken,
            modifier = Modifier
                .clip(
                    shape = HalfClipper(
                        clipSide = ClipSide.Left,
                        offset = halfSpace
                    )
                )
        )

        TokenUiGridLogo(
            token = rightToken,
            modifier = Modifier
                .clip(
                    shape = HalfClipper(
                        clipSide = ClipSide.Right,
                        offset = halfSpace
                    )
                )
        )
    }

}


@Composable
private fun TokenUiGridLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    token: TokenUiSingle,
) {

    Box(
        modifier = modifier
    ) {
        SubcomposeAsyncImage(
            model = token.logo,
            contentDescription = null,
            modifier = Modifier.size(size),
            loading = {
                V2Loading()
            },
            error = {
                Box(
                    modifier = Modifier.size(size),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        token.name.firstOrNull()?.toString().orEmpty(),
                        color = Theme.colors.text.primary,
                        style = Theme.brockmann.body.m.medium
                    )
                }
            }
        )
    }


}

@Preview
@Composable
private fun TokenUiGridLogoPreview1(){
    TokenUiGridLogo(
       token = TokenUiSingle(
            name = Coins.Bitcoin.BTC.ticker,
            logo = getCoinLogo(Coins.Bitcoin.BTC.logo)
        )
    )
}

@Preview
@Composable
private fun TokenUiGridLogoPreview2(){
    TokenUiGridLogo(
        tokens = TokenUiPair(
            left = TokenUiSingle(
                name = Coins.Bitcoin.BTC.ticker,
                logo = getCoinLogo(Coins.Bitcoin.BTC.logo)
            ),
            right = TokenUiSingle(
                name = Coins.Ethereum.BAT.ticker,
                logo = getCoinLogo(Coins.Ethereum.BAT.logo)
            )
        )
    )
}

@Composable
private fun TokenUiGridName(
    token: TokenUiSingle,
) {
    Text(
        text = token.name,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.colors.text.primary,
        modifier = Modifier
            .widthIn(max = 74.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TokenUiGridName(
    tokens: TokenUiPair,
    mapper: (TokenUiPair) -> String = { "${it.left.name}/${it.right.name}" },
) {
    Text(
        text = mapper(tokens),
        style = Theme.brockmann.supplementary.caption,
        color = Theme.colors.text.primary,
        modifier = Modifier
            .widthIn(max = 74.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}


@Preview
@Composable
private fun TokenGridNamePreview1(){
    TokenUiGridName(
        token = TokenUiSingle(
            name = Coins.Bitcoin.BTC.ticker,
            logo = getCoinLogo(Coins.Bitcoin.BTC.logo)
        )
    )
}

@Preview
@Composable
private fun TokenGridNamePreview2(){
    TokenUiGridName(
        tokens = TokenUiPair(
            left = TokenUiSingle(
                name = Coins.Bitcoin.BTC.ticker,
                logo = getCoinLogo(Coins.Bitcoin.BTC.logo)
            ),
            right = TokenUiSingle(
                name = Coins.Ethereum.BAT.ticker,
                logo = getCoinLogo(Coins.Ethereum.BAT.logo)
            )
        )
    )
}

private enum class ClipSide {
    Left, Right
}

private class HalfClipper(
    private val clipSide: ClipSide,
    private val offset: Float = 0f,
): Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        return Outline.Generic(Path().apply {
            when(clipSide){
                ClipSide.Left -> {
                    moveTo(0f, 0f)
                    lineTo(size.width / 2f - offset, 0f)
                    lineTo(size.width / 2f - offset, size.height)
                    lineTo(0f, size.height)
                }
                ClipSide.Right -> {
                    moveTo(size.width / 2f + offset, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f + offset, size.height)
                }
            }
            close()
        })
    }

}
