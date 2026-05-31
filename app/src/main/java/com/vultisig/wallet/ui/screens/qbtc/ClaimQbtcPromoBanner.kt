package com.vultisig.wallet.ui.screens.qbtc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.theme.Theme

/**
 * "Claim your QBTC" promo card shown on the Bitcoin chain-detail screen when the vault is eligible
 * (has a QBTC key + claimable UTXOs). Tapping the CTA opens the claim flow. Mirrors iOS
 * `ClaimQbtcPromoBanner`.
 */
@Composable
internal fun ClaimQbtcPromoBanner(onClaim: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.v2.colors.backgrounds.surface2)
                .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.qbtc_claim_banner_subtitle),
            style = Theme.brockmann.body.xs.regular,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
        )
        UiSpacer(size = 8.dp)
        Text(
            text = stringResource(R.string.qbtc_claim_banner_title),
            style = Theme.brockmann.headings.title2,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
        )
        UiSpacer(size = 16.dp)
        VsButton(
            label = stringResource(R.string.qbtc_claim_banner_cta),
            size = VsButtonSize.Small,
            onClick = onClaim,
        )
    }
}
