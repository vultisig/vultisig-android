package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun CopiableAddress(
    modifier: Modifier = Modifier,
    address: String,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = address,
            modifier = Modifier.widthIn(max = 64.dp),
            overflow = TextOverflow.MiddleEllipsis,
            maxLines = 1,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.extraLight,
        )

        UiSpacer(
            size = 4.dp
        )

        CopyIcon(
            textToCopy = address,
            size = 12.dp,
            tint = Theme.colors.text.extraLight
        )
    }
}

@Preview
@Composable
private fun PreviewCopiableAddress() {
    CopiableAddress(
        address = "0x123456543321123456543322111122343"
    )
}