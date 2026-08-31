package com.vultisig.wallet.ui.screens.v2.chaintokens.bottomsheets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.loading.V2Loading
import com.vultisig.wallet.ui.models.RippleTrustLineActivationUiModel
import com.vultisig.wallet.ui.models.RippleTrustLineActivationViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun RippleTrustLineActivationSheet(
    viewModel: RippleTrustLineActivationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RippleTrustLineActivationSheet(
        state = state,
        onActivate = viewModel::activate,
        onDismiss = viewModel::dismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RippleTrustLineActivationSheet(
    state: RippleTrustLineActivationUiModel,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    V2BottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 8.dp)
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier.size(56.dp)
                            .clip(CircleShape)
                            .background(Theme.v2.colors.backgrounds.secondary)
                            .border(
                                width = 1.dp,
                                color = Theme.v2.colors.alerts.warning.copy(alpha = 0.4f),
                                shape = CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    UiIcon(
                        drawableResId = R.drawable.ic_triangle_alert,
                        size = 24.dp,
                        tint = Theme.v2.colors.alerts.warning,
                    )
                }

                UiSpacer(24.dp)

                Text(
                    text = stringResource(R.string.ripple_trust_line_title),
                    style = Theme.brockmann.headings.title2,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                )

                UiSpacer(12.dp)

                Text(
                    text = stringResource(R.string.ripple_trust_line_reserve_warning, state.ticker),
                    style = Theme.brockmann.body.s.regular,
                    color = Theme.v2.colors.text.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                UiSpacer(24.dp)

                when {
                    state.isLoading -> V2Loading(modifier = Modifier.size(32.dp))

                    state.error != null ->
                        Text(
                            text = state.error.asString(),
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.alerts.error,
                            textAlign = TextAlign.Center,
                        )

                    else -> {
                        V2Container(
                            type = ContainerType.SECONDARY,
                            radius = Theme.v2.radius.md,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ActivationRow(R.string.ripple_trust_line_reserve, state.reserve)
                                ActivationRow(R.string.send_form_est_network_fee, state.networkFee)
                                ActivationRow(
                                    R.string.ripple_trust_line_spendable_after,
                                    state.spendableAfter,
                                )
                                ActivationRow(R.string.ripple_trust_line_limit, state.limit)
                                ActivationRow(
                                    R.string.ripple_field_issuer,
                                    state.issuer,
                                    overflow = TextOverflow.MiddleEllipsis,
                                )
                            }
                        }

                        if (!state.isAffordable) {
                            UiSpacer(12.dp)

                            Text(
                                text = stringResource(R.string.ripple_trust_line_insufficient_xrp),
                                style = Theme.brockmann.supplementary.caption,
                                color = Theme.v2.colors.alerts.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            if (!state.isLoading && state.error == null) {
                UiSpacer(24.dp)

                VsButton(
                    label = stringResource(R.string.ripple_trust_line_activate),
                    state =
                        if (state.isAffordable && !state.isActivating) VsButtonState.Enabled
                        else VsButtonState.Disabled,
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ActivationRow(
    @StringRes titleRes: Int,
    value: String,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )
        Text(
            text = value,
            style = Theme.satoshi.price.bodyS,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
            overflow = overflow,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
