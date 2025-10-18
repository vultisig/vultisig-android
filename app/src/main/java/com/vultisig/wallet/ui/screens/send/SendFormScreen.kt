@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.screens.send

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.PasteIcon
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.selectors.ChainSelector
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.send.SendFormUiModel
import com.vultisig.wallet.ui.models.send.SendFormViewModel
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.screens.swap.TokenChip
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.cursorBrush
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun SendScreen(
    viewModel: SendFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    SendFormScreen(
        state = state,
        addressFieldState = viewModel.addressFieldState,
        tokenAmountFieldState = viewModel.tokenAmountFieldState,
        fiatAmountFieldState = viewModel.fiatAmountFieldState,
        memoFieldState = viewModel.memoFieldState,
        onDstAddressLostFocus = { /* no-op */ },
        onTokenAmountLostFocus = viewModel::validateTokenAmount,
        onDismissError = viewModel::dismissError,
        onSelectNetworkRequest = viewModel::selectNetwork,
        onSelectTokenRequest = viewModel::openTokenSelection,
        onSetOutputAddress = viewModel::setOutputAddress,
        onChooseMaxTokenAmount = viewModel::chooseMaxTokenAmount,
        onChoosePercentageAmount = viewModel::choosePercentageAmount,
        onScanDstAddressRequest = viewModel::scanAddress,
        onAddressBookClick = viewModel::openAddressBook,
        onSend = viewModel::send,
        onRefreshRequest = viewModel::refreshGasFee,
        onGasSettingsClick = viewModel::openGasSettings,
        onBackClick = viewModel::back,
        onToogleAmountInputType = viewModel::toggleAmountInputType,
        onExpandSection = viewModel::expandSection,
    )

    val selectedChain = state.selectedCoin?.model?.address?.chain
    val specific = state.specific

    if (state.showGasSettings && selectedChain != null && specific != null) {
        GasSettingsScreen(
            chain = selectedChain,
            specific = specific,
            onSaveGasSettings = viewModel::saveGasSettings,
            onDismissGasSettings = viewModel::dismissGasSettings,
        )
    }
}

