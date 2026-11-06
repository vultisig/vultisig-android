package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.SubcomposeAsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.referral.ReferralVaultListViewModel.Companion.VAULT_ID_SELECTED
import com.vultisig.wallet.ui.models.referral.ReferralViewUiState
import com.vultisig.wallet.ui.models.referral.ViewReferralViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.VsClipboardService
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun ReferralViewScreen(
    navController: NavController,
    model: ViewReferralViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val context = LocalContext.current

    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.let { handle ->
            snapshotFlow { handle.get<String>(VAULT_ID_SELECTED) }
                .distinctUntilChanged()
                .collect { code ->
                    if (code != null) {
                        model.onVaultSelected(code)
                        handle.remove<String>(VAULT_ID_SELECTED)
                    }
                }
        }
    }

    ReferralViewScreen(
        state = state,
        onBackPressed = {
            val code = model.state.value.referralFriendCode
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(NEW_EXTERNAL_REFERRAL_CODE, code)
            navController.popBackStack()
        },
        onClickFriendReferralBanner = model::navigateToStoreFriendReferralBanner,
        onDismissErrorDialog = model::onDismissErrorDialog,
        onClickEditReferral = model::onClickedEditReferral,
        onVaultClicked = model::onVaultClicked,
        onCreateReferral = model::onCreateReferralClicked,
        onCopyReferralCode = { VsClipboardService.copy(context, it) },
    )
}

@Composable
internal fun ReferralViewScreen(
    state: ReferralViewUiState,
    onBackPressed: () -> Unit,
    onClickFriendReferralBanner: () -> Unit,
    onCopyReferralCode: (String) -> Unit,
    onDismissErrorDialog: () -> Unit,
    onClickEditReferral: () -> Unit,
    onVaultClicked: () -> Unit,
    onCreateReferral: () -> Unit,
) {
    if (state.error.isNotEmpty()) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = stringResource(R.string.error_loading_information),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = onDismissErrorDialog,
        )
    }

    V2Scaffold(
        title = stringResource(R.string.referral_main_my_referral_title),
        onBackClick = onBackPressed,
        bottomBar = { UiSpacer(32.dp) },
        content = {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .navigationBarsPadding()
            ) {
                FriendReferralBanner(
                    isActive = state.referralFriendCode.isNotEmpty(),
                    onClick = onClickFriendReferralBanner,
                )

                UiSpacer(8.dp)

                SelectedVaultPanel(
                    state = state,
                    onVaultClicked = onVaultClicked,
                    onCreateReferral = onCreateReferral,
                    onCopyReferralCode = onCopyReferralCode,
                    onClickEditReferral = onClickEditReferral,
                )
            }
        },
    )
}

@Composable
private fun SelectedVaultPanel(
    state: ReferralViewUiState,
    onVaultClicked: () -> Unit,
    onCreateReferral: () -> Unit,
    onCopyReferralCode: (String) -> Unit,
    onClickEditReferral: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = Theme.v2.colors.backgrounds.primary.copy(alpha = 0.5f),
                    shape = Theme.v2.radius.xl,
                )
                .border(
                    border =
                        BorderStroke(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                        ),
                    shape = Theme.v2.radius.xl,
                )
                .padding(PANEL_GAP),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        Text(
            text = stringResource(R.string.referral_view_selected_vault),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )

        VaultItem(name = state.vaultName, onVaultClicked = onVaultClicked)

        if (state.referralVaultCode.isEmpty()) {
            EmptyReferralBanner(onCreateReferral)
        } else {
            ReferralDetails(state, onCopyReferralCode)

            VsButton(
                label = stringResource(R.string.referral_view_edit_referral),
                modifier = Modifier.fillMaxWidth(),
                variant = VsButtonVariant.Primary,
                state =
                    if (!state.isLoadingExpirationDate && !state.isLoadingRewards) {
                        VsButtonState.Enabled
                    } else {
                        VsButtonState.Disabled
                    },
                onClick = onClickEditReferral,
            )
        }
    }
}

