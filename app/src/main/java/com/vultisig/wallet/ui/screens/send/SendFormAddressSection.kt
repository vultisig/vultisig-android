package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
private fun AddressActionRow(
    onPaste: (String) -> Unit,
    onScan: () -> Unit,
    onAddressBook: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PasteIcon(
            modifier = Modifier.vsStyledBackground().padding(all = 12.dp).weight(1f),
            onPaste = onPaste,
        )

        UiIcon(
            drawableResId = R.drawable.camera,
            size = 20.dp,
            modifier = Modifier.vsStyledBackground().padding(all = 12.dp).weight(1f),
            onClick = onScan,
        )

        UiIcon(
            drawableResId = R.drawable.ic_bookmark,
            size = 20.dp,
            modifier = Modifier.vsStyledBackground().padding(all = 12.dp).weight(1f),
            onClick = onAddressBook,
        )
    }
}

@Composable
internal fun FoldableDestinationAddressWidget(
    state: SendFormUiModel,
    onExpandSection: (SendSections) -> Unit,
    addressFieldState: TextFieldState,
    addressFocusRequester: FocusRequester = remember { FocusRequester() },
    onDstAddressLostFocus: () -> Unit,
    onSetOutputAddress: (String) -> Unit,
    onScanDstAddressRequest: () -> Unit,
    onAddressBookClick: () -> Unit,
) {
    FoldableSection(
        expanded = state.expandedSection == SendSections.Address,
        complete = state.isDstAddressComplete,
        title = stringResource(R.string.add_address_address_title),
        onToggle = { onExpandSection(SendSections.Address) },
        completeTitleContent = {
            Text(
                text = addressFieldState.text.toString(),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.send_from_address),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .border(
                            border =
                                BorderStroke(width = 1.dp, color = Theme.v2.colors.border.light),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .background(
                            color = Theme.v2.colors.backgrounds.secondary,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = state.srcVaultName,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.supplementary.caption,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )

                Text(
                    text = state.srcAddress,
                    color = Theme.v2.colors.text.tertiary,
                    style = Theme.brockmann.supplementary.caption,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }

            UiSpacer(16.dp)

            Text(
                text =
                    when (state.defiType) {
                        DeFiNavActions.BOND,
                        DeFiNavActions.UNBOND -> stringResource(R.string.bond_node_address)
                        else -> stringResource(R.string.send_to_address)
                    },
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            VsTextInputField(
                textFieldState = addressFieldState,
                hint = stringResource(R.string.send_to_address_hint),
                focusRequester = addressFocusRequester,
                onFocusChanged = {
                    if (!it) {
                        onDstAddressLostFocus()
                    }
                },
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
                innerState =
                    if (state.dstAddressError != null) VsTextInputFieldInnerState.Error
                    else VsTextInputFieldInnerState.Default,
                footNote = state.dstAddressError?.asString(),
                modifier = Modifier.fillMaxWidth().testTag("SendFormScreen.addressField"),
            )

            UiSpacer(16.dp)

            AddressActionRow(
                onPaste = onSetOutputAddress,
                onScan = onScanDstAddressRequest,
                onAddressBook = onAddressBookClick,
            )
        }
    }
}

@Composable
internal fun FoldableBondDestinationAddress(
    state: SendFormUiModel,
    onExpandSection: (SendSections) -> Unit,
    addressFieldState: TextFieldState,
    addressFocusRequester: FocusRequester = remember { FocusRequester() },
    onDstAddressLostFocus: () -> Unit,
    onSetOutputAddress: (String) -> Unit,
    onScanDstAddressRequest: () -> Unit,
    onAddressBookClick: () -> Unit,
    providerFieldState: TextFieldState,
    onSetOutputProvider: (String) -> Unit,
    onScanProviderRequest: () -> Unit,
    onAddressProviderBookClick: () -> Unit,
) {
    FoldableSection(
        expanded = state.expandedSection == SendSections.Address,
        complete = state.isDstAddressComplete,
        title = stringResource(R.string.add_address_address_title),
        onToggle = { onExpandSection(SendSections.Address) },
        completeTitleContent = {
            Text(
                text = addressFieldState.text.toString(),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text =
                    when (state.defiType) {
                        null,
                        DeFiNavActions.BOND,
                        DeFiNavActions.UNBOND -> stringResource(R.string.bond_node_address)
                        else -> stringResource(R.string.send_to_address)
                    },
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            VsTextInputField(
                textFieldState = addressFieldState,
                hint = stringResource(R.string.send_to_address_hint),
                focusRequester = addressFocusRequester,
                onFocusChanged = {
                    if (!it) {
                        onDstAddressLostFocus()
                    }
                },
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
                innerState =
                    if (state.dstAddressError != null) VsTextInputFieldInnerState.Error
                    else VsTextInputFieldInnerState.Default,
                footNote = state.dstAddressError?.asString(),
                modifier = Modifier.fillMaxWidth().testTag("SendFormScreen.addressField"),
            )

            UiSpacer(16.dp)

            AddressActionRow(
                onPaste = onSetOutputAddress,
                onScan = onScanDstAddressRequest,
                onAddressBook = onAddressBookClick,
            )

            UiSpacer(12.dp)

            Text(
                text = stringResource(R.string.bond_provider_optional),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            VsTextInputField(
                textFieldState = providerFieldState,
                hint = stringResource(R.string.send_to_address_hint),
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
                innerState =
                    if (state.bondProviderError != null) {
                        VsTextInputFieldInnerState.Error
                    } else {
                        VsTextInputFieldInnerState.Default
                    },
                footNote = state.bondProviderError?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(16.dp)

            AddressActionRow(
                onPaste = onSetOutputProvider,
                onScan = onScanProviderRequest,
                onAddressBook = onAddressProviderBookClick,
            )
        }
    }
}
