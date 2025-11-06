package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.tokenitem.GridTokenUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.NoFoundContent
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGridUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionGroupUiModel
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionList
import com.vultisig.wallet.ui.components.v2.tokenitem.TokenSelectionUiModel
import com.vultisig.wallet.ui.screens.v2.defi.model.PositionUiModelDialog
import com.vultisig.wallet.ui.theme.Theme

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
    searchTextFieldState: TextFieldState = TextFieldState(),
    onPositionSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    onDoneClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
) {
    val searchQuery = searchTextFieldState.text.toString().lowercase()
    val updateBondPositions = bondPositions.map {
        it.copy(
            isSelected = selectedPositions.contains(it.ticker)
        )
    }
    val updateStakePositions = stakePositions.map {
        it.copy(
            isSelected = selectedPositions.contains(it.ticker),
        )
    }

    // Filter positions based on search query
    val filteredBondPositions = if (searchQuery.isEmpty()) {
        updateBondPositions
    } else {
        updateBondPositions.filter { it.ticker.lowercase().contains(searchQuery) }
    }
    val filteredStakePositions = if (searchQuery.isEmpty()) {
        updateStakePositions
    } else {
        updateStakePositions.filter { it.ticker.lowercase().contains(searchQuery) }
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
                    color = Theme.colors.neutrals.n100,
                )

                UiSpacer(16.dp)

                Text(
                    text = stringResource(R.string.enable_at_least_one_position),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.extraLight,
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
        onSetSearchText = { /* Search is handled by the searchTextFieldState */ }
    )
}

@Preview(showBackground = true, name = "Positions Selection Dialog")
@Composable
private fun PositionsSelectionDialogPreview() {
    PositionsSelectionDialog(
        selectedPositions = listOf("RUNE", "TCY")
    )
}

@Preview(showBackground = true, name = "Info Item - With Value")
@Composable
private fun InfoItemWithValuePreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
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
            .background(Theme.colors.backgrounds.primary)
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
            .background(Theme.colors.backgrounds.primary)
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
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ActionButton(
            title = "Bond",
            icon = R.drawable.circle_plus,
            background = Theme.colors.buttons.primary,
            contentColor = Theme.colors.text.primary,
            iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
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
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ActionButton(
            title = "Bond",
            icon = R.drawable.circle_plus,
            background = Theme.colors.buttons.primary,
            contentColor = Theme.colors.text.primary,
            iconCircleColor = Theme.colors.text.primary,
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
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ActionButton(
            title = "Unbond",
            icon = R.drawable.circle_minus,
            background = Color.Transparent,
            border = BorderStroke(1.dp, Theme.colors.buttons.primary),
            contentColor = Theme.colors.buttons.primary,
            iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Action Buttons - Row")
@Composable
private fun ActionButtonsRowPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                title = "Bond",
                icon = R.drawable.circle_plus,
                background = Theme.colors.buttons.primary,
                contentColor = Theme.colors.text.primary,
                iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            ActionButton(
                title = "Unbond",
                icon = R.drawable.circle_minus,
                background = Color.Transparent,
                border = BorderStroke(1.dp, Theme.colors.buttons.primary),
                contentColor = Theme.colors.buttons.primary,
                iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
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
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Theme.colors.backgrounds.secondary,
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            // Node Address
            Text(
                text = "thor1abcd...xyz",
                style = Theme.brockmann.body.m.medium,
                color = Theme.colors.text.primary
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
                    background = Theme.colors.buttons.primary,
                    contentColor = Theme.colors.text.primary,
                    iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                ActionButton(
                    title = "Unbond",
                    icon = R.drawable.circle_minus,
                    background = Color.Transparent,
                    border = BorderStroke(1.dp, Theme.colors.buttons.primary),
                    contentColor = Theme.colors.buttons.primary,
                    iconCircleColor = Theme.colors.buttons.primary.copy(alpha = 0.1f),
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
            .background(Theme.colors.backgrounds.primary)
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
            .background(Theme.colors.backgrounds.primary)
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
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        ApyInfoItem(
            apy = "0.5%"
        )
    }
}