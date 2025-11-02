package com.vultisig.wallet.ui.screens.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import coil.compose.SubcomposeAsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.referral.ReferralVaultListViewModel.Companion.VAULT_ID_SELECTED
import com.vultisig.wallet.ui.models.referral.ReferralViewUiState
import com.vultisig.wallet.ui.models.referral.ViewReferralViewModel
import com.vultisig.wallet.ui.screens.transaction.shadeCircle
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun ReferralViewScreen(
    navController: NavController,
    model: ViewReferralViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    val clipboardManager =
        LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

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
        onEditFriendReferralCode = model::navigateToStoreFriendReferralBanner,
        onDismissErrorDialog = model::onDismissErrorDialog,
        onClickEditReferral = model::onClickedEditReferral,
        onVaultClicked = model::onVaultClicked,
        onCreateReferral = model::onCreateReferralClicked,
        onCopyReferralCode = {
            val clip = ClipData.newPlainText("ReferralCode", it)
            clipboardManager?.setPrimaryClip(clip)
        }
    )
}

@Composable
internal fun ReferralViewScreen(
    state: ReferralViewUiState,
    onBackPressed: () -> Unit,
    onClickFriendReferralBanner: () -> Unit,
    onEditFriendReferralCode: () -> Unit,
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

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_view_title),
                onBackClick = {
                    onBackPressed()
                },
            )
        },
        content = { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                if (state.referralFriendCode.isEmpty()) {
                    FriendReferralBanner(
                        onClick = onClickFriendReferralBanner
                    )
                } else {
                    FriendReferralCode(
                        text = state.referralFriendCode,
                        onEditFriendReferralCode = onEditFriendReferralCode,
                    )
                }

                UiSpacer(16.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadeCircle()
                        .border(
                            border = BorderStroke(
                                width = 1.dp,
                                color = Theme.colors.borders.light
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.referral_view_selected_vault),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.colors.text.primary,
                    )

                    UiSpacer(16.dp)

                    VaultItem(
                        name = state.vaultName,
                        onVaultClicked = onVaultClicked,
                    )

                    UiSpacer(16.dp)

                    if (state.referralVaultCode.isEmpty()) {
                        EmptyReferralBanner(onCreateReferral)
                    } else {
                        ReferralDetails(state, onCopyReferralCode)

                        UiSpacer(16.dp)

                        VsButton(
                            label = stringResource(R.string.referral_view_edit_referral),
                            modifier = Modifier.fillMaxWidth(),
                            variant = VsButtonVariant.Primary,
                            state = if (!state.isLoadingExpirationDate && !state.isLoadingRewards) {
                                VsButtonState.Enabled
                            } else {
                                VsButtonState.Disabled
                            },
                            onClick = onClickEditReferral,
                        )
                    }
                }
            }
        },
        bottomBar = {
            UiSpacer(32.dp)
        },
    )
}

@Composable
private fun ReferralDetails(
    state: ReferralViewUiState,
    onCopyReferralCode: (String) -> Unit,
) {
    ReferralRewardsBanner(state.rewardsReferral, state.isLoadingRewards)

    UiSpacer(16.dp)

    Text(
        text = stringResource(R.string.referral_view_your_referral_code),
        style = Theme.brockmann.body.s.medium,
        color = Theme.colors.text.primary,
    )

    UiSpacer(8.dp)

    ContentRow(
        text = state.referralVaultCode,
        icon = {
            UiIcon(
                drawableResId = R.drawable.ic_copy,
                size = 18.dp,
                onClick = { onCopyReferralCode(state.referralVaultCode) }
            )
        }
    )

    UiSpacer(16.dp)

    ReferralExpirationItem(state.referralVaultExpiration, state.isLoadingExpirationDate)
}

@Composable
private fun FriendReferralCode(
    text: String,
    onEditFriendReferralCode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = Theme.colors.borders.light
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = stringResource(R.string.referral_view_your_friend_referral),
            style = Theme.brockmann.body.s.medium,
            color = Theme.colors.text.primary,
        )

        UiSpacer(8.dp)

        ContentRow(
            text = text,
            icon = {
                UiIcon(
                    drawableResId = R.drawable.ic_edit_pencil,
                    size = 18.dp,
                    onClick = { onEditFriendReferralCode() }
                )
            }
        )
    }
}

