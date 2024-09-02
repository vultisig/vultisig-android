package com.vultisig.wallet.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.BuildConfig
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun AppVersionText(
    modifier: Modifier = Modifier.padding(12.dp)
) {
    Text(
        modifier = modifier,
        text = stringResource(
            R.string.vultisig_app_version,
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        ),
        style = Theme.menlo.body2,
        color = Theme.colors.turquoise600Main,
    )
}