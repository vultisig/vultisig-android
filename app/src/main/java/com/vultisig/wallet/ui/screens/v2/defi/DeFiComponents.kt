package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.tokenitem.GridTokenUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGridUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGroupUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionList
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionUiModel
import com.vultisig.wallet.ui.components.v2.utils.roundToPx
import com.vultisig.wallet.ui.screens.referral.SetBackgroundBanner
import com.vultisig.wallet.ui.screens.swap.components.HintBox
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import com.vultisig.wallet.ui.screens.v2.home.components.NotEnabledContainer
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BalanceBanner(
    title: String,
    isLoading: Boolean,
    totalValue: String,
    image: Int,
    isBalanceVisible: Boolean = true,
) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(16.dp),
                )
    ) {
        SetBackgroundBanner(backgroundImageResId = image)

        Column(modifier = Modifier.padding(start = 16.dp, top = 25.dp)) {
            Text(
                text = title,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.l.medium,
            )

            UiSpacer(6.dp)

            if (isLoading) {
                UiPlaceholderLoader(modifier = Modifier.size(width = 150.dp, height = 32.dp))
            } else {
                Text(
                    text = if (isBalanceVisible) totalValue else HIDE_BALANCE_CHARS,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.satoshi.price.title1,
                )
            }
        }
    }
}

private val HIDE_BALANCE_CHARS = "• ".repeat(8).trim()

@Preview(showBackground = true, name = "Balance Banner - With Value")
@Composable
private fun BalanceBannerPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        BalanceBanner(
            title = Chain.ThorChain.raw,
            isLoading = false,
            totalValue = "$12,345.67",
            image = R.drawable.referral_data_banner,
            isBalanceVisible = true,
        )
    }
}

@Preview(showBackground = true, name = "Balance Banner - Hidden")
@Composable
private fun BalanceBannerHiddenPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        BalanceBanner(
            title = Chain.ThorChain.raw,
            isLoading = false,
            totalValue = "$12,345.67",
            image = R.drawable.referral_data_banner,
            isBalanceVisible = false,
        )
    }
}

@Preview(showBackground = true, name = "Balance Banner - Loading")
@Composable
private fun BalanceBannerLoadingPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        BalanceBanner(
            title = Chain.ThorChain.raw,
            isLoading = true,
            totalValue = "",
            image = R.drawable.referral_data_banner,
            isBalanceVisible = true,
        )
    }
}

@Composable
fun InfoItem(
    modifier: Modifier = Modifier,
    icon: Int,
    label: String,
    value: String?,
    onMoreInfoClick: (() -> Unit)? = null,
    onMoreInfoIconModifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UiIcon(
                size = 16.dp,
                drawableResId = icon,
                contentDescription = null,
                tint = Theme.v2.colors.text.tertiary,
            )

            UiSpacer(4.dp)

            Text(
                text = label,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
            )

            if (onMoreInfoClick != null) {
                UiSpacer(size = 4.dp)

                UiIcon(
                    drawableResId = R.drawable.circleinfo,
                    size = 16.dp,
                    tint = Theme.v2.colors.text.tertiary,
                    modifier = onMoreInfoIconModifier.clickable { onMoreInfoClick() },
                )
            }
        }

        if (value != null) {
            UiSpacer(6.dp)

            Text(
                text = value,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@Composable
fun ActionButton(
    title: String,
    icon: Int?,
    background: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    contentColor: Color,
    iconCircleColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = background,
                contentColor = contentColor,
                disabledContainerColor = background.copy(alpha = 0.5f),
                disabledContentColor = contentColor.copy(alpha = 0.5f),
            ),
        border =
            if (enabled) {
                border
            } else {
                border?.let {
                    BorderStroke(
                        width = it.width,
                        color =
                            when (val brush = it.brush) {
                                is SolidColor -> brush.value.copy(alpha = 0.5f)
                                else ->
                                    Color.Gray.copy(alpha = 0.5f) // fallback for gradient brushes
                            },
                    )
                }
            },
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(start = 4.dp, end = 16.dp),
        modifier = modifier.height(42.dp),
    ) {
        if (icon != null) {
            Box(
                modifier =
                    Modifier.size(34.dp)
                        .background(
                            if (enabled) iconCircleColor else iconCircleColor.copy(alpha = 0.5f),
                            RoundedCornerShape(50),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                )
            }
        }

        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = Theme.brockmann.button.medium.medium,
        )
    }
}

