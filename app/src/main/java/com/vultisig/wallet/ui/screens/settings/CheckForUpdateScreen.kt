package com.vultisig.wallet.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.AppVersionText
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.settingsShade
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.settings.CheckForUpdateUiModel
import com.vultisig.wallet.ui.models.settings.CheckForUpdateViewModel
import com.vultisig.wallet.ui.theme.Theme

private val LOGO_TILE_GRADIENT_END = Color(0xFF0D39B1)

@Composable
internal fun CheckForUpdateScreen() {

    val model = hiltViewModel<CheckForUpdateViewModel>()
    val state = model.state.collectAsState()

    CheckForUpdateScreen(
        model = state.value,
        onBackClick = model::back,
        onUpdateClick = model::update,
        onClickSecret = model::clickSecret,
    )
}

@Composable
internal fun CheckForUpdateScreen(
    model: CheckForUpdateUiModel,
    onBackClick: () -> Unit = {},
    onUpdateClick: () -> Unit = {},
    onClickSecret: () -> Unit = {},
) {
    V2Scaffold(onBackClick = onBackClick, title = stringResource(R.string.check_updates_title)) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .settingsShade(centerOffsetY = SHADE_CENTER_OFFSET)
                    .padding(bottom = CONTENT_BOTTOM_OFFSET),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppLogoTile()

            UiSpacer(size = LOGO_TO_TITLE_GAP)

            Text(
                text =
                    if (model.isUpdateAvailable) stringResource(R.string.update_available)
                    else stringResource(R.string.app_up_to_date),
                style = Theme.brockmann.button.medium.regular,
                color = Theme.v2.colors.neutrals.n50,
            )

            UiSpacer(size = 12.dp)

            AppVersionText(modifier = Modifier.clickable(onClick = onClickSecret))

            UiSpacer(size = VERSION_TO_BUTTON_GAP)

            // The logo doesn't move between states, so the button's space stays reserved.
            if (model.isUpdateAvailable) {
                VsButton(
                    label = stringResource(R.string.update),
                    onClick = onUpdateClick,
                    variant = VsButtonVariant.Primary,
                    size = VsButtonSize.Wide,
                )
            } else {
                UiSpacer(size = BUTTON_HEIGHT)
            }
        }
    }
}

@Composable
private fun AppLogoTile() {
    Box(
        modifier =
            Modifier.size(LOGO_TILE_SIZE)
                .background(
                    brush =
                        Brush.verticalGradient(
                            listOf(Theme.v2.colors.primary.accent4, LOGO_TILE_GRADIENT_END)
                        ),
                    shape = LOGO_TILE_SHAPE,
                )
                .innerShadow(
                    LOGO_TILE_SHAPE,
                    Shadow(
                        radius = LOGO_TILE_HIGHLIGHT_SIZE,
                        color = Theme.v2.colors.neutrals.n50,
                        alpha = LOGO_TILE_HIGHLIGHT_ALPHA,
                        offset = DpOffset(x = 0.dp, y = LOGO_TILE_HIGHLIGHT_SIZE),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.vultisig_logo),
            contentDescription = "app logo",
            modifier = Modifier.size(LOGO_SIZE),
        )
    }
}

private val CONTENT_BOTTOM_OFFSET = 91.dp
private val SHADE_CENTER_OFFSET = (-64).dp
private val LOGO_TO_TITLE_GAP = 27.5.dp
private val VERSION_TO_BUTTON_GAP = 30.dp
private val BUTTON_HEIGHT = 46.dp
private val LOGO_TILE_SIZE = 74.5.dp
private val LOGO_TILE_SHAPE = RoundedCornerShape(23.84.dp)
private val LOGO_TILE_HIGHLIGHT_SIZE = 1.49.dp
private const val LOGO_TILE_HIGHLIGHT_ALPHA = 0.35f
private val LOGO_SIZE = 41.72.dp

internal class CheckForUpdatePreviewParameterProvider :
    PreviewParameterProvider<CheckForUpdateUiModel> {
    override val values: Sequence<CheckForUpdateUiModel>
        get() =
            sequenceOf(
                CheckForUpdateUiModel(isUpdateAvailable = true, currentVersion = "1.0.23"),
                CheckForUpdateUiModel(isUpdateAvailable = false, currentVersion = "1.0.23"),
            )
}

@Preview
@Composable
private fun CheckForUpdateScreenPreview(
    @PreviewParameter(CheckForUpdatePreviewParameterProvider::class) model: CheckForUpdateUiModel
) {
    CheckForUpdateScreen(model)
}
