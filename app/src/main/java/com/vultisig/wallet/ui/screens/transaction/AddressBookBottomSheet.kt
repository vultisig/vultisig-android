package com.vultisig.wallet.ui.screens.transaction

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.animatePlacementInScope
import com.vultisig.wallet.ui.components.bottomsheet.VsModalBottomSheet
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.Variables
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
                onCancelClick = model::back
            )
        }
    )
}

@Composable
private fun AddressBookContent(
    state: AddressBookBottomSheetUiModel,
    onAddressClick: (AddressEntryUiModel) -> Unit,
    onCancelClick: () -> Unit
) {
    var isShowingAddresses by remember { mutableStateOf(true) }
    V2BottomSheet(
        onDismissRequest = onCancelClick,
        leftAction = {
            VsCircleButton(
                drawableResId = R.drawable.big_close,
                onClick = onCancelClick,
                type = VsCircleButtonType.Tertiary,
                size = VsCircleButtonSize.Small,
            )
        },
        title = stringResource(R.string.address_book_toolbar_title),
    )
    {
        Column(
            modifier = Modifier.padding(
                horizontal = V2Scaffold.PADDING_HORIZONTAL,
                vertical = V2Scaffold.PADDING_HORIZONTAL,
            )
        ) {
            AddressToggle(
                isShowingAddresses = isShowingAddresses,
                onAddressClick = { isShowingAddresses = true },
                onVaultClick = { isShowingAddresses = false },
            )
            LazyColumn(
                contentPadding = PaddingValues(
                    vertical = V2Scaffold.PADDING_VERTICAL,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val displayItems = if (isShowingAddresses) state.addresses else state.vaults

                if (displayItems.isEmpty()) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp)
                                .background(
                                    color = Theme.v2.colors.variables.backgroundsSurface1,
                                    shape = RoundedCornerShape(size = 12.dp)
                                )
                                .padding(
                                    vertical = 20.dp,
                                    horizontal = 24.dp,
                                ),

                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {

                            UiIcon(
                                drawableResId = R.drawable.book,
                                size = 24.dp,
                                tint = Theme.v2.colors.primary.accent4
                            )
                            UiSpacer(
                                size = 14.dp
                            )

                            Text(
                                text = stringResource(R.string.address_book_empty_title),
                                style = Theme.brockmann.button.medium.regular,
                                color = Theme.v2.colors.neutrals.n50,
                                textAlign = TextAlign.Center
                            )

                            UiSpacer(
                                size = 8.dp
                            )

                            Text(
                                text = stringResource(R.string.address_book_empty_description),
                                style = Theme.brockmann.button.medium.small,
                                color = Theme.v2.colors.text.extraLight,
                                textAlign = TextAlign.Center
                            )

                        }
                    }
                } else {
                    items(displayItems) {
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

            }
        }
    }

}

@Composable
private fun AddressToggle(
    isShowingAddresses: Boolean,
    onAddressClick: () -> Unit,
    onVaultClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(intrinsicSize = IntrinsicSize.Min)
    ) {
        LookaheadScope {

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.normal,
                        shape = RoundedCornerShape(size = 60.dp)
                    )
                    .padding(4.dp)
                    .fillMaxWidth(),
            ) {
                PickerItem(
                    title = stringResource(R.string.address_book_saved_addresses),
                    onClick = onAddressClick,
                    isSelected = isShowingAddresses

                )

                PickerItem(
                    title = stringResource(R.string.address_book_my_vaults),
                    onClick = onVaultClick,
                    isSelected = !isShowingAddresses
                )
            }
        }
    }
}

@Composable
private fun RowScope.PickerItem(
    title: String,
    onClick: () -> Unit,
    isSelected: Boolean
) {

    Text(
        text = title,
        style = Theme.brockmann.supplementary.footnote,
        color = Theme.v2.colors.text.light,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.MiddleEllipsis,
        maxLines = 1,
        modifier = Modifier
            .width(160.dp)
            .height(42.dp)
            .background(
                color = if (isSelected) Theme.v2.colors.variables.buttonsCTAPrimary
                else Theme.v2.colors.backgrounds.transparent,
                shape = RoundedCornerShape(size = 99.dp)
            )
            .padding(
                vertical = 12.dp,
                horizontal = 20.dp,
            )
            .clickable(onClick = onClick)
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
        onCancelClick = {}
    )
}