package com.vultisig.wallet.ui.screens.referral

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MoreInfoBox
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiGradientDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.inputs.VsTextInputField
import com.vultisig.wallet.ui.components.inputs.VsTextInputFieldInnerState
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.CreateReferralUiState
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel
import com.vultisig.wallet.ui.models.referral.FeesReferral
import com.vultisig.wallet.ui.models.referral.ReferralError
import com.vultisig.wallet.ui.models.referral.SearchStatusType
import com.vultisig.wallet.ui.models.referral.isError
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralCreateScreen(
    navController: NavController,
    model: CreateReferralViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    ReferralCreateScreen(
        state = state,
        searchTextFieldState = model.searchReferralTexFieldState,
        onBackPressed = navController::popBackStack,
        onSearchClick = model::onSearchReferralCode,
        onAddClick = model::onAddExpirationYear,
        onSubtractClick = model::onSubtractExpirationYear,
        onCreateReferral = model::onCreateReferralCode,
        onCleanReferralClick = model::onCleanReferralClick,
        onDismissError = model::onDismissError,
        onToggleInfoBox = { model.setInfoBoxVisibility(!state.showInfoBox) },
        onHideInfoBox = { model.setInfoBoxVisibility(false) },
    )
}

@Composable
private fun ReferralCreateScreen(
    state: CreateReferralUiState,
    searchTextFieldState: TextFieldState,
    onBackPressed: () -> Unit,
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubtractClick: () -> Unit,
    onCreateReferral: () -> Unit,
    onCleanReferralClick: () -> Unit,
    onDismissError: () -> Unit,
    onToggleInfoBox: () -> Unit,
    onHideInfoBox: () -> Unit,
) {
    val statusBarHeightPx = WindowInsets.statusBars.getTop(LocalDensity.current)
    val statusBarHeightDp = with(LocalDensity.current) { statusBarHeightPx.toDp() }

    if (state.error != null) {
        val message = when (state.error) {
            ReferralError.BALANCE_ERROR -> stringResource(R.string.referral_create_not_enough_balance)
            else -> stringResource(R.string.referral_create_unknown_error)
        }

        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = message,
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = onDismissError,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Theme.colors.backgrounds.primary,
            topBar = {
                VsTopAppBar(
                    title = stringResource(R.string.referral_create_create_referral),
                    onBackClick = onBackPressed,
                    iconRight = R.drawable.ic_info,
                    onIconRightClick = onToggleInfoBox,
                )
            },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.body.s.medium,
                    text = stringResource(R.string.referral_create_pick_referral_code),
                    textAlign = TextAlign.Start,
                )

                UiSpacer(8.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isNotEmpty = searchTextFieldState.text.isNotEmpty()

                    VsTextInputField(
                        textFieldState = searchTextFieldState,
                        innerState = state.getInnerState(),
                        hint = stringResource(R.string.referral_screen_code_hint),
                        focusRequester = null,
                        trailingIcon = if (state.searchStatus.isError() && isNotEmpty) {
                            R.drawable.x
                        } else {
                            null
                        },
                        onTrailingIconClick = {
                            onCleanReferralClick()
                        },
                        imeAction = ImeAction.Go,
                        keyboardType = KeyboardType.Text,
                        modifier = Modifier.weight(1f)
                    )

                    UiSpacer(8.dp)

                    VsButton(
                        label = stringResource(R.string.referral_create_search),
                        shape = RoundedCornerShape(percent = 20),
                        variant = VsButtonVariant.Primary,
                        state = VsButtonState.Enabled,
                        size = VsButtonSize.Medium,
                        onClick = onSearchClick,
                        modifier = Modifier.fillMaxHeight(),
                    )
                }

                UiSpacer(16.dp)

                if (state.searchStatus != SearchStatusType.DEFAULT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            color = Theme.colors.text.extraLight,
                            style = Theme.brockmann.body.s.medium,
                            text = stringResource(R.string.referral_create_status),
                            textAlign = TextAlign.Start,
                        )

                        UiSpacer(1f)

                        if (state.searchStatus == SearchStatusType.IS_SEARCHING) {
                            UiPlaceholderLoader(
                                modifier = Modifier
                                    .height(16.dp)
                                    .width(80.dp)
                            )
                        } else {
                            SearchReferralTag(state.searchStatus)
                        }
                    }
                }

                UiSpacer(16.dp)

                UiGradientDivider(
                    initialColor = Theme.colors.backgrounds.primary,
                    endColor = Theme.colors.backgrounds.primary,
                )

                UiSpacer(16.dp)

                Text(
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.body.s.medium,
                    text = stringResource(R.string.referral_create_set_expiration_years),
                    textAlign = TextAlign.Start,
                )

                UiSpacer(16.dp)

                CounterYearExpiration(
                    count = state.yearExpiration,
                    onIncrement = onAddClick,
                    onDecrement = onSubtractClick,
                )

                UiSpacer(16.dp)


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        color = Theme.colors.text.extraLight,
                        style = Theme.brockmann.body.s.medium,
                        text = stringResource(R.string.referral_create_expiration_date),
                        textAlign = TextAlign.Start,
                    )

                    UiSpacer(1f)

                    Text(
                        color = Theme.colors.text.primary,
                        style = Theme.brockmann.body.s.medium,
                        text = state.formattedYearExpiration,
                        textAlign = TextAlign.Start,
                    )
                }

                UiSpacer(16.dp)

                UiGradientDivider(
                    initialColor = Theme.colors.backgrounds.primary,
                    endColor = Theme.colors.backgrounds.primary,
                )

                UiSpacer(16.dp)

                val fees = state.getFees()

                EstimatedNetworkFee(
                    title = stringResource(R.string.referral_create_registration_fees),
                    tokenGas = fees.tokenGas,
                    fiatGas = fees.tokenPrice,
                )

                UiSpacer(16.dp)

                EstimatedNetworkFee(
                    title = stringResource(R.string.referral_create_cost),
                    tokenGas = fees.costGas,
                    fiatGas = fees.costPrice,
                )
            }
        },
        bottomBar = {
            VsButton(
                label = stringResource(R.string.referral_create_create_referral_code),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 32.dp)
                    .fillMaxWidth(),
                variant = VsButtonVariant.Primary,
                state = if (state.searchStatus == SearchStatusType.SUCCESS) {
                    VsButtonState.Enabled
                } else {
                    VsButtonState.Disabled
                },
                onClick = onCreateReferral,
                )
            }
        )

        ShowInfoDialog(
            isVisible = state.showInfoBox,
            statusBarHeightDp = statusBarHeightDp,
            onHideInfoBox = onHideInfoBox,
        )
    }
}

