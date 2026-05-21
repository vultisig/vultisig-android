package com.vultisig.wallet.ui.screens.referral

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.StyledText
import com.vultisig.wallet.ui.components.StyledTextPart
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.referral.ReferralUiState
import com.vultisig.wallet.ui.models.referral.ReferralViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ReferralScreen(
    navController: NavController,
    model: ReferralViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()
    ReferralScreen(
        state = state,
        onBackPressed = navController::popBackStack,
        onCreateCardClick = model::onCreateOrEditReferral,
        onMyReferralCardClick = model::onMyReferralClick,
    )
}

@Composable
private fun ReferralScreen(
    state: ReferralUiState,
    onBackPressed: () -> Unit,
    onCreateCardClick: () -> Unit,
    onMyReferralCardClick: () -> Unit,
) {
    val ready = state as? ReferralUiState.Ready
    val createEnabled = ready != null
    val createLabel =
        if (ready?.hasReferral == true) R.string.referral_edit_referral
        else R.string.referral_create_referral
    val myReferralEnabled = state !is ReferralUiState.Loading

    V2Scaffold(
        onBackClick = onBackPressed,
        title = stringResource(R.string.referral_screen_title),
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.referral_hero),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                CreateReferralCard(
                    titleRes = createLabel,
                    enabled = createEnabled,
                    onClick = onCreateCardClick,
                )

                UiSpacer(14.dp)

                ReferralEntryCard(
                    iconRes = R.drawable.ic_image_avatar_sparkle,
                    title = stringResource(R.string.referral_main_my_referral_title),
                    bodyText = stringResource(R.string.referral_main_my_referral_body),
                    enabled = myReferralEnabled,
                    onClick = onMyReferralCardClick,
                )
            }
        }
    }
}

@Composable
private fun CreateReferralCard(titleRes: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.surface1)
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED_CARD_ALPHA)
                .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_megaphone),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(titleRes),
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.primary,
                )
            }
            StyledText(
                parts =
                    listOf(
                        StyledTextPart(
                            stringResource(R.string.referral_create_code_and_earn),
                            Theme.v2.colors.text.secondary,
                        ),
                        StyledTextPart("20%", Theme.v2.colors.primary.accent4),
                        StyledTextPart(
                            stringResource(R.string.referral_on_referred_swaps),
                            Theme.v2.colors.text.secondary,
                        ),
                    ),
                fontSize = Theme.brockmann.supplementary.footnote.fontSize,
                fontFamily = Theme.brockmann.supplementary.footnote.fontFamily,
                fontWeight = Theme.brockmann.supplementary.footnote.fontWeight,
                textAlign = TextAlign.Start,
            )
        }
        Image(
            painter = painterResource(id = R.drawable.ic_chevron_right_small),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ReferralEntryCard(
    iconRes: Int,
    title: String,
    bodyText: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.surface1)
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED_CARD_ALPHA)
                .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = title,
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.primary,
                )
            }
            Text(
                text = bodyText,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.secondary,
            )
        }
        Image(
            painter = painterResource(id = R.drawable.ic_chevron_right_small),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}

private const val DISABLED_CARD_ALPHA = 0.5f

@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true)
@Composable
private fun ReferralScreenLoadingPreview() {
    ReferralScreen(
        state = ReferralUiState.Loading,
        onBackPressed = {},
        onCreateCardClick = {},
        onMyReferralCardClick = {},
    )
}

@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true, name = "Ready – no referral")
@Composable
private fun ReferralScreenReadyEmptyPreview() {
    ReferralScreen(
        state = ReferralUiState.Ready(hasReferral = false),
        onBackPressed = {},
        onCreateCardClick = {},
        onMyReferralCardClick = {},
    )
}

@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true, name = "Ready – has referral")
@Composable
private fun ReferralScreenReadyWithReferralPreview() {
    ReferralScreen(
        state = ReferralUiState.Ready(hasReferral = true),
        onBackPressed = {},
        onCreateCardClick = {},
        onMyReferralCardClick = {},
    )
}