@Composable
private fun SendFormScreen(
    state: SendFormUiModel,
    addressFieldState: TextFieldState,
    tokenAmountFieldState: TextFieldState,
    fiatAmountFieldState: TextFieldState,
    memoFieldState: TextFieldState,
    onDstAddressLostFocus: () -> Unit = {},
    onTokenAmountLostFocus: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onSelectNetworkRequest: () -> Unit = {},
    onSelectTokenRequest: () -> Unit = {},
    onSetOutputAddress: (String) -> Unit = {},
    onChooseMaxTokenAmount: () -> Unit = {},
    onChoosePercentageAmount: (Float) -> Unit = {},
    onAddressBookClick: () -> Unit = {},
    onScanDstAddressRequest: () -> Unit = {},
    onSend: () -> Unit = {},
    onRefreshRequest: () -> Unit = {},
    onGasSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onToogleAmountInputType: (Boolean) -> Unit = {},
    onExpandSection: (SendSections) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current

    val errorText = state.errorText
    if (errorText != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = errorText.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = onDismissError,
        )
    }

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.send_screen_title),
                onBackClick = onBackClick,
            )
        },
        content = { contentPadding ->
            val pullToRefreshState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefreshRequest,
                state = pullToRefreshState,
                indicator = {
                    Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.isRefreshing,
                        color = Theme.colors.primary.accent3,
                        state = pullToRefreshState
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(all = 16.dp)
                ) {
                    // select asset
                    FoldableSection(
                        expanded = state.expandedSection == SendSections.Asset,
                        onToggle = {
                            onExpandSection(SendSections.Asset)
                        },
                        complete = true,
                        title = stringResource(R.string.form_token_selection_asset),
                        completeTitleContent = {
                            Row(
                                modifier = Modifier.weight(1f)
                            ) {
                                val selectedToken = state.selectedCoin

                                TokenLogo(
                                    errorLogoModifier = Modifier
                                        .size(16.dp)
                                        .background(Theme.colors.neutral100),
                                    logo = selectedToken?.tokenLogo ?: "",
                                    title = selectedToken?.title ?: "",
                                    modifier = Modifier
                                        .size(16.dp)
                                )

                                UiSpacer(4.dp)

                                Text(
                                    text = selectedToken?.title ?: "",
                                    style = Theme.brockmann.supplementary.caption,
                                    color = Theme.colors.text.extraLight,
                                )
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    start = 12.dp,
                                    top = 16.dp,
                                    end = 12.dp,
                                    bottom = 12.dp,
                                )
                        ) {
                            ChainSelector(
                                title = "From",
                                // TODO selectedChain should not be nullable
                                //  or default value should be something else
                                chain = state.selectedCoin?.model?.address?.chain
                                    ?: Chain.ThorChain,
                                onClick = onSelectNetworkRequest,
                            )

                            UiSpacer(12.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                            ) {
                                TokenChip(
                                    selectedToken = state.selectedCoin,
                                    onSelectTokenClick = onSelectTokenRequest,
                                )

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(1f),
                                ) {
                                    state.selectedCoin?.let { token ->
                                        Text(
                                            text = stringResource(
                                                R.string.form_token_selection_balance,
                                                token.balance ?: ""
                                            ),
                                            color = Theme.colors.text.light,
                                            style = Theme.brockmann.body.s.medium,
                                            textAlign = TextAlign.End,
                                        )

                                        UiSpacer(2.dp)

                                        token.fiatValue?.let { fiatValue ->
                                            Text(
                                                text = fiatValue,
                                                textAlign = TextAlign.End,
                                                color = Theme.colors.text.extraLight,
                                                style = Theme.brockmann.supplementary.caption,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // input dst address
                    FoldableSection(
                        expanded = state.expandedSection == SendSections.Address,
                        complete = state.isDstAddressComplete,
                        title = "Address",
                        onToggle = {
                            onExpandSection(SendSections.Address)
                        },
                        completeTitleContent = {
                            Text(
                                text = addressFieldState.text.toString(),
                                color = Theme.colors.text.extraLight,
                                style = Theme.brockmann.body.s.medium,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    start = 12.dp,
                                    top = 16.dp,
                                    end = 12.dp,
                                    bottom = 12.dp,
                                )
                        ) {
                            Text(
                                text = stringResource(R.string.send_from_address),
                                color = Theme.colors.text.extraLight,
                                style = Theme.brockmann.supplementary.caption,
                            )

                            UiSpacer(12.dp)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = Theme.colors.borders.light,
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .background(
                                        color = Theme.colors.backgrounds.secondary,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp,
                                    ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = state.srcVaultName,
                                    color = Theme.colors.text.primary,
                                    style = Theme.brockmann.supplementary.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                )

                                Text(
                                    text = state.srcAddress,
                                    color = Theme.colors.text.extraLight,
                                    style = Theme.brockmann.supplementary.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                )
                            }

                            UiSpacer(16.dp)

                            Text(
                                text = stringResource(R.string.send_to_address),
                                color = Theme.colors.text.extraLight,
                                style = Theme.brockmann.supplementary.caption,
                            )

                            UiSpacer(12.dp)

                            VsTextInputField(
                                textFieldState = addressFieldState,
                                hint = stringResource(R.string.send_to_address_hint),
                                onFocusChanged = {
                                    if (!it) {
                                        onDstAddressLostFocus()
                                    }
                                },
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next,
                                innerState = if (state.dstAddressError != null)
                                    VsTextInputFieldInnerState.Error
                                else VsTextInputFieldInnerState.Default,
                                footNote = state.dstAddressError?.asString(),
                                modifier = Modifier
                                    .fillMaxWidth(),
                            )

                            UiSpacer(16.dp)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PasteIcon(
                                    modifier = Modifier
                                        .vsClickableBackground()
                                        .padding(all = 12.dp)
                                        .weight(1f),
                                    onPaste = onSetOutputAddress
                                )

                                UiIcon(
                                    drawableResId = R.drawable.camera,
                                    size = 20.dp,
                                    modifier = Modifier
                                        .vsClickableBackground()
                                        .padding(all = 12.dp)
                                        .weight(1f),
                                    onClick = onScanDstAddressRequest,
                                )

                                UiIcon(
                                    drawableResId = R.drawable.ic_bookmark,
                                    size = 20.dp,
                                    modifier = Modifier
                                        .vsClickableBackground()
                                        .padding(all = 12.dp)
                                        .weight(1f),
                                    onClick = onAddressBookClick,
                                )
                            }
                        }
                    }


                    FoldableSection(
                        expanded = state.expandedSection == SendSections.Amount,
                        onToggle = {
                            if (state.isDstAddressComplete &&
                                addressFieldState.text.isNotEmpty()
                            ) {
                                onExpandSection(SendSections.Amount)
                            }
                        },
                        expandedTitleActions = {
                            if (state.hasGasSettings) {
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier
                                        .weight(1f),
                                ) {
                                    UiIcon(
                                        drawableResId = R.drawable.advance_gas_settings, // TODO different icon
                                        size = 16.dp,
                                        tint = Theme.colors.text.primary,
                                        onClick = onGasSettingsClick,
                                    )
                                }
                            }
                        },
                        title = "Amount"
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(
                                    start = 12.dp,
                                    top = 16.dp,
                                    end = 12.dp,
                                    bottom = 12.dp,
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(211.dp)
                                    .fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(
                                            horizontal = 54.dp,
                                        )
                                        .align(Alignment.Center),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    val primaryAmountText: String
                                    val secondaryAmountText: String
                                    val primaryFieldState: TextFieldState
                                    val secondaryFieldState: TextFieldState

                                    if (state.usingTokenAmountInput) {
                                        primaryAmountText = state.selectedCoin?.title ?: ""
                                        secondaryAmountText = state.fiatCurrency
                                        primaryFieldState = tokenAmountFieldState
                                        secondaryFieldState = fiatAmountFieldState
                                    } else {
                                        primaryAmountText = state.fiatCurrency
                                        secondaryAmountText = state.selectedCoin?.title ?: ""
                                        primaryFieldState = fiatAmountFieldState
                                        secondaryFieldState = tokenAmountFieldState
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        BasicTextField(
                                            state = primaryFieldState,
                                            lineLimits = TextFieldLineLimits.MultiLine(
                                                maxHeightInLines = 3,
                                            ),
                                            textStyle = Theme.brockmann.headings.largeTitle
                                                .copy(
                                                    color = Theme.colors.text.primary,
                                                    textAlign = TextAlign.Center,
                                                ),
                                            cursorBrush = Theme.cursorBrush,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Send,
                                            ),
                                            onKeyboardAction = {
                                                focusManager.clearFocus()
                                                onSend()
                                            },
                                            modifier = Modifier
                                                .width(IntrinsicSize.Min),
                                            decorator = { textField ->
                                                if (primaryFieldState.text.isEmpty()) {
                                                    Text(
                                                        text = "0",
                                                        color = Theme.colors.text.light,
                                                        style = Theme.brockmann.headings.largeTitle,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier
                                                            .wrapContentWidth()
                                                    )
                                                }
                                                textField()
                                            }
                                        )

                                        Text(
                                            text = " $primaryAmountText",
                                            color = Theme.colors.text.primary,
                                            style = Theme.brockmann.headings.largeTitle,
                                            textAlign = TextAlign.Center,
                                        )
                                    }

                                    Text(
                                        text = "${secondaryFieldState.text.ifEmpty { "0" }} $secondaryAmountText",
                                        color = Theme.colors.text.extraLight,
                                        style = Theme.brockmann.body.s.medium,
                                        textAlign = TextAlign.Center,
                                    )
                                }

                                TokenFiatToggle(
                                    isTokenSelected = state.usingTokenAmountInput,
                                    onTokenSelected = {
                                        onToogleAmountInputType(it)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd),
                                )
                            }

                            UiSpacer(12.dp)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // TODO indicate selection
                                PercentageChip(
                                    title = "25%",
                                    isSelected = false,
                                    onClick = { onChoosePercentageAmount(0.25f) },
                                    modifier = Modifier
                                        .weight(1f),
                                )

                                PercentageChip(
                                    title = "50%",
                                    isSelected = false,
                                    onClick = { onChoosePercentageAmount(0.5f) },
                                    modifier = Modifier
                                        .weight(1f),
                                )

                                PercentageChip(
                                    title = "75%",
                                    isSelected = false,
                                    onClick = { onChoosePercentageAmount(0.75f) },
                                    modifier = Modifier
                                        .weight(1f),
                                )

                                PercentageChip(
                                    title = "Max",
                                    isSelected = false,
                                    onClick = onChooseMaxTokenAmount,
                                    modifier = Modifier
                                        .weight(1f),
                                )
                            }

                            UiSpacer(12.dp)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .background(
                                        color = Theme.colors.backgrounds.secondary,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(
                                        all = 16.dp,
                                    )
                            ) {
                                Text(
                                    text = "Balance available:",
                                    style = Theme.brockmann.body.s.medium,
                                    color = Theme.colors.text.primary,
                                )

                                Text(
                                    text = state.selectedCoin?.balance ?: "",
                                    style = Theme.brockmann.body.s.medium,
                                    color = Theme.colors.text.light,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier
                                        .weight(1f),
                                )
                            }

                            UiSpacer(12.dp)

                            // memo
                            if (state.hasMemo) {
                                var isMemoExpanded by remember { mutableStateOf(false) }

                                val rotationAngle by animateFloatAsState(
                                    targetValue = if (isMemoExpanded) 180f else 0f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "caretRotation"
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            isMemoExpanded = !isMemoExpanded
                                        }
                                        .padding(
                                            vertical = 2.dp,
                                        )
                                ) {
                                    Text(
                                        text = "Add MEMO",
                                        style = Theme.brockmann.supplementary.caption,
                                        color = Theme.colors.text.extraLight,
                                        modifier = Modifier
                                            .weight(1f),
                                    )

                                    UiIcon(
                                        drawableResId = R.drawable.ic_caret_down,
                                        tint = Theme.colors.text.primary,
                                        size = 16.dp,
                                        modifier = Modifier
                                            .rotate(rotationAngle)
                                    )
                                }

                                UiSpacer(12.dp)

                                AnimatedVisibility(
                                    visible = isMemoExpanded,
                                ) {
                                    val clipboardData = VsClipboardService.getClipboardData()
                                    VsTextInputField(
                                        textFieldState = memoFieldState,
                                        hint = "Enter Memo",
                                        trailingIcon = R.drawable.ic_paste,
                                        onTrailingIconClick = {
                                            clipboardData.value
                                                ?.takeIf { it.isNotEmpty() }
                                                ?.let { memoFieldState.setTextAndPlaceCursorAtEnd(text = it) }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                    )

                                    UiSpacer(12.dp)
                                }
                            }

                            if (state.showGasFee) {
                                FadingHorizontalDivider(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                )

                                UiSpacer(12.dp)

                                EstimatedNetworkFee(
                                    tokenGas = state.totalGas.asString(),
                                    fiatGas = state.estimatedFee.asString(),
                                )
                            }
                        }
                    }

                    UiSpacer(24.dp)

                    // TODO handle this (in a dialog maybe?)
                    AnimatedContent(
                        targetState = state.reapingError,
                        label = "error message"
                    ) { errorMessage ->
                        if (errorMessage != null) {
                            Column {
                                UiSpacer(size = 8.dp)
                                Text(
                                    text = errorMessage.asString(),
                                    color = Theme.colors.error,
                                    style = Theme.menlo.body1
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.send_continue_button),
                state = if (state.isLoading)
                    VsButtonState.Disabled
                else
                    VsButtonState.Enabled,
                onClick = {
                    if (!state.isLoading) {
                        focusManager.clearFocus()
                        onSend()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = 12.dp,
                    ),
            )
        }
    )
}

@Composable
internal fun EstimatedNetworkFee(
    tokenGas: String,
    fiatGas: String,
    isLoading: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Est. network fee",
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .weight(1f),
        ) {
            if (isLoading) {
                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(20.dp)
                        .width(150.dp)
                )

                UiSpacer(6.dp)

                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(20.dp)
                        .width(150.dp)
                )
            } else {
                Text(
                    text = tokenGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.primary,
                )

                Text(
                    text = fiatGas,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.colors.text.extraLight,
                )
            }
        }
    }
}

