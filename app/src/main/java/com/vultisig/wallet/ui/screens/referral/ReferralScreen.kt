package com.vultisig.wallet.ui.screens.referral

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun ReferralScreen(
    navController: NavController,
) {
    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.referral_screen_title),
                onBackClick = {

                },
            )
        },
        content = { contentPadding ->
            ReferralContent(contentPadding)
        },
    )
}

@Composable
internal fun ReferralContent(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp),
            contentAlignment = Alignment.Center
        ) {

            Image(
                painter = painterResource(id = R.drawable.crypto_natives),
                contentDescription = "ReferralImage",
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        UiSpacer(16.dp)

        // TODO: This can be shown or hide
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(
                    color = Theme.colors.text.primary,
                    fontWeight = FontWeight.Medium,
                )) {
                    append("Save ")
                }
                withStyle(style = SpanStyle(
                    color = Theme.colors.primary.accent4,
                    fontWeight = FontWeight.Medium,
                )) {
                    append("10%")
                }
                withStyle(style = SpanStyle(
                    color = Theme.colors.text.primary,
                    fontWeight = FontWeight.Medium,
                )) {
                    append(" on swaps - Add a Referral")
                }
            },
            color = Theme.colors.text.primary,

            /*
            medium = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontFamily = fontFamily,
                            lineHeightStyle = lineHeightStyle,
                        )
             */
        )
    }
}