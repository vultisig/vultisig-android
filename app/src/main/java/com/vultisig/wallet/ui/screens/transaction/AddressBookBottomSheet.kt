package com.vultisig.wallet.ui.screens.transaction

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.ColorGenerator

@Composable
internal fun AddressBookBottomSheet(
    model: AddressBookBottomSheetViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    VsModalBottomSheet(
        onDismissRequest = model::back,
        content = {
            AddressBookContent(
                state = state,
                onAddressClick = model::selectAddress,
            )
        }
    )
}

@Composable
private fun AddressBookContent(
    state: AddressBookBottomSheetUiModel,
    onAddressClick: (AddressEntryUiModel) -> Unit,
) {
    var isShowingAddresses by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            Column {
                Text(
                    text = stringResource(R.string.address_book_toolbar_title),
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(all = 10.dp)
                        .fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .padding(
                            vertical = 8.dp,
                            horizontal = 16.dp
                        )
                        .border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                            shape = RoundedCornerShape(99.dp),
                        )
                        .fillMaxWidth(),
                ) {
                    PickerItem(
                        title = stringResource(R.string.address_book_saved_addresses),
                        isSelected = isShowingAddresses,
                        onClick = {
                            isShowingAddresses = true
                        }
                    )

                    PickerItem(
                        title = stringResource(R.string.address_book_my_vaults),
                        isSelected = !isShowingAddresses,
                        onClick = {
                            isShowingAddresses = false
                        }
                    )
                }
            }
        },
        content = { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(contentPadding),
                contentPadding = PaddingValues(
                    vertical = 8.dp,
                    horizontal = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(if (isShowingAddresses) state.addresses else state.vaults) {
                    EntryItem(
                        title = it.title,
                        subtitle = it.subtitle,
                        image = it.image,
                        onClick = {
                            onAddressClick(it)
                        }
                    )
                }
            }
        },
    )
}

@Composable
private fun RowScope.PickerItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = title,
        style = Theme.brockmann.supplementary.footnote,
        color = Theme.v2.colors.text.light,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.MiddleEllipsis,
        maxLines = 1,
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected)
                    Modifier.background(
                        color = Theme.v2.colors.primary.accent3,
                        shape = RoundedCornerShape(99.dp),
                    )
                else Modifier
            )
            .padding(all = 12.dp)
            .weight(1f)
    )
}

@Composable
private fun EntryItem(
    @DrawableRes image: Int?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = Theme.v2.colors.border.light,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(
                horizontal = 20.dp,
                vertical = 12.dp,
            )
            .fillMaxWidth(),
    ) {
        if (image != null) {
            Image(
                painter = painterResource(image),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .aspectRatio(1f)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = ColorGenerator.generate(title),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (title.firstOrNull() ?: ' ')
                        .uppercase()
                        .toString(),
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }


        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )

            Text(
                text = subtitle,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.light,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAddressBookBottomSheet() {
    AddressBookContent(
        state = AddressBookBottomSheetUiModel(),
        onAddressClick = {},
    )
}