@Composable
internal fun FadingHorizontalDivider(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Theme.colors.backgrounds.secondary.copy(alpha = 0f),
                        Color(0xFF284570),
                        Theme.colors.backgrounds.secondary.copy(alpha = 0f),
                    ),
                    startX = 0f,
                    endX = Float.POSITIVE_INFINITY,
                    tileMode = TileMode.Clamp
                )
            )
    )
}

@Composable
private fun Modifier.vsClickableBackground() =
    border(
        border = BorderStroke(
            width = 1.dp,
            color = Theme.colors.borders.light,
        ),
        shape = RoundedCornerShape(12.dp),
    )
        .background(
            color = Theme.colors.backgrounds.secondary,
            shape = RoundedCornerShape(12.dp),
        )

@Composable
private fun PercentageChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = Theme.brockmann.supplementary.caption,
        color = Theme.colors.text.light,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected)
                    Modifier.background(
                        color = Theme.colors.primary.accent3,
                        shape = RoundedCornerShape(99.dp),
                    )
                else
                    Modifier.border(
                        width = 1.dp,
                        color = Theme.colors.borders.light,
                        shape = RoundedCornerShape(99.dp),
                    )
            )
            .padding(
                all = 4.dp,
            )
    )
}

@Composable
private fun TokenFiatToggle(
    isTokenSelected: Boolean,
    onTokenSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = RoundedCornerShape(99.dp)
            )
            .padding(
                all = 4.dp,
            )
    ) {
        ToggleButton(
            drawableResId = R.drawable.ic_coins,
            isSelected = isTokenSelected,
            onClick = { onTokenSelected(true) },
        )

        ToggleButton(
            drawableResId = R.drawable.ic_dollar_sign,
            isSelected = !isTokenSelected,
            onClick = { onTokenSelected(false) },
        )
    }
}

