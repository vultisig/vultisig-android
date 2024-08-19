package com.vultisig.wallet.presenter.keygen.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme


@Composable
fun DeviceInfo(
    @DrawableRes icon: Int,
    name: String,
    isSelected: Boolean,
    onItemSelected: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(122.dp)
            .height(165.dp)
            .clip(shape = RoundedCornerShape(size = 7.dp))
            .background(Theme.colors.oxfordBlue600Main)
            .clickable {
                onItemSelected(!isSelected)
            },
        horizontalAlignment = CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .height(25.dp)
                    .width(25.dp)
            ) {
                Checkbox(
                    modifier = Modifier.clip(shape = CircleShape),
                    checked = isSelected,
                    onCheckedChange = { isChecked ->
                        onItemSelected(isChecked)
                    },
                    colors = CheckboxDefaults.colors(
                        uncheckedColor = Theme.colors.oxfordBlue600Main,
                        checkedColor = Theme.colors.neutral0,
                        checkmarkColor = Theme.colors.oxfordBlue600Main
                    )
                )
            }
        }

        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier
                .height(50.dp)
                .width(50.dp)
                .padding(top = 10.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            modifier = Modifier.align(CenterHorizontally),
            text = name,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            color = Theme.colors.neutral0,
            style = Theme.montserrat.subtitle2,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceInfoPreview() {
    DeviceInfo(R.drawable.ipad, "iPad", true, onItemSelected = { })
}