private fun CreateReferralUiState.getFees() =
    if (this.fees is FeesReferral.Result) {
        FeesUiModel(
        tokenGas = this.fees.registrationFeesToken,
        tokenPrice = this.fees.registrationFeesPrice,
        costGas = this.fees.costFeesToken,
        costPrice = this.fees.costFeesPrice,
    )
} else {
    FeesUiModel()
}

private fun CreateReferralUiState.getInnerState() =
    when {
        this.searchStatus.isError() -> VsTextInputFieldInnerState.Error
        this.searchStatus == SearchStatusType.SUCCESS -> VsTextInputFieldInnerState.Success
        else -> VsTextInputFieldInnerState.Default
    }

@Composable
private fun ShowInfoDialog(
    isVisible: Boolean,
    statusBarHeightDp: Dp,
    onHideInfoBox: () -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onHideInfoBox
                )
        ) {
            MoreInfoBox(
                text = stringResource(R.string.referral_create_info_content),
                title = stringResource(R.string.referral_create_info_title),
                modifier = Modifier
                    .padding(start = 62.dp, end = 8.dp, top = 8.dp)
                    .offset(y = statusBarHeightDp)
                    .wrapContentSize()
                    .align(Alignment.TopStart)
                    .clickable(onClick = onHideInfoBox)
            )
        }
    }
}

