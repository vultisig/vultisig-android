package com.vultisig.wallet.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AppVersionText(
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(
            R.string.keysign_app_version_text,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        ),
        style = Theme.brockmann.button.medium.medium,
        color = Theme.v2.colors.text.tertiary,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}