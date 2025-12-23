package com.vultisig.wallet.ui.components.fastselection.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.NetworkUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChainSelectorPickerItem(
    item: NetworkUiModel,
    distanceFromCenter: Int,
    modifier: Modifier = Modifier,
) {
    PopupPickerItem(
        distanceFromCenter = distanceFromCenter,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .width(FAST_SELECTION_MODAL_WIDTH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {

            TokenLogo(
                errorLogoModifier = Modifier
                    .size(40.dp)
                    .background(Theme.colors.neutrals.n100),
                logo = item.logo,
                title = item.title,
                modifier = Modifier
                    .size(40.dp)
            )


            UiSpacer(
                size = 10.dp
            )

            Text(
                text = item.chain.raw,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.m.medium
            )
        }
    }
}