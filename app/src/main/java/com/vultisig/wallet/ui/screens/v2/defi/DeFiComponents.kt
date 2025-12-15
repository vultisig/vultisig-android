package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.tokenitem.GridTokenUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGridUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGroupUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionList
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionUiModel
import com.vultisig.wallet.ui.screens.referral.SetBackgoundBanner
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
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = Theme.v2.colors.border.light,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        SetBackgoundBanner(backgroundImageResId = image)

        Column(
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp)
        ) {
            Text(
                text = title,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.l.medium,
            )

            UiSpacer(16.dp)

            Text(
                text = stringResource(R.string.defi_balance),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            if (isLoading) {
                UiPlaceholderLoader(
                    modifier = Modifier
                        .size(width = 150.dp, height = 32.dp)
                )
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
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        BalanceBanner(
            title = Chain.ThorChain.raw,
            isLoading = false,
            totalValue = "$12,345.67",
            image = R.drawable.referral_data_banner,
            isBalanceVisible = true
        )
    }
}

@Preview(showBackground = true, name = "Balance Banner - Hidden")
@Composable
private fun BalanceBannerHiddenPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        BalanceBanner(
            title = Chain.ThorChain.raw,
            isLoading = false,
            totalValue = "$12,345.67",
            image = R.drawable.referral_data_banner,
            isBalanceVisible = false
        )
    }
}

@Preview(showBackground = true, name = "Balance Banner - Loading")
@Composable
private fun BalanceBannerLoadingPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        BalanceBanner(
            title = Chain.ThorChain.raw,
            isLoading = true,
            totalValue = "",
            image = R.drawable.referral_data_banner,
            isBalanceVisible = true
        )
    }
}

@Composable
fun InfoItem(icon: Int, label: String, value: String?) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UiIcon(
                size = 16.dp,
                drawableResId = icon,
                contentDescription = null,
                tint = Theme.v2.colors.text.extraLight,
            )

            UiSpacer(4.dp)

            Text(
                text = label,
                color = Theme.v2.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
            )
        }

        if (value != null) {
            UiSpacer(6.dp)

            Text(
                text = value,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.light,
            )
        }
    }
}


@Composable
fun ActionButton(
    title: String,
    icon: Int,
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
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = contentColor,
            disabledContainerColor = background.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        border = if (enabled) {
            border
        } else {
            border?.let {
                BorderStroke(
                    width = it.width,
                    color = when (val brush = it.brush) {
                        is SolidColor -> brush.value.copy(alpha = 0.5f)
                        else -> Color.Gray.copy(alpha = 0.5f) // fallback for gradient brushes
                    }
                )
            }
        },
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = modifier.height(42.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (enabled) iconCircleColor else iconCircleColor.copy(alpha = 0.5f),
                    RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
            )
        }

        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ApyInfoItem(
    apy: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    searchTextFieldState: TextFieldState,
    onPositionSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    onDoneClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
) {
    val searchQuery = searchTextFieldState.text.toString().lowercase()

    // Update selections with remember to avoid recreation
    val updateBondPositions = remember(bondPositions, selectedPositions) {
        bondPositions.map {
            it.copy(
                isSelected = selectedPositions.contains(it.ticker)
            )
        }
    }

    val updateStakePositions = remember(stakePositions, selectedPositions) {
        stakePositions.map {
            it.copy(
                isSelected = selectedPositions.contains(it.ticker),
            )
        }
    }

    // Filter positions based on search query with remember
    val filteredBondPositions = remember(searchQuery, updateBondPositions) {
        if (searchQuery.isEmpty()) {
            updateBondPositions
        } else {
            updateBondPositions.filter { it.ticker.lowercase().contains(searchQuery) }
        }
    }

    val filteredStakePositions = remember(searchQuery, updateStakePositions) {
        if (searchQuery.isEmpty()) {
            updateStakePositions
        } else {
            updateStakePositions.filter { it.ticker.lowercase().contains(searchQuery) }
        }
    }

    val groups = mutableListOf<TokenSelectionGroupUiModel<PositionUiModelDialog>>()

    if (filteredBondPositions.isNotEmpty()) {
        groups.add(
            TokenSelectionGroupUiModel(
                title = "Bond",
                items = filteredBondPositions.map { position ->
                    GridTokenUiModel.SingleToken(data = position)
                },
                mapper = { gridToken ->
                    val tokenSelectionUiModel = when (gridToken) {
                        is GridTokenUiModel.PairToken<PositionUiModelDialog> ->
                            error("Not supported")
                        is GridTokenUiModel.SingleToken<PositionUiModelDialog> -> {
                            TokenSelectionUiModel.TokenUiSingle(
                                name = gridToken.data.ticker,
                                logo = gridToken.data.logo,
                            )
                        }
                    }
                    TokenSelectionGridUiModel(
                        isChecked = gridToken.data.isSelected,
                        tokenSelectionUiModel = tokenSelectionUiModel
                    )
                },
                plusUiModel = null,
            )
        )
    }

    if (filteredStakePositions.isNotEmpty()) {
        groups.add(
            TokenSelectionGroupUiModel(
                title = "Stake",
                items = filteredStakePositions.map { position ->
                    GridTokenUiModel.SingleToken(data = position)
                },
                mapper = { gridToken ->
                    val tokenSelectionUiModel = when (gridToken) {
                        is GridTokenUiModel.PairToken<PositionUiModelDialog> ->
                            error("Not Supported")
                        is GridTokenUiModel.SingleToken<PositionUiModelDialog> -> {
                            TokenSelectionUiModel.TokenUiSingle(
                                name = gridToken.data.ticker,
                                logo = gridToken.data.logo
                            )
                        }
                    }
                    TokenSelectionGridUiModel(
                        isChecked = gridToken.data.isSelected,
                        tokenSelectionUiModel = tokenSelectionUiModel
                    )
                },
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
                    color = Theme.v2.colors.text.extraLight,
                )
            }
        },
        notFoundContent = {
            NoFoundContent(
                message = stringResource(R.string.no_positions_found)
            )
        },
        onCheckChange = { isSelected, uiModel ->
            when (uiModel) {
                is GridTokenUiModel.SingleToken<PositionUiModelDialog> -> {
                    val position = uiModel.data
                    onPositionSelectionChange(position.ticker, isSelected)
                }

                else -> error("Not supported double coin")
            }
        },
        onDoneClick = onDoneClick,
        onCancelClick = onCancelClick,
        onPasteClick = searchTextFieldState::setTextAndPlaceCursorAtEnd
    )
}