@Composable
fun ApyInfoItem(apy: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        InfoItem(
            icon = R.drawable.ic_icon_percentage,
            label = stringResource(R.string.apy),
            value = null,
        )

        UiSpacer(1f)

        Text(
            text = apy,
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.alerts.success,
        )
    }
}

@Composable
internal fun PositionsSelectionDialog(
    selectedPositions: List<String>,
    bondPositions: List<PositionUiModelDialog> = emptyList(),
    stakePositions: List<PositionUiModelDialog> = emptyList(),
    lpPositions: List<PositionUiModelDialog> = emptyList(),
    searchTextFieldState: TextFieldState,
    onPositionSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    onDoneClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
) {
    val searchQuery = searchTextFieldState.text.toString().lowercase()

    val updateBondPositions =
        remember(bondPositions, selectedPositions) {
            bondPositions.map { it.copy(isSelected = selectedPositions.contains(it.positionKey)) }
        }

    val updateStakePositions =
        remember(stakePositions, selectedPositions) {
            stakePositions.map { it.copy(isSelected = selectedPositions.contains(it.positionKey)) }
        }

    val updateLpPositions =
        remember(lpPositions, selectedPositions) {
            lpPositions.map { it.copy(isSelected = selectedPositions.contains(it.positionKey)) }
        }

    val filteredBondPositions =
        remember(searchQuery, updateBondPositions) {
            if (searchQuery.isEmpty()) updateBondPositions
            else updateBondPositions.filter { it.ticker.lowercase().contains(searchQuery) }
        }

    val filteredStakePositions =
        remember(searchQuery, updateStakePositions) {
            if (searchQuery.isEmpty()) updateStakePositions
            else updateStakePositions.filter { it.ticker.lowercase().contains(searchQuery) }
        }

    val filteredLpPositions =
        remember(searchQuery, updateLpPositions) {
            if (searchQuery.isEmpty()) updateLpPositions
            else updateLpPositions.filter { it.ticker.lowercase().contains(searchQuery) }
        }

    val groups = mutableListOf<TokenSelectionGroupUiModel<PositionUiModelDialog>>()

    fun buildItems(positions: List<PositionUiModelDialog>) =
        positions.map { GridTokenUiModel.SingleToken(data = it) }

    fun buildMapper(gridToken: GridTokenUiModel<PositionUiModelDialog>): TokenSelectionGridUiModel {
        val model =
            when (gridToken) {
                is GridTokenUiModel.PairToken<PositionUiModelDialog> -> error("Not supported")
                is GridTokenUiModel.SingleToken<PositionUiModelDialog> ->
                    TokenSelectionUiModel.TokenUiSingle(
                        name = gridToken.data.ticker,
                        logo = gridToken.data.logo,
                    )
            }
        return TokenSelectionGridUiModel(
            isChecked = gridToken.data.isSelected,
            tokenSelectionUiModel = model,
        )
    }

    if (filteredBondPositions.isNotEmpty()) {
        groups.add(
            TokenSelectionGroupUiModel(
                title = stringResource(R.string.defi_bond),
                items = buildItems(filteredBondPositions),
                mapper = ::buildMapper,
                plusUiModel = null,
            )
        )
    }

    if (filteredStakePositions.isNotEmpty()) {
        groups.add(
            TokenSelectionGroupUiModel(
                title = stringResource(R.string.defi_stake),
                items = buildItems(filteredStakePositions),
                mapper = ::buildMapper,
                plusUiModel = null,
            )
        )
    }

    if (filteredLpPositions.isNotEmpty()) {
        groups.add(
            TokenSelectionGroupUiModel(
                title = stringResource(R.string.liquidity_pool),
                items = buildItems(filteredLpPositions),
                mapper = ::buildMapper,
                plusUiModel = null,
            )
        )
    }

    TokenSelectionList(
        groups = groups,
        searchTextFieldState = searchTextFieldState,
        titleContent = {
            Column {
                Text(
                    stringResource(R.string.select_positions),
                    style = Theme.brockmann.headings.title2,
                    color = Theme.v2.colors.neutrals.n100,
                )

                UiSpacer(16.dp)

                Text(
                    text = stringResource(R.string.enable_at_least_one_position),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        },
        notFoundContent = { NoFoundContent(message = stringResource(R.string.no_positions_found)) },
        onCheckChange = { isSelected, uiModel ->
            when (uiModel) {
                is GridTokenUiModel.SingleToken<PositionUiModelDialog> -> {
                    val position = uiModel.data
                    onPositionSelectionChange(position.positionKey, isSelected)
                }

                else -> error("Not supported double coin")
            }
        },
        onDoneClick = onDoneClick,
        onCancelClick = onCancelClick,
        onPasteClick = searchTextFieldState::setTextAndPlaceCursorAtEnd,
    )
}

@Composable
internal fun NoPositionsContainer(onManagePositionsClick: () -> Unit = {}) {
    NotEnabledContainer(
        title = stringResource(R.string.defi_no_positions_selected),
        content = stringResource(R.string.defi_no_positions_selected_desc),
        action = {
            VsButton(
                label = stringResource(R.string.manage_positions),
                onClick = onManagePositionsClick,
                state = VsButtonState.Enabled,
            )
        },
    )
}

@Composable
internal fun DeFiWarningBanner(text: String, onClickClose: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Theme.v2.colors.backgrounds.secondary)
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.light,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = if (onClickClose != null) 40.dp else 16.dp,
                        bottom = 16.dp,
                    )
        ) {
            Text(
                text = text,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )
        }

        if (onClickClose != null) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Theme.v2.colors.backgrounds.surface2)
                        .clickable(onClick = onClickClose),
                contentAlignment = Alignment.Center,
            ) {
                UiIcon(
                    drawableResId = R.drawable.big_close,
                    contentDescription = "cancel",
                    size = 16.dp,
                )
            }
        }
    }
}

