package com.vultisig.wallet.presenter.keygen.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun DeviceInfo(
    @DrawableRes icon: Int,
    name: String,
    isSelected: Boolean,
    onItemSelected: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = Theme.colors.neutral0,
                        shape = RoundedCornerShape(size = 8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .background(
                color = Theme.colors.oxfordBlue600Main,
                shape = RoundedCornerShape(size = 8.dp)
            )
            .width(92.dp)
            .height(122.dp)
            .clickable {
                onItemSelected(!isSelected)
            },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(12.dp)
                    .background(
                        color = Theme.colors.neutral0,
                        shape = CircleShape
                    )
                    .align(Alignment.TopEnd)
            )
        }


        Column(
            modifier = Modifier
                .padding(
                    vertical = 10.dp,
                    horizontal = 16.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.height(56.dp)
            )

            UiSpacer(size = 6.dp)

            Text(
                text = name,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                color = Theme.colors.neutral0,
                style = Theme.montserrat.subtitle1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceInfoPreview() {
    DeviceInfo(R.drawable.ipad, "iPad", true, onItemSelected = { })
}
