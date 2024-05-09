package com.vultisig.wallet.presenter.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.base_components.MultiColorButton
import com.vultisig.wallet.presenter.common.TopBar
import com.vultisig.wallet.ui.theme.appColor
import com.vultisig.wallet.ui.theme.dimens
import com.vultisig.wallet.ui.theme.menloFamily
import com.vultisig.wallet.ui.theme.montserratFamily

@Composable
fun KeyGenErrorScreen(
    navController: NavController
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColor.oxfordBlue800)
    ) {

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.small2))

        TopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            centerText = stringResource(R.string.keygen),
            startIcon = null,
            navController = rememberNavController()
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(
                    start = 81.dp,
                    end = 81.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.danger),
                contentDescription = stringResource(R.string.danger_icon),
                alignment = Alignment.Center
            )
            Text(
                modifier = Modifier.padding(top = MaterialTheme.dimens.medium1),
                text = stringResource(R.string.signing_error_please_try_again),
                style = MaterialTheme.menloFamily.titleLarge,
                color = MaterialTheme.appColor.neutral0,
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
        ) {
            Text(
                modifier = Modifier
                    .padding(top = 150.dp)
                    .align(Alignment.CenterHorizontally),
                text = stringResource(R.string.bottom_warning_msg_keygen_error_screen),
                style = MaterialTheme.menloFamily.labelMedium,
                color = MaterialTheme.appColor.neutral0,
                textAlign = TextAlign.Center
            )

            MultiColorButton(
                text = stringResource(R.string.try_again),
                minHeight = MaterialTheme.dimens.minHeightButton,
                backgroundColor = MaterialTheme.appColor.turquoise800,
                textColor = MaterialTheme.appColor.oxfordBlue800,
                iconColor = MaterialTheme.appColor.turquoise800,
                textStyle = MaterialTheme.montserratFamily.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = MaterialTheme.dimens.small3,
                        start = MaterialTheme.dimens.small2,
                        end = MaterialTheme.dimens.small2,
                        bottom = MaterialTheme.dimens.small2
                    )
            ) {
                navController.navigate(route = " ")
            }
        }
    }
}


@Preview
@Composable
fun KeyGenErrorScreenPreview() {
    KeyGenErrorScreen(
        navController = rememberNavController()
    )
}