@Composable
internal fun HeaderDeFiWidget(
    title: String,
    iconRes: Int,
    buttonText: String,
    onClickAction: () -> Unit,
    totalAmount: String,
    totalPrice: String = "",
    isLoading: Boolean = false,
    isBalanceVisible: Boolean = true,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )

            UiSpacer(12.dp)

            Column {
                Text(
                    text = title,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )

                UiSpacer(4.dp)

                if (isLoading) {
                    UiPlaceholderLoader(modifier = Modifier.size(width = 120.dp, height = 28.dp))
                } else {
                    Text(
                        text = if (isBalanceVisible) totalAmount else HIDE_BALANCE_CHARS,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.v2.colors.text.primary,
                    )

                    if (totalPrice.isNotEmpty()) {
                        UiSpacer(4.dp)

                        Text(
                            text = if (isBalanceVisible) totalPrice else HIDE_BALANCE_CHARS,
                            style = Theme.brockmann.supplementary.footnote,
                            color = Theme.v2.colors.text.tertiary,
                        )
                    }
                }
            }
        }

        UiSpacer(16.dp)

        UiHorizontalDivider(color = Theme.v2.colors.border.light)

        UiSpacer(16.dp)

        ApyApproxWithHint()

        UiSpacer(16.dp)

        VsButton(
            label = buttonText,
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickAction,
            state = VsButtonState.Enabled,
        )
    }
}

@Composable
private fun ApyApprox(
    modifier: Modifier = Modifier,
    apy: String = "1%",
    onMoreInfoClick: (() -> Unit)? = null,
    onMoreInfoIconModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoItem(
            icon = R.drawable.ic_icon_percentage,
            label = stringResource(R.string.apy_approx),
            value = null,
            modifier = Modifier.fillMaxHeight(),
            onMoreInfoClick = onMoreInfoClick,
            onMoreInfoIconModifier = onMoreInfoIconModifier,
        )

        UiSpacer(1f)
        Text(
            text = apy,
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.alerts.success,
        )
    }
}

