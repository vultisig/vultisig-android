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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.components.vultiCircleShadeGradient
import com.vultisig.wallet.ui.models.transaction.AddressBookEntryUiModel
import com.vultisig.wallet.ui.models.transaction.AddressBookUiModel
import com.vultisig.wallet.ui.models.transaction.AddressBookViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AddressBookScreen(
    navController: NavController,
    model: AddressBookViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    LaunchedEffect(Unit) {
        model.loadData()
    }

    AddressBookScreen(
        navController = navController,
        state = state,
        onAddressClick = model::clickAddress,
        onDeleteAddressClick = model::deleteAddress,
        onToggleEditMode = model::toggleEditMode,
        onAddAddressClick = model::addAddress,
        onMove = model::move
    )
}

@Composable
internal fun AddressBookScreen(
    navController: NavController,
    state: AddressBookUiModel,
    onAddressClick: (AddressBookEntryUiModel) -> Unit = {},
    onDeleteAddressClick: (AddressBookEntryUiModel) -> Unit = {},
    onToggleEditMode: () -> Unit = {},
    onAddAddressClick: () -> Unit = {},
    onMove: (from: Int, to: Int) -> Unit,
) {
    val isEditModeEnabled = state.isEditModeEnabled
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                modifier = Modifier
                    .padding(horizontal = 16.dp),
                title = if (isEditModeEnabled)
                    stringResource(R.string.address_book_title_edit)
                else
                    stringResource(R.string.address_book_toolbar_title),
                navigationContent = {
                    UiIcon(
                        drawableResId = R.drawable.ic_caret_left,
                        onClick = { navController.popBackStack() },
                        size = 24.dp,
                    )
                },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        if (isEditModeEnabled) {
                            Text(
                                text = stringResource(R.string.address_book_edit_mode_done),
                                style = Theme.brockmann.button.medium,
                                color = Theme.colors.primary.accent4,
                                modifier = Modifier
                                    .background(
                                        color = Theme.colors.backgrounds.secondary,
                                        shape = CircleShape
                                    )
                                    .padding(
                                        all = 12.dp
                                    )
                                    .clickOnce(onClick = onToggleEditMode)
                            )

                        } else {
                            UiIcon(
                                drawableResId = R.drawable.reame,
                                contentDescription = "edit",
                                tint = Theme.colors.text.button.light,
                                onClick = onToggleEditMode,
                                size = 16.dp,
                            )

                        }
                    }
                }
            )
        },
        content = { scaffoldPadding ->
            if (state.entries.isNotEmpty()) {
                VerticalReorderList(
                    data = state.entries,
                    onMove = onMove,
                    key = { it.model.id },
                    isReorderEnabled = state.isEditModeEnabled,
                    modifier = Modifier.padding(scaffoldPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { entry ->
                    AddressItem(
                        image = entry.image,
                        name = entry.name,
                        address = entry.address,
                        isEditModeEnabled = isEditModeEnabled,
                        onClick = { onAddressClick(entry) },
                        onDeleteClick = { onDeleteAddressClick(entry) })
                }
            } else {
                NoAddressView(onAddAddressClick = onAddAddressClick)
            }
        }, bottomBar = {
            if (state.entries.isNotEmpty()) {
                VsButton(
                    label = stringResource(R.string.address_book_add_address_button),
                    onClick = onAddAddressClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 16.dp,
                            horizontal = 16.dp,
                        ),
                )
            }
        })
}

@Composable
private fun NoAddressView(
    onAddAddressClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .shadeCircle()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Text(
            text = stringResource(R.string.address_book_empty_title),
            style = Theme.brockmann.button.large,
            color = Theme.colors.neutral0,
            textAlign = TextAlign.Center
        )

        UiSpacer(
            size = 12.dp
        )

        Text(
            text = stringResource(R.string.address_book_empty_description),
            style = Theme.brockmann.button.medium,
            color = Theme.colors.neutral300,
            textAlign = TextAlign.Center
        )

        UiSpacer(
            size = 30.dp
        )

        VsButton(
            label = stringResource(R.string.address_book_add_address_button),
            onClick = onAddAddressClick,
            size = VsButtonSize.Medium,
            variant = VsButtonVariant.Primary
        )
    }
}

internal fun Modifier.shadeCircle() = this.drawBehind {
    drawCircle(
        brush = Brush.vultiCircleShadeGradient(),
    )
}

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
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {

        if (isEditModeEnabled) {
            UiIcon(
                drawableResId = R.drawable.ic_drag_handle,
                size = 24.dp,
            )
            UiSpacer(
                size = 8.dp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickOnce(onClick = onClick)
                .background(
                    color = Theme.colors.backgrounds.secondary,
                    shape = RoundedCornerShape(
                        size = 12.dp
                    ),
                )
                .border(
                    width = 1.dp,
                    color = Theme.colors.borders.light,
                    shape = RoundedCornerShape(
                        size = 12.dp
                    )
                )
                .padding(
                    horizontal = 20.dp,
                    vertical = 12.dp,
                )
        ) {
            TokenLogo(
                logo = image,
                title = name,
                modifier = Modifier
                    .size(32.dp),
                errorLogoModifier = Modifier
                    .size(32.dp),
            )
            UiSpacer(
                size = 12.dp,
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.primary,
                )

                UiSpacer(
                    size = 4.dp
                )
                Text(
                    text = address,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.colors.text.light,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
            }

            if (isEditModeEnabled) {
                UiSpacer(
                    size = 16.dp
                )
                UiIcon(
                    drawableResId = R.drawable.trash_outline,
                    onClick = onDeleteClick,
                    size = 20.dp,
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
        onDeleteClick = {}
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
        onDeleteClick = {}
    )
}