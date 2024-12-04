@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.transaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.components.reorderable.VerticalReorderList
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
        onAddressClick = model::selectAddress,
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
        containerColor = Theme.colors.oxfordBlue800,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.address_book_toolbar_title),
                        style = Theme.montserrat.heading5,
                        fontWeight = FontWeight.Bold,
                        color = Theme.colors.neutral0,
                        textAlign = TextAlign.Center,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Theme.colors.oxfordBlue800,
                    titleContentColor = Theme.colors.neutral0,
                ),
                navigationIcon = {
                    IconButton(onClick = clickOnce { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_caret_left),
                            contentDescription = null,
                            tint = Theme.colors.neutral0,
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                        )
                    ) {
                        if (isEditModeEnabled) {
                            Text(
                                text = stringResource(R.string.address_book_edit_mode_done),
                                style = Theme.menlo.subtitle1,
                                fontWeight = FontWeight.Bold,
                                color = Theme.colors.neutral0,
                                modifier = Modifier.clickOnce(onClick = onToggleEditMode)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_edit_square_24),
                                contentDescription = "edit",
                                tint = Theme.colors.neutral0,
                                modifier = Modifier.clickOnce(onClick = onToggleEditMode)
                            )
                        }
                    }
                }
            )
        },
        content = { scaffoldPadding ->
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
                    network = entry.network,
                    address = entry.address,
                    isEditModeEnabled = isEditModeEnabled,
                    onClick = { onAddressClick(entry) },
                    onDeleteClick = { onDeleteAddressClick(entry) }
                )
            }
        },
        bottomBar = {
            MultiColorButton(
                text = stringResource(R.string.address_book_add_address_button),
                textColor = Theme.colors.oxfordBlue800,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 16.dp,
                        horizontal = 16.dp,
                    ),
                onClick = onAddAddressClick,
            )
        }
    )
}

@Composable
private fun AddressItem(
    image: ImageModel,
    name: String,
    network: String,
    address: String,
    isEditModeEnabled: Boolean = true,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditModeEnabled) {
            UiIcon(
                drawableResId = R.drawable.ic_drag_handle,
                size = 24.dp,
            )

            UiSpacer(size = 8.dp)
        }

        FormCard(
            modifier = Modifier
                .clickable(onClick = onClick)
                .weight(1f),
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp),
            ) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                )

                UiSpacer(size = 12.dp)

                Column {
                    Row {
                        Text(
                            text = name,
                            color = Theme.colors.neutral100,
                            style = Theme.montserrat.body1,
                            modifier = Modifier.weight(1f),
                        )

                        UiSpacer(size = 12.dp)

                        Text(
                            text = network,
                            color = Theme.colors.neutral300,
                            style = Theme.menlo.body1,
                        )
                    }

                    UiSpacer(size = 6.dp)

                    Text(
                        text = address,
                        color = Theme.colors.neutral100,
                        style = Theme.menlo.body1,
                    )
                }
            }
        }

        if (isEditModeEnabled) {
            UiSpacer(size = 8.dp)

            UiIcon(
                drawableResId = R.drawable.trash_outline,
                onClick = onDeleteClick,
                size = 24.dp,
            )
        }
    }
}

@Preview
@Composable
private fun AddressBookScreenPreview() {
    AddressBookScreen(
        navController = rememberNavController(),
        state = AddressBookUiModel(
            isEditModeEnabled = true,
            entries = listOf(
                AddressBookEntryUiModel(
                    model = AddressBookEntry(
                        chain = Chain.Ethereum,
                        address = "123456abcdef",
                        title = "",
                    ),
                    image = "",
                    name = "Satoshi Nakamoto",
                    network = "Bitcoin",
                    address = "123456abcdef",
                ),
            ),
        ),
        onAddressClick = {},
        onMove = { _, _ -> },
    )
}