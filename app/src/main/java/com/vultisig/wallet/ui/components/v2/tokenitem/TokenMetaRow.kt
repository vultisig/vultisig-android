package com.vultisig.wallet.ui.components.v2.tokenitem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.theme.Theme

/**
 * A key/value row: a label pill on the left, a value pill on the right. Used throughout the token
 * detail screen for price/network/market-stats/token-info rows.
 */
@Composable
internal fun TokenMetaRow(
    key: String,
    value: String?,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        V2Container {
            Text(
                text = key,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(all = 4.dp),
            )
        }
        UiSpacer(weight = 1f)
        V2Container(type = ContainerType.TERTIARY) {
            LoadableValue(
                value = value,
                color = Theme.v2.colors.text.primary,
                style = Theme.satoshi.price.bodyS,
                isVisible = isVisible,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Same key-pill layout as [TokenMetaRow], but the value is a long identifier (e.g. a contract
 * address): middle-ellipsized to one line rather than wrapping, with a copy icon since it's meant
 * to be copied, not read.
 */
@Composable
internal fun TokenMetaAddressRow(key: String, address: String, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        V2Container {
            Text(
                text = key,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.body.s.medium,
                modifier = Modifier.padding(all = 4.dp),
            )
        }
        UiSpacer(weight = 1f)
        V2Container(type = ContainerType.TERTIARY) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = address,
                    color = Theme.v2.colors.text.primary,
                    style = Theme.satoshi.price.bodyS,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                UiSpacer(size = 6.dp)
                CopyIcon(textToCopy = address, size = 14.dp, tint = Theme.v2.colors.text.primary)
            }
        }
    }
}