@Composable
private fun SearchReferralTag(
    type: SearchStatusType,
) {
    val (color, text) = when (type) {
        SearchStatusType.VALIDATION_ERROR -> Pair(Theme.colors.alerts.error, stringResource(R.string.referral_create_invalid))
        SearchStatusType.SUCCESS -> Pair(Theme.colors.alerts.success, stringResource(R.string.referral_create_available))
        SearchStatusType.ERROR -> Pair(Theme.colors.alerts.error, stringResource(R.string.referral_create_taken))
        else -> Pair(Theme.colors.alerts.error, stringResource(R.string.referral_create_unknown))
    }

    Box(
        modifier = Modifier
            .background(
                color = Theme.colors.backgrounds.secondary,
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            style = Theme.brockmann.supplementary.footnote,
        )
    }
}

@Composable
fun CounterYearExpiration(
    count: Int,
    defaultInitCounter: Int = 1,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { if (count != defaultInitCounter) onDecrement() },
            shape = RoundedCornerShape(12.dp),
            enabled = count != defaultInitCounter,
            colors = ButtonDefaults.buttonColors(
                containerColor = Theme.colors.backgrounds.secondary,
                contentColor = Theme.colors.text.primary,
                disabledContainerColor = Theme.colors.backgrounds.secondary.copy(alpha = 0.5f),
                disabledContentColor = Theme.colors.text.primary.copy(alpha = 0.5f),
            ),
            modifier = Modifier
                .weight(1f)
                .height(height = 60.dp)
                .border(1.dp, Theme.colors.borders.normal, RoundedCornerShape(12.dp)),
        ) {
            Icon(
                painter = painterResource(R.drawable.circle_minus),
                contentDescription = "Decrease"
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(height = 60.dp)
                .border(1.dp, Theme.colors.borders.normal, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.m.medium,
            )
        }

        Button(
            onClick = { if (count < 100) onIncrement() },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Theme.colors.backgrounds.secondary,
                contentColor = Theme.colors.text.primary
            ),
            modifier = Modifier
                .weight(1f)
                .height(height = 60.dp)
                .border(1.dp, Theme.colors.borders.normal, RoundedCornerShape(12.dp)),
        ) {
            Icon(
                painter = painterResource(R.drawable.circle_plus),
                contentDescription = "Increase"
            )
        }
    }
}

@Composable
internal fun EstimatedNetworkFee(
    title: String,
    tokenGas: String,
    fiatGas: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.colors.text.extraLight,
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .weight(1f),
        ) {
            if (tokenGas.isEmpty()) {
                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(16.dp)
                        .width(80.dp)
                )

                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(16.dp)
                        .width(80.dp)
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

@Preview(showBackground = true)
@Composable
private fun ReferralCreateScreenPreview() {
    val searchTextFieldState = TextFieldState("VULTISIG-REF")
    
    ReferralCreateScreen(
        state = CreateReferralUiState(
            searchStatus = SearchStatusType.SUCCESS,
            yearExpiration = 2,
            formattedYearExpiration = "December 31, 2026",
            fees = FeesReferral.Result(
                registrationFeesToken = "0.02 RUNE",
                registrationFeesPrice = "$1.50",
                costFeesToken = "0.04 RUNE",
                costFeesTokenAmount = "0.04",
                costFeesPrice = "$3.00"
            ),
            error = null
        ),
        searchTextFieldState = searchTextFieldState,
        onBackPressed = {},
        onSearchClick = {},
        onAddClick = {},
        onSubtractClick = {},
        onCreateReferral = {},
        onCleanReferralClick = {},
        onDismissError = {},
        onToggleInfoBox = {},
        onHideInfoBox = {},
    )
}

@Preview(showBackground = true, name = "Loading State")
@Composable
private fun ReferralCreateScreenLoadingPreview() {
    ReferralCreateScreen(
        state = CreateReferralUiState(
            searchStatus = SearchStatusType.IS_SEARCHING,
            yearExpiration = 1,
            formattedYearExpiration = "December 31, 2025",
            fees = FeesReferral.Loading,
            error = null
        ),
        searchTextFieldState = TextFieldState(),
        onBackPressed = {},
        onSearchClick = {},
        onAddClick = {},
        onSubtractClick = {},
        onCreateReferral = {},
        onCleanReferralClick = {},
        onDismissError = {},
        onToggleInfoBox = {},
        onHideInfoBox = {},
    )
}

private data class FeesUiModel(
    val tokenGas: String = "",
    val tokenPrice: String = "",
    val costGas: String = "",
    val costPrice: String = "",
)