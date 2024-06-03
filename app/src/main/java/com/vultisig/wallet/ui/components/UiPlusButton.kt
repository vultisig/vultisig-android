package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.clickOnce
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.montserratFamily

@Preview
@Composable
private fun UiPlusButtonPreview() {
    UiPlusButton(
        title = "Choose Chains",
        onClick = {}
    )
}

@Composable
internal fun UiPlusButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickOnce(enabled = true,onClick = onClick  )
    ) {
        Image(
            painter = painterResource(R.drawable.plus),
            colorFilter = ColorFilter.tint(MaterialTheme.appColor.turquoise600Main),
            modifier = Modifier.size(20.dp),
            contentDescription = stringResource(R.string.plus_icon),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = title,
            color = MaterialTheme.appColor.turquoise600Main,
            style = MaterialTheme.montserratFamily.titleLarge,
        )
    }
}