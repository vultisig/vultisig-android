package com.vultisig.wallet.ui.components.dapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.payload.DAppMetadata
import com.vultisig.wallet.ui.theme.Theme

/**
 * Informational card showing which dApp produced a keysign request. Rendered above the transaction
 * hero on verify and done screens; trust decisions stay with Blockaid and the independently-decoded
 * calldata, so this only echoes the metadata the dApp self-declared.
 *
 * Layout mirrors the iOS `DAppRequestBanner`: a "Request from" header followed by a 32dp circular
 * icon and a name/host stack. Empty fields are treated as absent so partially-populated metadata
 * still renders cleanly.
 */
@Composable
internal fun DappRequestBanner(metadata: DAppMetadata, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.request_from)
    // Merge the three texts into one TalkBack announcement: "Request from Uniswap, app.uniswap.org"
    // instead of three separate nodes.
    val announcement = buildString {
        append(label)
        if (metadata.name.isNotEmpty()) append(' ').append(metadata.name)
        if (metadata.host.isNotEmpty()) append(", ").append(metadata.host)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = Theme.v2.colors.backgrounds.surface2,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(20.dp)
                .semantics(mergeDescendants = true) { contentDescription = announcement },
    ) {
        Text(
            text = label,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DappIcon(safeIconUrl = metadata.safeIconUrl)
            DappInfo(name = metadata.name, host = metadata.host, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DappIcon(safeIconUrl: String?) {
    val placeholder: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.size(ICON_SIZE)
                    .background(Theme.v2.colors.backgrounds.surface1, CircleShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.settings_globe),
                contentDescription = null,
                tint = Theme.v2.colors.text.tertiary,
                modifier = Modifier.size(PLACEHOLDER_GLYPH_SIZE),
            )
        }
    }

    if (safeIconUrl == null) {
        placeholder()
        return
    }

    SubcomposeAsyncImage(
        model = safeIconUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(ICON_SIZE).clip(CircleShape),
        loading = { placeholder() },
        error = { placeholder() },
    )
}

@Composable
private fun DappInfo(name: String, host: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (name.isNotEmpty()) {
            Text(
                text = name,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (host.isNotEmpty()) {
            // When name is absent the host carries the identity on its own — bump up to the same
            // body-m size so it doesn't look like an afterthought.
            Text(
                text = host,
                style =
                    if (name.isEmpty()) Theme.brockmann.body.m.medium
                    else Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
    }
}

private val ICON_SIZE = 32.dp
private val PLACEHOLDER_GLYPH_SIZE = 20.dp

@Preview
@Composable
private fun PreviewDappRequestBannerFullMetadata() {
    DappRequestBanner(
        metadata =
            DAppMetadata(
                name = "Cross-chain swaps across 13+ networks | 1inch",
                url = "https://1inch.io/",
                iconUrl = "https://1inch.io/favicon.ico",
            )
    )
}

@Preview
@Composable
private fun PreviewDappRequestBannerNameOnly() {
    DappRequestBanner(metadata = DAppMetadata(name = "Uniswap", url = "", iconUrl = ""))
}

@Preview
@Composable
private fun PreviewDappRequestBannerHostFallback() {
    DappRequestBanner(
        metadata = DAppMetadata(name = "", url = "https://app.uniswap.org/swap", iconUrl = "")
    )
}