@Composable
private fun ApyApproxWithHint(modifier: Modifier = Modifier, apy: String = "1%") {
    var showHint by remember { mutableStateOf(false) }
    var iconPosition by remember { mutableStateOf(Offset.Zero) }
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hintBoxSize by remember { mutableStateOf(IntSize.Zero) }
    val iconSize = 16.dp

    Box(modifier = modifier.onGloballyPositioned { boxCoords = it }) {
        ApyApprox(
            apy = apy,
            onMoreInfoClick = { showHint = !showHint },
            onMoreInfoIconModifier =
                Modifier.onGloballyPositioned { coordinates ->
                    iconPosition =
                        boxCoords?.localPositionOf(coordinates, Offset.Zero) ?: Offset.Zero
                },
        )

        HintBox(
            modifier = Modifier.width(200.dp).onGloballyPositioned { hintBoxSize = it.size },
            isVisible = showHint,
            title = stringResource(R.string.apy_approx_hint_title),
            message = stringResource(R.string.apy_approx_hint_message),
            offset =
                IntOffset(
                    x =
                        iconPosition.x.toInt() - hintBoxSize.width.div(2) +
                            iconSize.roundToPx().div(2),
                    y = (iconPosition.y - hintBoxSize.height).toInt(),
                ),
            onDismissClick = { showHint = false },
            isPointerTriangleOnTop = false,
        )
    }
}

@Composable
internal fun HeaderDeFiWidget(
    title: String,
    iconRes: Int,
    buttonFirstActionText: String,
    buttonSecondActionText: String,
    onClickFirstAction: () -> Unit,
    onClickSecondAction: () -> Unit,
    totalAmount: String,
    totalPrice: String = "",
    isLoading: Boolean = false,
    isBalanceVisible: Boolean = true,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )

            UiSpacer(12.dp)

            Column {
                Text(
                    text = title,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.tertiary,
                )

                UiSpacer(4.dp)

                if (isLoading) {
                    UiPlaceholderLoader(modifier = Modifier.size(width = 120.dp, height = 28.dp))
                } else {
                    Text(
                        text = if (isBalanceVisible) totalAmount else HIDE_BALANCE_CHARS,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.v2.colors.text.primary,
                    )

                    if (totalPrice.isNotEmpty()) {
                        UiSpacer(4.dp)

                        Text(
                            text = if (isBalanceVisible) totalPrice else HIDE_BALANCE_CHARS,
                            style = Theme.brockmann.supplementary.footnote,
                            color = Theme.v2.colors.text.tertiary,
                        )
                    }
                }
            }
        }

        UiSpacer(16.dp)

        UiHorizontalDivider(color = Theme.v2.colors.border.light)

        UiSpacer(16.dp)

        ApyApproxWithHint()

        UiSpacer(16.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                title = buttonFirstActionText,
                icon = R.drawable.circle_minus,
                background = Theme.v2.colors.backgrounds.tertiary_2,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f)),
                contentColor = Theme.v2.colors.text.primary,
                iconCircleColor = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f),
                onClick = onClickFirstAction,
            )

            ActionButton(
                title = buttonSecondActionText,
                icon = null,
                background = Theme.v2.colors.buttons.ctaPrimary,
                border = BorderStroke(1.dp, Theme.v2.colors.primary.accent3),
                contentColor = Theme.v2.colors.text.primary,
                iconCircleColor = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f),
                onClick = onClickSecondAction,
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    name = "DeFi Warning Banner - Short Text",
)
@Composable
private fun DeFiWarningBannerShortPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        DeFiWarningBanner(
            text = "This feature is currently in beta. Please use with caution.",
            onClickClose = {},
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    name = "DeFi Warning Banner - Long Text",
)
@Composable
private fun DeFiWarningBannerLongPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        DeFiWarningBanner(
            text =
                "Important: Your funds are at risk. This DeFi protocol has not been audited and may contain smart contract vulnerabilities. Only invest what you can afford to lose. Always do your own research before participating in any DeFi protocol.",
            onClickClose = {},
        )
    }
}

@Preview(showBackground = true, name = "Positions Selection Dialog")
@Composable
private fun PositionsSelectionDialogPreview() {
    PositionsSelectionDialog(
        bondPositions = defaultPositionsBondDialog(),
        stakePositions = defaultPositionsStakingDialog(),
        selectedPositions = listOf("RUNE", "TCY"),
        searchTextFieldState = TextFieldState(),
    )
}

@Preview(showBackground = true, name = "Info Item - With Value")
@Composable
private fun InfoItemWithValuePreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        InfoItem(icon = R.drawable.coins_tier, label = "APY", value = "12.5%")
    }
}

@Preview(showBackground = true, name = "Info Item - Without Value")
@Composable
private fun InfoItemWithoutValuePreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        InfoItem(icon = R.drawable.calendar_days, label = "Next Churn", value = null)
    }
}