private val PANEL_GAP = 14.dp

@Composable
private fun ReferralDetails(state: ReferralViewUiState, onCopyReferralCode: (String) -> Unit) {
    ReferralRewardsBanner(state.rewardsReferral, state.isLoadingRewards)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.referral_view_your_referral_code),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )

        ContentRow(
            text = state.referralVaultCode,
            icon = {
                UiIcon(
                    drawableResId = R.drawable.copy2,
                    size = 20.dp,
                    tint = Color.Unspecified,
                    onClick = { onCopyReferralCode(state.referralVaultCode) },
                )
            },
        )
    }

    ReferralExpirationItem(state.referralVaultExpiration, state.isLoadingExpirationDate)
}

@Composable
private fun ReferralExpirationItem(expiration: String = "25 May of 2027", isLoading: Boolean) {
    Column(
        modifier =
            Modifier.border(
                    border = BorderStroke(width = 1.dp, color = Theme.v2.colors.border.light),
                    shape = Theme.v2.radius.md,
                )
                .clip(Theme.v2.radius.md)
                .background(Theme.v2.colors.backgrounds.primary)
                .fillMaxWidth()
                .padding(all = PANEL_GAP)
    ) {
        Text(
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.body.s.medium,
            text = stringResource(R.string.referral_view_expires_on),
        )
        if (isLoading) {
            UiSpacer(2.dp)

            UiPlaceholderLoader(modifier = Modifier.height(22.dp).width(130.dp))
        } else {
            Text(
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.l.medium,
                text = expiration,
            )
        }
    }
}

@Composable
private fun FriendReferralBanner(isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(86.dp)
                .clip(Theme.v2.radius.xl)
                .background(Theme.v2.colors.backgrounds.surface1)
                .clickable { onClick() }
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.xl,
                )
    ) {
        Image(
            painter = painterResource(id = R.drawable.referral_friend_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )

        Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
            Text(
                text =
                    buildAnnotatedString {
                        append(stringResource(R.string.referral_view_save))
                        withStyle(style = SpanStyle(color = Theme.v2.colors.primary.accent4)) {
                            append(" 10% ")
                        }
                        append(stringResource(R.string.referral_view_on_swaps))
                    },
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.caption,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = stringResource(R.string.referral_view_add_friend),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
            )
        }

        if (isActive) {
            FriendActiveChip(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp)
            )
        }
    }
}

@Composable
private fun FriendActiveChip(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .clip(Theme.v2.radius.pill)
                .background(Theme.v2.colors.primary.accent4.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(Theme.v2.radius.pill).background(FRIEND_ACTIVE_DOT))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.referral_view_friend_active),
            color = Theme.v2.colors.primary.accent4,
            style = Theme.brockmann.supplementary.caption,
        )
    }
}

private val FRIEND_ACTIVE_DOT = Color(0xFF4ADE80)

@Composable
private fun ReferralRewardsBanner(rewards: String, isLoading: Boolean) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(108.dp)
                .clip(Theme.v2.radius.md)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.md,
                )
    ) {
        SetBackgroundBanner(backgroundImageResId = R.drawable.referral_data_banner)

        Column(modifier = Modifier.padding(PANEL_GAP)) {
            UiIcon(
                drawableResId = R.drawable.ic_cup,
                size = 20.dp,
                tint = Theme.v2.colors.text.primary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.referral_view_collected_rewards),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
            )

            if (isLoading) {
                UiSpacer(2.dp)

                UiPlaceholderLoader(modifier = Modifier.height(22.dp).width(130.dp))
            } else {
                Text(
                    text = rewards,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.body.l.medium,
                )
            }
        }
    }
}