@Composable
private fun ReferralExpirationItem(expiration: String = "25 May of 2027", isLoading: Boolean) {
    Column(
        modifier = Modifier
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = Theme.colors.borders.light
                ),
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.colors.backgrounds.primary)
            .fillMaxWidth()
            .padding(all = 16.dp),
    ) {
        Text(
            color = Theme.colors.text.extraLight,
            style = Theme.brockmann.body.s.medium,
            text = stringResource(R.string.referral_view_expires_on)
        )
        if (isLoading) {
            UiSpacer(2.dp)

            UiPlaceholderLoader(
                modifier = Modifier
                    .height(22.dp)
                    .width(130.dp)
            )
        } else {
            Text(
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.l.medium,
                text = expiration,
            )
        }
    }
}

@Composable
private fun FriendReferralBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.referral_friend_banner),
            contentDescription = "Provider Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.referral_view_save))
                    withStyle(style = SpanStyle(color = Theme.colors.primary.accent4)) {
                        append(" 10% ")
                    }
                    append(stringResource(R.string.referral_view_on_swaps))
                },
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.supplementary.caption,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.referral_view_add_friend),
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
            )
        }
    }
}

@Composable
private fun ReferralRewardsBanner(
    rewards: String,
    isLoading: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        SetBackgoundBanner(backgroundImageResId = R.drawable.referral_data_banner)

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            UiIcon(
                drawableResId = R.drawable.ic_cup,
                size = 24.dp,
                tint = Theme.colors.primary.accent4,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.referral_view_collected_rewards),
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
            )

            if (isLoading) {
                UiSpacer(2.dp)

                UiPlaceholderLoader(
                    modifier = Modifier
                        .height(22.dp)
                        .width(130.dp)
                )
            } else {
                Text(
                    text = rewards,
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.body.l.medium,
                )
            }
        }
    }
}


@Composable
fun BoxScope.SetBackgoundBanner(
    backgroundImageResId: Int,
) {
    Image(
        painter = painterResource(backgroundImageResId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
    )
}

@Composable
fun VaultItem(
    name: String,
    onVaultClicked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Theme.colors.backgrounds.primary)
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(28.dp)
            )
            .clickable { onVaultClicked() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = R.drawable.referral_vault_avatar,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )

        UiSpacer(12.dp)

        Text(
            text = name,
            color = Theme.colors.text.primary,
            style = Theme.brockmann.body.m.regular,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "click_referral_vault",
            tint = Theme.colors.text.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun ContentRow(
    text: String,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.colors.backgrounds.primary)
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Theme.colors.text.primary,
            style = Theme.brockmann.body.m.regular,
        )

        Spacer(modifier = Modifier.weight(1f))

        icon()
    }
}

@Composable
internal fun EmptyReferralBanner(onClickedCreateReferral: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadeCircle()
            .background(Theme.colors.backgrounds.primary)
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    color = Theme.colors.borders.light
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UiSpacer(32.dp)

        Image(
            painter = painterResource(id = R.drawable.referral_question),
            contentDescription = "Empty Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(42.dp)
        )

        UiSpacer(16.dp)

        Text(
            style = Theme.brockmann.body.m.medium,
            text = stringResource(R.string.referral_not_found),
            color = Theme.colors.text.primary,
            textAlign = TextAlign.Center,
        )

        UiSpacer(16.dp)

        Text(
            style = Theme.brockmann.supplementary.caption,
            text = stringResource(R.string.referral_cta),
            color = Theme.colors.text.extraLight,
            textAlign = TextAlign.Center
        )

        UiSpacer(16.dp)

        VsButton(
            label = stringResource(R.string.referral_create_referral),
            modifier = Modifier.fillMaxWidth(),
            variant = VsButtonVariant.Primary,
            onClick = onClickedCreateReferral,
        )

        UiSpacer(8.dp)
    }
}

@Preview(showBackground = true)
@Composable
private fun ReferralViewScreenPreview() {
    ReferralViewScreen(
        state = ReferralViewUiState(
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
        onEditFriendReferralCode = {},
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
        state = ReferralViewUiState(
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
        onEditFriendReferralCode = {},
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
        state = ReferralViewUiState(
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
        onEditFriendReferralCode = {},
        onCopyReferralCode = {},
        onDismissErrorDialog = {},
        onClickEditReferral = {},
        onVaultClicked = {},
        onCreateReferral = {},
    )
}