@Preview(showBackground = true, name = "Info Items - Row")
@Composable
private fun InfoItemsRowPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            InfoItem(icon = R.drawable.coins_tier, label = "APY", value = "12.5%")
            InfoItem(icon = R.drawable.coins_tier, label = "Bonded", value = "1000 RUNE")
            InfoItem(icon = R.drawable.coins_tier, label = "Next Award", value = "20 RUNE")
        }
    }
}

@Preview(showBackground = true, name = "Action Button - Bond (Enabled)")
@Composable
private fun ActionButtonBondEnabledPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        ActionButton(
            title = "Bond",
            icon = R.drawable.circle_plus,
            background = Theme.v2.colors.buttons.tertiary,
            contentColor = Theme.v2.colors.text.primary,
            iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
            enabled = true,
            onClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Action Button - Bond (Disabled)")
@Composable
private fun ActionButtonBondDisabledPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        ActionButton(
            title = "Bond",
            icon = R.drawable.circle_plus,
            background = Theme.v2.colors.buttons.tertiary,
            contentColor = Theme.v2.colors.text.primary,
            iconCircleColor = Theme.v2.colors.text.primary,
            enabled = false,
            onClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Action Button - Unbond")
@Composable
private fun ActionButtonUnbondPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        ActionButton(
            title = "Unbond",
            icon = R.drawable.circle_minus,
            background = Color.Transparent,
            border = BorderStroke(1.dp, Theme.v2.colors.buttons.tertiary),
            contentColor = Theme.v2.colors.buttons.tertiary,
            iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
            onClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Action Buttons - Row")
@Composable
private fun ActionButtonsRowPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                title = "Bond",
                icon = R.drawable.circle_plus,
                background = Theme.v2.colors.buttons.tertiary,
                contentColor = Theme.v2.colors.text.primary,
                iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f),
                onClick = {},
            )
            ActionButton(
                title = "Unbond",
                icon = R.drawable.circle_minus,
                background = Color.Transparent,
                border = BorderStroke(1.dp, Theme.v2.colors.buttons.tertiary),
                contentColor = Theme.v2.colors.buttons.tertiary,
                iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f),
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Header DeFi Widget - Two Actions")
@Composable
private fun HeaderDeFiWidgetTwoActionsPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        HeaderDeFiWidget(
            title = "USDC Deposit",
            iconRes = R.drawable.usdc,
            buttonFirstActionText = "Withdraw",
            buttonSecondActionText = "Deposit USDC",
            onClickFirstAction = {},
            onClickSecondAction = {},
            totalAmount = "0.7031 USDC",
        )
    }
}

@Preview(showBackground = true, name = "Complete Node Card Mock")
@Composable
private fun CompleteNodeCardMockPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(Theme.v2.colors.backgrounds.secondary, RoundedCornerShape(12.dp))
                    .padding(16.dp)
        ) {
            // Node Address
            Text(
                text = "thor1abcd...xyz",
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )

            UiSpacer(12.dp)

            // Info Items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                InfoItem(icon = R.drawable.coins_tier, label = "APY", value = "12.5%")
                InfoItem(icon = R.drawable.coins_tier, label = "Bonded", value = "1000 RUNE")
                InfoItem(icon = R.drawable.coins_tier, label = "Next Award", value = "20 RUNE")
            }

            UiSpacer(16.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    title = "Bond",
                    icon = R.drawable.circle_plus,
                    background = Theme.v2.colors.buttons.tertiary,
                    contentColor = Theme.v2.colors.text.primary,
                    iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f),
                    onClick = {},
                )
                ActionButton(
                    title = "Unbond",
                    icon = R.drawable.circle_minus,
                    background = Color.Transparent,
                    border = BorderStroke(1.dp, Theme.v2.colors.buttons.tertiary),
                    contentColor = Theme.v2.colors.buttons.tertiary,
                    iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f),
                    onClick = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "APY Info Item")
@Composable
private fun ApyInfoItemPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        ApyInfoItem(apy = "12.5%")
    }
}

@Preview(showBackground = true, name = "APY Info Item - High APY")
@Composable
private fun ApyInfoItemHighPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        ApyInfoItem(apy = "125.8%")
    }
}

@Preview(showBackground = true, name = "APY Info Item - Low APY")
@Composable
private fun ApyInfoItemLowPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        ApyInfoItem(apy = "0.5%")
    }
}
