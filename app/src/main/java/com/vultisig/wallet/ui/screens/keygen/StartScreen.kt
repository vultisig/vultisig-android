@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.keygen.StartViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.startScreenAnimations

private const val IS_IMPORT_SEEDPHRASE_ENABLED = true

@Composable
internal fun StartScreen(
    model: StartViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    StartScreen(
        hasBackButton = state.hasBackButton,
        onBackClick = model::back,
        onCreateNewVaultClick = model::navigateToCreateVault,
        onScanQrCodeClick = model::navigateToScanQrCode,
        onImportVaultClick = model::navigateToImportVault,
        onImportSeedphraseClick = model::navigateToImportSeedphrase,
        isImportSeedphraseEnabled = IS_IMPORT_SEEDPHRASE_ENABLED,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartScreen(
    hasBackButton: Boolean,
    onCreateNewVaultClick: () -> Unit,
    onScanQrCodeClick: () -> Unit,
    onImportVaultClick: () -> Unit,
    onImportSeedphraseClick: () -> Unit,
    onBackClick: () -> Unit,
    isImportSeedphraseEnabled: Boolean = false,
) {
    val logoScale = remember {
        Animatable(0f)
    }

    var isChooseImportTypeBottomSheetVisible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        logoScale.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = 200,
                delayMillis = 200
            ),
        )
    }

    V3Scaffold(
        onBackClick = if (hasBackButton) onBackClick else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "vultisig",
                    modifier = Modifier
                        .width(60.dp)
                        .scale(logoScale.value)
                )
                UiSpacer(16.dp)
                Text(
                    text = stringResource(R.string.create_new_vault_screen_vultisig),
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.headings.title1
                )
            }

            Column(
                horizontalAlignment = CenterHorizontally,
                ) {
                Row {

                    VsButton(
                        label = stringResource(R.string.home_screen_scan_qr_code),
                        variant = VsButtonVariant.Secondary,
                        onClick = onScanQrCodeClick,
                        modifier = Modifier
                            .weight(1f)
                            .startScreenAnimations(
                                delay = 350,
                            ),
                    )

                    UiSpacer(
                        size = 8.dp
                    )

                    VsButton(
                        label = stringResource(R.string.home_screen_import_vault),
                        variant = VsButtonVariant.Secondary,
                        onClick = {
                            isChooseImportTypeBottomSheetVisible = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .startScreenAnimations(
                                delay = 450,
                            ),
                    )
                }

                UiSpacer(
                    size = 16.dp
                )


                VsButton(
                    label = stringResource(R.string.create_new_vault_screen_create_new_vault),
                    modifier = Modifier
                        .startScreenAnimations(
                            delay = 100,
                        )
                        .fillMaxWidth()
                        .testTag("StartScreen.createNewVault"),
                    onClick = onCreateNewVaultClick
                )
            }
        }

        if (isChooseImportTypeBottomSheetVisible) {
            V2BottomSheet(onDismissRequest = {
                isChooseImportTypeBottomSheetVisible = false
            }) {
                ChooseImportTypeBottomSheetContent(
                    isImportSeedphraseEnabled = isImportSeedphraseEnabled,
                    onImportSeedphraseClick = {
                        isChooseImportTypeBottomSheetVisible = false
                        onImportSeedphraseClick()
                    },
                    onImportVaultClick = {
                        isChooseImportTypeBottomSheetVisible = false
                        onImportVaultClick()
                    },
                )
            }
        }

    }
}

@Composable
private fun ChooseImportTypeBottomSheetContent(
    isImportSeedphraseEnabled: Boolean,
    onImportSeedphraseClick: () -> Unit,
    onImportVaultClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
    ) {
        UiSpacer(
            size = 24.dp
        )
        Text(
            text = stringResource(R.string.start_screen_recover_or_convert),
            color = Theme.v2.colors.neutrals.n50,
            style = Theme.brockmann.headings.title3,
            textAlign = TextAlign.Center
        )

        UiSpacer(
            size = 20.dp
        )

        if (isImportSeedphraseEnabled) {
            ChooseImportTypeButton(
                isNew = true,
                logo = R.drawable.logo,
                title = stringResource(R.string.start_screen_import_seedphrase),
                description = stringResource(R.string.start_screen_import_seedphrase_desc),
                subDescription = null,
                onClick = onImportSeedphraseClick,
            )
            UiSpacer(
                size = 14.dp
            )
        }
        ChooseImportTypeButton(
            isNew = false,
            logo = R.drawable.wallet,
            title = stringResource(R.string.start_screen_import_vault_share),
            description = stringResource(R.string.start_screen_import_vault_share_desc),
            subDescription = stringResource(R.string.start_screen_import_vault_share_file_types),
            onClick = onImportVaultClick,
        )
    }
}


@Composable
private fun ChooseImportTypeButton(
    onClick: () -> Unit,
    isNew: Boolean,
    logo: Int,
    title: String,
    description: String,
    subDescription: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                shape = RoundedCornerShape(size = 16.dp)
            )
            .clickable(
                onClick = onClick,
            )
            .background(
                color = Theme.v2.colors.backgrounds.tertiary
            )
            .padding(
                all = 24.dp
            )

    ) {
        if (isNew) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiIcon(
                    drawableResId = R.drawable.energy,
                    size = 12.dp,
                    tint = Theme.v2.colors.alerts.warning
                )
                UiSpacer(
                    size = 4.dp
                )
                Text(
                    text = stringResource(R.string.start_screen_new),
                    color = Theme.v2.colors.alerts.warning,
                    style = Theme.brockmann.supplementary.caption
                )
            }
            UiSpacer(
                size = 12.dp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = logo,
                size = 20.dp,
                tint = Theme.v2.colors.alerts.info
            )
            UiSpacer(
                size = 8.dp
            )
            Text(
                text = title,
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.subtitle
            )
        }
        UiSpacer(
            size = 12.dp
        )

        Text(
            text = description,
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.headings.subtitle
        )

        UiSpacer(
            size = 12.dp
        )

        if (subDescription != null) {
            Text(
                text = subDescription,
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.caption
            )
        }

    }
}


@Composable
@Preview
private fun ChooseImportTypeBottomSheetContentPreview() {
    ChooseImportTypeBottomSheetContent(
        isImportSeedphraseEnabled = true,
        onImportVaultClick = {},
        onImportSeedphraseClick = {}
    )
}


@Preview
@Composable
private fun StartScreenPreview() {
    StartScreen(
        hasBackButton = true,
        onCreateNewVaultClick = {},
        onScanQrCodeClick = {},
        onImportVaultClick = {},
        onImportSeedphraseClick = {},
        onBackClick = {},
    )
}