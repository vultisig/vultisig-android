package com.vultisig.wallet.ui.components.referral

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun VsPromoBox(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = Theme.v2.colors.backgrounds.secondary,
                    shape = Theme.v2.radius.md,
                )
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.md,
                )
                .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiIcon(drawableResId = icon, size = 20.dp, tint = Theme.v2.colors.primary.accent4)

        UiSpacer(12.dp)

        Column {
            Text(
                text = title,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.supplementary.footnote,
            )

            Text(
                text = description,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.captionSmall,
            )
        }
    }
}

@Composable
fun VsPromoTag(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    // The tag runs off the start edge of the box, so only its end corners are drawn — and they are
    // fully round. Both the fill and the border read the one shape: two spellings of it here is the
    // drift this scale exists to stop.
    val shape =
        Theme.v2.radius.pill.shape.copy(topStart = CornerSize(0.dp), bottomStart = CornerSize(0.dp))
    Row(
        modifier =
            modifier
                .padding(start = 16.dp)
                .background(color = Theme.v2.colors.backgrounds.secondary, shape = shape)
                .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = shape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiIcon(drawableResId = icon, size = 14.dp, tint = Theme.v2.colors.primary.accent4)

        UiSpacer(8.dp)

        Text(
            text = text,
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.supplementary.caption,
        )
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview
private fun ReferralBoxes() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UiSpacer(60.dp)

        VsPromoTag(
            icon = R.drawable.ic_cup,
            text = stringResource(R.string.referral_create_info_title),
        )

        UiSpacer(32.dp)

        VsPromoBox(
            icon = R.drawable.ic_cup,
            title = stringResource(R.string.referral_create_code_title),
            description = stringResource(R.string.referral_create_code_description),
        )

        UiSpacer(32.dp)

        VsPromoBox(
            icon = R.drawable.ic_cup,
            title = stringResource(R.string.referral_share_title),
            description = stringResource(R.string.referral_share_description),
        )
    }
}
