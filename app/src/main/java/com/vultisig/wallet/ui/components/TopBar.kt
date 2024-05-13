package com.vultisig.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.ui.theme.montserratFamily

@Composable
internal fun TopBar(
    navController: NavController,
    centerText: String,
    modifier: Modifier = Modifier,
    @DrawableRes startIcon: Int? = null,
    @DrawableRes endIcon: Int? = null,
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(all = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = CenterVertically
    ) {
        val iconModifier = Modifier
            .size(24.dp)

        startIcon?.let { id ->
            Image(
                painter = painterResource(id = id),
                contentDescription = null,
                modifier = iconModifier
                    .clickable { navController.popBackStack() },
            )
        } ?: Spacer(modifier = iconModifier)

        Text(
            text = centerText,
            color = textColor,
            style = MaterialTheme.montserratFamily.heading5,
        )

        endIcon?.let { id ->
            Image(
                painter = painterResource(id = id),
                contentDescription = null,
                modifier = iconModifier,
            )
        } ?: Spacer(modifier = iconModifier)
    }
}

@Preview
@Composable
private fun TopBarPreview() {
    TopBar(
        navController = rememberNavController(),
        centerText = "Title"
    )
}