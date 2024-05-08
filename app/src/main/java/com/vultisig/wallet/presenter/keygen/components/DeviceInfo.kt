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
import com.vultisig.wallet.app.ui.theme.appColor
import com.vultisig.wallet.app.ui.theme.dimens
import com.vultisig.wallet.app.ui.theme.montserratFamily


@Composable
fun DeviceInfo(
    @DrawableRes icon: Int,
    name: String,
    isSelected: Boolean,
    onItemSelected: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .height(120.dp)
            .clip(shape = RoundedCornerShape(size = MaterialTheme.dimens.small1))
            .background(MaterialTheme.appColor.oxfordBlue600Main)
            .clickable {
                onItemSelected(!isSelected)
            },
        horizontalAlignment = CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .height(MaterialTheme.dimens.medium1)
                    .width(MaterialTheme.dimens.medium1)
            ) {
                Checkbox(
                    modifier = Modifier.clip(shape = CircleShape),
                    checked = isSelected,
                    onCheckedChange = { isChecked ->
                        onItemSelected(isChecked)
                    },
                    colors = CheckboxDefaults.colors(
                        uncheckedColor = MaterialTheme.appColor.oxfordBlue600Main,
                        checkedColor = MaterialTheme.appColor.neutral0,
                        checkmarkColor = MaterialTheme.appColor.oxfordBlue600Main
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
                .padding(top = MaterialTheme.dimens.small2)
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))

        Text(
            modifier = Modifier.align(CenterHorizontally),
            text = name,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.appColor.neutral0,
            style = MaterialTheme.montserratFamily.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceInfoPreview() {
    DeviceInfo(R.drawable.ipad, "iPad", true, onItemSelected = { })
}