@Composable
private fun ToggleButton(
    @DrawableRes drawableResId: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    UiIcon(
        drawableResId = drawableResId,
        size = 16.dp,
        tint = Theme.colors.text.light,
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected)
                    Modifier.background(
                        color = Theme.colors.primary.accent3,
                        shape = CircleShape,
                    )
                else Modifier
            )
            .padding(all = 8.dp)
    )
}


@Composable
private fun FoldableSection(
    expanded: Boolean = false,
    complete: Boolean = false,

    completeTitleContent: (@Composable RowScope.() -> Unit)? = null,
    expandedTitleActions: (@Composable RowScope.() -> Unit)? = null,

    onToggle: () -> Unit = {},

    title: String,

    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Theme.colors.borders.normal,
                shape = RoundedCornerShape(12.dp),
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(all = 16.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = title,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
            )

            if (expanded) {
                expandedTitleActions?.invoke(this)
            } else {
                if (complete) {
                    completeTitleContent?.invoke(this)

                    UiIcon(
                        drawableResId = R.drawable.ic_check, // TODO different icon
                        size = 16.dp,
                        tint = Theme.colors.alerts.success,
                    )

                    UiSpacer(1.dp)

                    UiIcon(
                        drawableResId = R.drawable.pencil, // TODO different icon
                        size = 16.dp,
                        tint = Theme.colors.text.primary,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
        ) {
            FadingHorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                    ),
            )

            content()
        }
    }
}

@Preview
@Composable
private fun SendScreenPreview() {
    SendFormScreen(
        state = SendFormUiModel(
            totalGas = UiText.DynamicString("12.5 Eth"),
            showGasFee = true,
            estimatedFee = UiText.DynamicString("$3.4"),
        ),
        addressFieldState = TextFieldState(),
        tokenAmountFieldState = TextFieldState(),
        fiatAmountFieldState = TextFieldState(),
        memoFieldState = TextFieldState(),
    )
}