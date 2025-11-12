package com.vultisig.wallet.ui.components.selectors

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun ChainSelector(
    chain: Chain,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
    ) {

        Image(
            painter = painterResource(chain.logo),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp),
        )

        UiSpacer(4.dp)

        Text(
            text = chain.raw,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.primary,
        )

        Image(
            painter = painterResource(R.drawable.ic_chevron_down_small),
            contentDescription = null,
            modifier = Modifier
                .size(16.dp),
        )
    }
}

@Composable
internal fun ChainSelector(
    title: String,
    chain: Chain,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.extraLight,
        )

        ChainSelector(
            chain = chain,
        )
    }
}