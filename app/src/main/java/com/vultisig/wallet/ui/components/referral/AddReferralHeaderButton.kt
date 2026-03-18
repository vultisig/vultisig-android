package com.vultisig.wallet.ui.components.referral

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AddReferralHeaderButton(
    hasReferral: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(
                width = 1.dp,
                color = Theme.v2.colors.text.tertiary.copy(alpha = 0.3f),
                shape = shape,
            )
            .background(Theme.v2.colors.backgrounds.secondary)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (hasReferral) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Theme.v2.colors.alerts.success,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.referral_added),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.primary,
            )
        } else {
            Text(
                text = stringResource(R.string.add_referral),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.primary,
            )
        }
    }
}
