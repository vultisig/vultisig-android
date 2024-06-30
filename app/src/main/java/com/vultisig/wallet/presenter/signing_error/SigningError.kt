package com.vultisig.wallet.presenter.signing_error

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiScrollableColumn
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
fun SigningError(navController: NavHostController) {
    val textColor = Theme.colors.neutral0
    UiScrollableColumn(
        horizontalAlignment = CenterHorizontally,
        modifier = Modifier
            .background(Theme.colors.oxfordBlue800)
            .padding(
                vertical = MaterialTheme.dimens.marginMedium,
                horizontal = MaterialTheme.dimens.marginSmall
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
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.medium1))
        Text(
            text = stringResource(R.string.signing_error_signing_error_please_try_again),
            color = textColor,
            style = Theme.menlo.body2.copy(
                textAlign = TextAlign.Center, lineHeight = 25.sp
            ),
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.large),

            )

        Spacer(modifier = Modifier.weight(1.0f))

        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.dimens.small1),
            text = stringResource(R.string.keep_devices_on_the_same_wifi_network),
            color = textColor,
            style = Theme.menlo.heading5.copy(
                textAlign = TextAlign.Center, fontSize = 13.sp
            ),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small1))
        MultiColorButton(
            text = stringResource(R.string.signing_error_try_again),
            backgroundColor = Theme.colors.turquoise600Main,
            textColor = Theme.colors.oxfordBlue600Main,
            minHeight = MaterialTheme.dimens.minHeightButton,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.dimens.marginMedium,
                    end = MaterialTheme.dimens.marginMedium,
                    bottom = MaterialTheme.dimens.buttonMargin,
                )
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