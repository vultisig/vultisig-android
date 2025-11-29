package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.AppVersionText
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.topbar.VsTopAppBar
import com.vultisig.wallet.ui.models.settings.CheckForUpdateUiModel
import com.vultisig.wallet.ui.models.settings.CheckForUpdateViewModel
import com.vultisig.wallet.ui.screens.transaction.shadeCircle
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun CheckForUpdateScreen() {

    val model = hiltViewModel<CheckForUpdateViewModel>()
    val state = model.state.collectAsState()

    CheckForUpdateScreen(
        model = state.value,
        onBackClick = model::back,
        onUpdateClick = model::update,
        onClickSecret = model::clickSecret
    )

}


@Composable
internal fun CheckForUpdateScreen(
    model: CheckForUpdateUiModel,
    onBackClick: () -> Unit = {},
    onUpdateClick: () -> Unit = {},
    onClickSecret: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            VsTopAppBar(
                title = stringResource(R.string.check_updates_title),
                iconLeft = com.vultisig.wallet.R.drawable.ic_caret_left,
                onIconLeftClick = onBackClick,
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
                .shadeCircle(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            Image(
                painter = painterResource(R.drawable.vultisig),
                contentDescription = "app logo",
                modifier = Modifier.size(72.dp),
            )

            UiSpacer(
                size = 40.dp
            )

            Text(
                text =
                    if (model.isUpdateAvailable)
                        stringResource(R.string.update_available)
                    else
                        stringResource(R.string.app_up_to_date),
                style = Theme.brockmann.button.medium.large,
                color = Theme.v2.colors.neutrals.n50
            )

            UiSpacer(
                size = 12.dp
            )

            AppVersionText(
                modifier = Modifier
                   .padding(top = 12.dp , bottom = 24.dp)
                     .clickable(onClick =onClickSecret)
            )



            if (model.isUpdateAvailable) {

                UiSpacer(
                    size = 30.dp
                )

                VsButton(
                    label = stringResource(R.string.update),
                    onClick = onUpdateClick,
                    variant = VsButtonVariant.Primary,
                    size = VsButtonSize.Medium,
                )
            }

        }
    }
}

internal class CheckForUpdatePreviewParameterProvider :
    PreviewParameterProvider<CheckForUpdateUiModel> {
    override val values: Sequence<CheckForUpdateUiModel>
        get() = sequenceOf(
            CheckForUpdateUiModel(
                isUpdateAvailable = true,
                currentVersion = "1.0.23"
            ),
            CheckForUpdateUiModel(
                isUpdateAvailable = false,
                currentVersion = "1.0.23"
            ),
        )
}


@Preview()
@Composable
private fun CheckForUpdateScreenPreview(
    @PreviewParameter(CheckForUpdatePreviewParameterProvider::class)
    model: CheckForUpdateUiModel
) {
    CheckForUpdateScreen(model)
}
