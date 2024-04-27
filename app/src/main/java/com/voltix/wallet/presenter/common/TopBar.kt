package com.voltix.wallet.presenter.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.voltix.wallet.app.ui.theme.dimens
import com.voltix.wallet.app.ui.theme.montserratFamily

@Composable
fun TopBar(
    navController: NavController,
    centerText: String,
    modifier: Modifier = Modifier,
    @DrawableRes startIcon: Int? = null,
    @DrawableRes endIcon: Int? = null,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = CenterVertically
    ) {
        startIcon?.let { id ->
            Image(painter = painterResource(id = id), contentDescription = null,modifier = Modifier.clickable {
                navController.popBackStack()
            })
        } ?: Spacer(modifier = Modifier)
        Text(
            text = centerText,
            color = textColor,
            style = MaterialTheme.montserratFamily.headlineMedium.copy(fontSize = MaterialTheme.dimens.medium1.value.sp)
        )
        endIcon?.let { id ->
            Image(painter = painterResource(id = id), contentDescription = null)
        } ?: Spacer(modifier = Modifier)
    }
}