@Composable
internal fun NoPositionsContainer(
    onManagePositionsClick: () -> Unit = {}
) {
    NotEnabledContainer(
        title = stringResource(R.string.defi_no_positions_selected),
        content = stringResource(R.string.defi_no_positions_selected_desc),
        action = {
            Text(
                text = stringResource(R.string.manage_positions),
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.text.primary,
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .clickOnce(onClick = onManagePositionsClick)
                    .background(
                        color = Theme.v2.colors.border.primaryAccent4
                    )
                    .padding(
                        vertical = 8.dp,
                        horizontal = 16.dp
                    )
            )
        }
    )
}

@Composable
internal fun DeFiWarningBanner(
    text: String,
    onClickClose: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.normal,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = if (onClickClose != null) 40.dp else 16.dp,
                    bottom = 16.dp
                )
        ) {
            Text(
                text = text,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.light,
            )
        }

        if (onClickClose != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Theme.v2.colors.backgrounds.surface2)
                    .clickable(onClick = onClickClose),
                contentAlignment = Alignment.Center
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
    isLoading: Boolean = false,
    isBalanceVisible: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.v2.colors.backgrounds.secondary)
            .border(
                width = 1.dp,
                color = Theme.v2.colors.border.normal,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )

            UiSpacer(12.dp)

            Column {
                Text(
                    text = title,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.extraLight,
                )

                UiSpacer(4.dp)

                if (isLoading) {
                    UiPlaceholderLoader(
                        modifier = Modifier
                            .size(width = 120.dp, height = 28.dp)
                    )
                } else {
                    Text(
                        text = if (isBalanceVisible) totalAmount else HIDE_BALANCE_CHARS,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.v2.colors.text.primary,
                    )
                }
            }
        }

        UiSpacer(16.dp)

        UiHorizontalDivider(color = Theme.v2.colors.border.light)

        UiSpacer(16.dp)

        VsButton(
            label = buttonText,
            modifier = Modifier.fillMaxWidth(),
            onClick = onClickAction,
            state = VsButtonState.Enabled,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "DeFi Warning Banner - Short Text")
@Composable
private fun DeFiWarningBannerShortPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        DeFiWarningBanner(
            text = "This feature is currently in beta. Please use with caution.",
            onClickClose = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "DeFi Warning Banner - Long Text")
@Composable
private fun DeFiWarningBannerLongPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        DeFiWarningBanner(
            text = "Important: Your funds are at risk. This DeFi protocol has not been audited and may contain smart contract vulnerabilities. Only invest what you can afford to lose. Always do your own research before participating in any DeFi protocol.",
            onClickClose = {}
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
        searchTextFieldState = TextFieldState()
    )
}

