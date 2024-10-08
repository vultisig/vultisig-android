package com.vultisig.wallet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun SigningError(navController: NavHostController) {
    val textColor = Theme.colors.neutral0
    Column(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = 12.dp,
                horizontal = 8.dp
            )
    ) {
        TopBar(
            centerText = stringResource(R.string.signing_error_keygen),
            navController = navController
        )

        Spacer(modifier = Modifier.weight(1.0f))
        Image(
            painterResource(id = R.drawable.danger),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = stringResource(R.string.signing_error_signing_error_please_try_again),
            color = textColor,
            style = Theme.menlo.body2.copy(
                textAlign = TextAlign.Center, lineHeight = 25.sp
            ),
            modifier = Modifier.padding(horizontal = 65.dp),

            )

        Spacer(modifier = Modifier.weight(1.0f))

        InformationNote(
            modifier = Modifier.padding(horizontal = 8.dp),
            text = stringResource(R.string.keep_devices_on_the_same_wifi_network),
        )

        Spacer(modifier = Modifier.height(8.dp))
        MultiColorButton(
            text = stringResource(R.string.signing_error_try_again),
            backgroundColor = Theme.colors.turquoise600Main,
            textColor = Theme.colors.oxfordBlue600Main,
            minHeight = 45.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 16.dp,
                    horizontal = 16.dp,
                ),
            onClick = {
            }
        ) {

        }

    }
}

@Preview(showBackground = true)
@Composable
fun SigningErrorPreview() {
    val navController = rememberNavController()
    SigningError(navController)

}