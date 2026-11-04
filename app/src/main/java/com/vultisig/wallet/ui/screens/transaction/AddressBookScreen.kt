@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiConfirmDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.components.settingsShade
import com.vultisig.wallet.ui.components.v2.buttons.DesignType
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.vultiCircleShadeGradient
import com.vultisig.wallet.ui.models.transaction.AddressBookEntryUiModel
import com.vultisig.wallet.ui.models.transaction.AddressBookUiModel
import com.vultisig.wallet.ui.models.transaction.AddressBookViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AddressBookScreen(model: AddressBookViewModel = hiltViewModel()) {
    val state by model.state.collectAsState()

    LaunchedEffect(Unit) { model.loadData() }

    AddressBookScreen(
        state = state,
        onBackClick = model::back,
        onAddressClick = model::clickAddress,
        onDeleteAddressClick = model::requestDeleteAddress,
        onConfirmDeleteAddress = model::confirmDeleteAddress,
        onCancelDeleteAddress = model::cancelDeleteAddress,
        onToggleEditMode = model::toggleEditMode,
        onAddAddressClick = model::addAddress,
        onMove = model::move,
    )
}

@Composable
internal fun AddressBookScreen(
    state: AddressBookUiModel,
    onBackClick: () -> Unit,
    onAddressClick: (AddressBookEntryUiModel) -> Unit = {},
    onDeleteAddressClick: (AddressBookEntryUiModel) -> Unit = {},
    onConfirmDeleteAddress: () -> Unit = {},
    onCancelDeleteAddress: () -> Unit = {},
    onToggleEditMode: () -> Unit = {},
    onAddAddressClick: () -> Unit = {},
    onMove: (from: Int, to: Int) -> Unit,
) {
    val isEditModeEnabled = state.isEditModeEnabled

    state.pendingDeletion?.let { pending ->
        DeleteAddressDialog(
            entry = pending,
            onConfirm = onConfirmDeleteAddress,
            onDismiss = onCancelDeleteAddress,
        )
    }

    V2Scaffold(
        actions = {
            if (state.entries.isNotEmpty()) {
                if (isEditModeEnabled) {
                    val doneLabel = stringResource(R.string.address_book_edit_mode_done)
                    VsCircleButton(
                        modifier = Modifier.semantics { contentDescription = doneLabel },
                        drawableResId = R.drawable.header_checkmark,
                        tint = Color.Unspecified,
                        iconSize = HEADER_ICON_SIZE,
                        onClick = onToggleEditMode,
                        designType = DesignType.Solid,
                    )
                } else {
                    VsCircleButton(
                        drawableResId = R.drawable.pen_writing_filled,
                        tint = Color.Unspecified,
                        iconSize = HEADER_ICON_SIZE,
                        onClick = onToggleEditMode,
                        type = VsCircleButtonType.Secondary,
                    )
                }
            }
        },
        onBackClick = onBackClick,
        title =
            if (isEditModeEnabled) stringResource(R.string.address_book_title_edit)
            else stringResource(R.string.address_book_toolbar_title),
        bottomBar = {
            if (state.entries.isNotEmpty()) {
                VsButton(
                    label = stringResource(R.string.address_book_add_address_button),
                    labelStyle = Theme.brockmann.button.medium.medium,
                    onClick = onAddAddressClick,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 22.dp),
                )
            }
        },
    ) {
        if (state.entries.isNotEmpty()) {
            VerticalReorderList(
                data = state.entries,
                onMove = onMove,
                key = { it.model.id },
                isReorderEnabled = state.isEditModeEnabled,
                contentPadding = PaddingValues(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { entry ->
                AddressItem(
                    image = entry.image,
                    name = entry.name,
                    address = entry.address,
                    isEditModeEnabled = isEditModeEnabled,
                    onClick = { onAddressClick(entry) },
                    onDeleteClick = { onDeleteAddressClick(entry) },
                )
            }
        } else {
            NoAddressView(onAddAddressClick = onAddAddressClick)
        }
    }
}

@Composable
private fun DeleteAddressDialog(
    entry: AddressBookEntryUiModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    UiConfirmDialog(
        title = stringResource(R.string.address_book_delete_entry_title),
        text = stringResource(R.string.address_book_delete_entry_message, entry.name),
        confirmTitle = stringResource(R.string.address_book_delete_entry_confirm),
        dismissTitle = stringResource(R.string.address_book_delete_entry_cancel),
        confirmColor = Theme.v2.colors.alerts.error,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun NoAddressView(onAddAddressClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .settingsShade(centerOffsetY = EMPTY_SHADE_CENTER_OFFSET)
            .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = EMPTY_BOTTOM_OFFSET),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.address_book_empty_title),
            style = Theme.brockmann.button.medium.regular,
            color = Theme.v2.colors.neutrals.n50,
            textAlign = TextAlign.Center,
        )

        UiSpacer(size = 12.dp)

        Text(
            text = stringResource(R.string.address_book_empty_description),
            style = Theme.brockmann.button.medium.medium,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(size = 30.dp)

        VsButton(
            label = stringResource(R.string.address_book_add_address_button),
            onClick = onAddAddressClick,
            size = VsButtonSize.Wide,
            variant = VsButtonVariant.Primary,
        )
    }
}

