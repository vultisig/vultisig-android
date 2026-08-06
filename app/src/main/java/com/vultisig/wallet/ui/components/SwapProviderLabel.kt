package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.getProviderLogo
import com.vultisig.wallet.ui.screens.transaction.components.TokenCircle
import com.vultisig.wallet.ui.theme.Theme

/**
 * A swap provider's logo followed by its name — the shape Figma specifies for every surface that
 * names a provider (swap form, verify, transaction done, history card, detail sheet), so the same
 * swap reads identically wherever it appears.
 *
 * The 16dp logo matches the caption/body line height the callers use, keeping the row the same
 * height as a plain-text value. Providers [getProviderLogo] has no drawable for degrade to the bare
 * name.
 *
 * @param logo pre-resolved logo for callers whose UI model already carries one; resolved from
 *   [provider] when absent.
 */
@Composable
internal fun SwapProviderLabel(
    provider: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Theme.brockmann.body.s.medium,
    color: Color = Theme.v2.colors.text.primary,
    logo: ImageModel? = null,
) {
    val providerLogo = logo ?: remember(provider) { getProviderLogo(provider) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (providerLogo != null) {
            TokenCircle(logo = providerLogo, ticker = provider, size = 16)
        }
        Text(text = provider, style = style, color = color, maxLines = 1)
    }
}
