package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

/**
 * The Vultisig logo over its wordmark, as the vault creation screen shows it. The launch splash
 * draws the same mark, so the first frame of the app and the screen it opens onto match.
 */
@Composable
internal fun VultisigBrandMark(modifier: Modifier = Modifier, logoScale: Float = 1f) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "vultisig",
            modifier = Modifier.width(74.5.dp).scale(logoScale),
        )
        UiSpacer(12.dp)
        Text(
            text = stringResource(R.string.create_new_vault_screen_vultisig),
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.headings.title1,
        )
    }
}