@Preview(showBackground = true, name = "Info Item - With Value")
@Composable
private fun InfoItemWithValuePreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        InfoItem(
            icon = R.drawable.coins_tier,
            label = "APY",
            value = "12.5%"
        )
    }
}

@Preview(showBackground = true, name = "Info Item - Without Value")
@Composable
private fun InfoItemWithoutValuePreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        InfoItem(
            icon = R.drawable.calendar_days,
            label = "Next Churn",
            value = null
        )
    }
}

@Preview(showBackground = true, name = "Info Items - Row")
@Composable
private fun InfoItemsRowPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoItem(
                icon = R.drawable.coins_tier,
                label = "APY",
                value = "12.5%"
            )
            InfoItem(
                icon = R.drawable.coins_tier,
                label = "Bonded",
                value = "1000 RUNE"
            )
            InfoItem(
                icon = R.drawable.coins_tier,
                label = "Next Award",
                value = "20 RUNE"
            )
        }
    }
}

@Preview(showBackground = true, name = "Action Button - Bond (Enabled)")
@Composable
private fun ActionButtonBondEnabledPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ActionButton(
            title = "Bond",
            icon = R.drawable.circle_plus,
            background = Theme.v2.colors.buttons.tertiary,
            contentColor = Theme.v2.colors.text.primary,
            iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
            enabled = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Action Button - Bond (Disabled)")
@Composable
private fun ActionButtonBondDisabledPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ActionButton(
            title = "Bond",
            icon = R.drawable.circle_plus,
            background = Theme.v2.colors.buttons.tertiary,
            contentColor = Theme.v2.colors.text.primary,
            iconCircleColor = Theme.v2.colors.text.primary,
            enabled = false,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Action Button - Unbond")
@Composable
private fun ActionButtonUnbondPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ActionButton(
            title = "Unbond",
            icon = R.drawable.circle_minus,
            background = Color.Transparent,
            border = BorderStroke(1.dp, Theme.v2.colors.buttons.tertiary),
            contentColor = Theme.v2.colors.buttons.tertiary,
            iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Action Buttons - Row")
@Composable
private fun ActionButtonsRowPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                title = "Bond",
                icon = R.drawable.circle_plus,
                background = Theme.v2.colors.buttons.tertiary,
                contentColor = Theme.v2.colors.text.primary,
                iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            ActionButton(
                title = "Unbond",
                icon = R.drawable.circle_minus,
                background = Color.Transparent,
                border = BorderStroke(1.dp, Theme.v2.colors.buttons.tertiary),
                contentColor = Theme.v2.colors.buttons.tertiary,
                iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Complete Node Card Mock")
@Composable
private fun CompleteNodeCardMockPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Theme.v2.colors.backgrounds.secondary,
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            // Node Address
            Text(
                text = "thor1abcd...xyz",
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary
            )

            UiSpacer(12.dp)

            // Info Items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoItem(
                    icon = R.drawable.coins_tier,
                    label = "APY",
                    value = "12.5%"
                )
                InfoItem(
                    icon = R.drawable.coins_tier,
                    label = "Bonded",
                    value = "1000 RUNE"
                )
                InfoItem(
                    icon = R.drawable.coins_tier,
                    label = "Next Award",
                    value = "20 RUNE"
                )
            }

            UiSpacer(16.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    title = "Bond",
                    icon = R.drawable.circle_plus,
                    background = Theme.v2.colors.buttons.tertiary,
                    contentColor = Theme.v2.colors.text.primary,
                    iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                ActionButton(
                    title = "Unbond",
                    icon = R.drawable.circle_minus,
                    background = Color.Transparent,
                    border = BorderStroke(1.dp, Theme.v2.colors.buttons.tertiary),
                    contentColor = Theme.v2.colors.buttons.tertiary,
                    iconCircleColor = Theme.v2.colors.buttons.tertiary.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "APY Info Item")
@Composable
private fun ApyInfoItemPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ApyInfoItem(
            apy = "12.5%"
        )
    }
}

@Preview(showBackground = true, name = "APY Info Item - High APY")
@Composable
private fun ApyInfoItemHighPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ApyInfoItem(
            apy = "125.8%"
        )
    }
}

@Preview(showBackground = true, name = "APY Info Item - Low APY")
@Composable
private fun ApyInfoItemLowPreview() {
    Box(
        modifier = Modifier
            .background(Theme.v2.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ApyInfoItem(
            apy = "0.5%"
        )
    }
}