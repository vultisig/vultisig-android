package com.vultisig.wallet.ui.screens.send

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
import com.vultisig.wallet.ui.components.v2.fastselection.components.PopupPickerItem
import com.vultisig.wallet.ui.screens.select.AssetUiModel
import com.vultisig.wallet.ui.screens.select.NetworkUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ChainSelectorPickerItem(
    item: NetworkUiModel,
    distanceFromCenter: Int,
    modifier: Modifier = Modifier.Companion,
) {
    PopupPickerItem(
        item = item,
        distanceFromCenter = distanceFromCenter,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.Companion
                .width(220.dp),
            verticalAlignment = Alignment.Companion.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {

            TokenLogo(
                errorLogoModifier = Modifier.Companion
                    .size(40.dp)
                    .background(Theme.colors.neutral100),
                logo = item.logo,
                title = item.title,
                modifier = Modifier.Companion
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

@Composable
internal fun AssetSelectorPickerItem(
    item: AssetUiModel,
    distanceFromCenter: Int,
    modifier: Modifier = Modifier.Companion,
) {
    PopupPickerItem(
        item = item,
        distanceFromCenter = distanceFromCenter,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.Companion,
            verticalAlignment = Alignment.Companion.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {

            TokenLogo(
                errorLogoModifier = Modifier.Companion
                    .size(40.dp)
                    .background(Theme.colors.neutral100),
                logo = item.logo,
                title = item.title,
                modifier = Modifier.Companion
                    .size(40.dp)
            )


            UiSpacer(
                size = 10.dp
            )

            Text(
                text = item.title,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.m.medium
            )

            Text(
                text = item.value + " " + item.amount,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.m.medium
            )
        }
    }
}