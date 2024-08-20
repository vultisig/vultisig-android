package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.clickOnce
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.Theme.colors

@Composable
internal fun PercentText(
    percent: Int,
    onPercentClick: (percent: Int) -> Unit,
) {
    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.oxfordBlue600Main)
            .padding(
                horizontal = 19.dp,
                vertical = 5.dp
            )
            .clickOnce { onPercentClick(percent) },
        text = stringResource(
            R.string.send_percent,
            percent
        ),
        color = colors.neutral100,
        style = Theme.menlo.overline2,
    )
}