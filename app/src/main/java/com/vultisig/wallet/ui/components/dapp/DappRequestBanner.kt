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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 *
 * **Privacy stance — Android intentionally does NOT fetch the dApp-supplied `iconUrl`.** Loading
 * the favicon would expose the signing device IP and TLS fingerprint to a host the user has not yet
 * consented to interact with, and feed a default Coil pipeline an attacker-controlled URL with no
 * size cap or timeout (decoder-CVE surface). iOS and Windows currently load the icon — Android
 * leads on this and ships a placeholder glyph instead. The name and host already identify the dApp;
 * the icon was visual polish, not load-bearing.
 */
@Composable
internal fun DappRequestBanner(metadata: DAppMetadata, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.request_from)
    // Merge the three texts into one TalkBack announcement: "Request from Uniswap, app.uniswap.org"
    // instead of three separate nodes.
    val announcement = buildString {
        append(label)
        if (metadata.name.isNotEmpty()) append(' ').append(metadata.name)
        if (metadata.host.isNotEmpty()) {
            // Use a comma to separate name from host, but a plain space when name is absent so
            // TalkBack reads "Request from app.uniswap.org" instead of "Request from, app…".
            append(if (metadata.name.isNotEmpty()) ", " else " ").append(metadata.host)
        }
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
            DappIcon()
            DappInfo(name = metadata.name, host = metadata.host, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DappIcon() {
    // Intentionally no `iconUrl` parameter. See class kdoc — Android does not fetch dApp-supplied
    // icons so a hostile origin can't fingerprint the signing device the moment verify renders.
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(ICON_SIZE).background(Theme.v2.colors.backgrounds.surface1, CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.settings_globe),
            contentDescription = null,
            tint = Theme.v2.colors.text.tertiary,
            modifier = Modifier.size(PLACEHOLDER_GLYPH_SIZE),
        )
    }
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