@Composable
fun BoxScope.SetBackgroundBanner(backgroundImageResId: Int) {
    Image(
        painter = painterResource(backgroundImageResId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
    )
}

@Composable
fun VaultItem(name: String, onVaultClicked: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(48.dp)
                .clip(Theme.v2.radius.pill)
                .background(Theme.v2.colors.backgrounds.primary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.pill,
                )
                .clickable { onVaultClicked() }
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubcomposeAsyncImage(
            model = R.drawable.referral_vault_avatar,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )

        UiSpacer(10.dp)

        Text(
            text = name,
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.body.m.regular,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            painter = painterResource(R.drawable.ic_caret_right),
            contentDescription = null,
            tint = Theme.v2.colors.text.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
internal fun ContentRow(text: String, icon: @Composable () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(Theme.v2.radius.lg)
                .background(Theme.v2.colors.backgrounds.primary)
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.variables.bordersExtraLight,
                    shape = Theme.v2.radius.lg,
                )
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.body.s.medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        UiSpacer(12.dp)

        icon()
    }
}

@Composable
internal fun EmptyReferralBanner(onClickedCreateReferral: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Theme.v2.colors.backgrounds.surface1, EMPTY_CARD_SHAPE)
                .padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.circle_dashed),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )

        UiSpacer(12.dp)

        Text(
            style = Theme.brockmann.headings.title3,
            text = stringResource(R.string.referral_not_found),
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(8.dp)

        Text(
            style = Theme.brockmann.supplementary.footnote,
            text = stringResource(R.string.referral_cta),
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(16.dp)

        VsButton(
            variant = VsButtonVariant.Primary,
            size = VsButtonSize.Mini,
            onClick = onClickedCreateReferral,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiIcon(
                    drawableResId = R.drawable.plus_small,
                    size = 16.dp,
                    tint = Color.Unspecified,
                )

                Text(
                    text = stringResource(R.string.referral_create_referral),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.button.primary,
                )
            }
        }
    }
}

private val EMPTY_CARD_SHAPE = RoundedCornerShape(20.dp)

@Preview(showBackground = true)
@Composable
private fun ReferralViewScreenPreview() {
    ReferralViewScreen(
        state =
            ReferralViewUiState(
                referralFriendCode = "FRIEND-REF-2024",
                referralVaultCode = "VAULT-REF-ABC123",
                referralVaultExpiration = "December 31, 2025",
                vaultName = "My Secure Vault",
                rewardsReferral = "0.5 RUNE ($25.00)",
                isLoadingRewards = false,
                isLoadingExpirationDate = false,
                error = "",
            ),
        onBackPressed = {},
        onClickFriendReferralBanner = {},
        onCopyReferralCode = {},
        onDismissErrorDialog = {},
        onClickEditReferral = {},
        onVaultClicked = {},
        onCreateReferral = {},
    )
}

@Preview(showBackground = true, name = "Loading State")
@Composable
private fun ReferralViewScreenLoadingPreview() {
    ReferralViewScreen(
        state =
            ReferralViewUiState(
                referralFriendCode = "",
                referralVaultCode = "VAULT-REF-ABC123",
                referralVaultExpiration = "",
                vaultName = "My Secure Vault",
                rewardsReferral = "",
                isLoadingRewards = true,
                isLoadingExpirationDate = true,
                error = "",
            ),
        onBackPressed = {},
        onClickFriendReferralBanner = {},
        onCopyReferralCode = {},
        onDismissErrorDialog = {},
        onClickEditReferral = {},
        onVaultClicked = {},
        onCreateReferral = {},
    )
}

@Preview(showBackground = true, name = "Error State")
@Composable
private fun ReferralViewScreenErrorPreview() {
    ReferralViewScreen(
        state =
            ReferralViewUiState(
                referralFriendCode = "FRIEND-REF-2024",
                referralVaultCode = "VAULT-REF-ABC123",
                referralVaultExpiration = "December 31, 2025",
                vaultName = "My Secure Vault",
                rewardsReferral = "0.5 RUNE ($25.00)",
                isLoadingRewards = false,
                isLoadingExpirationDate = false,
                error = "Failed to load referral information",
            ),
        onBackPressed = {},
        onClickFriendReferralBanner = {},
        onCopyReferralCode = {},
        onDismissErrorDialog = {},
        onClickEditReferral = {},
        onVaultClicked = {},
        onCreateReferral = {},
    )
}