private val HEADER_ICON_SIZE = 24.dp
private val EMPTY_BOTTOM_OFFSET = 177.dp
private val EMPTY_SHADE_CENTER_OFFSET = (-44).dp
private val ROW_HEIGHT = 66.dp
private val GRIP_TO_ROW_GAP = 5.5.dp

fun Modifier.shadeCircle() =
    this.drawBehind { drawCircle(brush = Brush.vultiCircleShadeGradient()) }

@Preview
@Composable
private fun NoAddressPreview() {
    NoAddressView(onAddAddressClick = {})
}

@Composable
private fun AddressItem(
    image: ImageModel,
    name: String,
    address: String,
    isEditModeEnabled: Boolean = true,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val rowShape = if (isEditModeEnabled) Theme.v2.radius.pill else Theme.v2.radius.xl
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isEditModeEnabled) {
            UiIcon(
                drawableResId = R.drawable.grip_dots_vertical,
                size = 24.dp,
                tint = Color.Unspecified,
            )
            UiSpacer(size = GRIP_TO_ROW_GAP)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = ROW_HEIGHT)
                    .clickOnce(onClick = onClick)
                    .background(color = Theme.v2.colors.backgrounds.secondary, shape = rowShape)
                    .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = rowShape)
                    .padding(start = 16.dp, end = if (isEditModeEnabled) 20.dp else 16.dp),
        ) {
            TokenLogo(
                logo = image,
                title = name,
                modifier = Modifier.size(32.dp),
                errorLogoModifier = Modifier.size(32.dp),
            )
            UiSpacer(size = 12.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.primary,
                )

                UiSpacer(size = 4.dp)
                Text(
                    text = address,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }

            if (isEditModeEnabled) {
                UiSpacer(size = 12.dp)
                UiIcon(
                    drawableResId = R.drawable.trash_filled,
                    onClick = onDeleteClick,
                    size = 20.dp,
                    tint = Theme.v2.colors.text.tertiary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun AddressItemPreview() {
    AddressItem(
        image = Chain.Ethereum.logo,
        name = "Online Wallet",
        address = "0xF43jf9840fkfjn38fk0dk9Ac5F43jf9840fkfjn38fk0dk9Ac5",
        isEditModeEnabled = true,
        onClick = {},
        onDeleteClick = {},
    )
}

@Preview
@Composable
private fun AddressItemPreview2() {
    AddressItem(
        image = Chain.Ethereum.logo,
        name = "Online Wallet",
        address = "0xF43jf9840fkfjn38fk0dk9Ac5F43jf9840fkfjn38fk0dk9Ac5",
        isEditModeEnabled = false,
        onClick = {},
        onDeleteClick = {},
    )
}

@Preview
@Composable
private fun DeleteAddressDialogPreview() {
    DeleteAddressDialog(
        entry =
            AddressBookEntryUiModel(
                model =
                    AddressBookEntry(
                        chain = Chain.Ethereum,
                        address = "0xF43jf9840fkfjn38fk0dk9Ac5",
                        title = "Online Wallet",
                    ),
                image = "",
                name = "Online Wallet",
                network = "Ethereum",
                address = "0xF43jf9840fkfjn38fk0dk9Ac5",
            ),
        onConfirm = {},
        onDismiss = {},
    )
}

@Preview
@Composable
private fun AddressBookScreenPreview() {
    AddressBookScreen(
        state =
            AddressBookUiModel(
                isEditModeEnabled = false,
                entries =
                    listOf(
                        AddressBookEntryUiModel(
                            model =
                                AddressBookEntry(
                                    chain = Chain.Ethereum,
                                    address = "0xF43jf9840fkfjn38fk0dk9Ac5",
                                    title = "Online Wallet",
                                ),
                            image = "",
                            name = "Online Wallet",
                            network = "Ethereum",
                            address = "0xF43jf9840fkfjn38fk0dk9Ac5",
                        )
                    ),
            ),
        onBackClick = {},
        onAddressClick = {},
        onDeleteAddressClick = {},
        onToggleEditMode = {},
        onAddAddressClick = {},
        onMove = { _, _ -> },
    )
}
