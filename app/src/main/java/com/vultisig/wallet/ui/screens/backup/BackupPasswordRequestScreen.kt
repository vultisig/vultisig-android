package com.vultisig.wallet.ui.screens.backup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.components.v2.texts.highlightedText
import com.vultisig.wallet.ui.navigation.BackupType
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.v2.Radius
import com.vultisig.wallet.ui.theme.v2.V2
import com.vultisig.wallet.ui.utils.RequestWriteFilePermissionOnceIfNotGranted
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.file.RequestCreateDocument

@Composable
internal fun BackupPasswordRequestScreen(model: BackupPasswordRequestViewModel = hiltViewModel()) {
    val state by model.state.collectAsState()

    RequestWriteFilePermissionOnceIfNotGranted(
        onRequestPermissionResult = model::handleWriteFilePermissionStatus
    )

    RequestCreateDocument(
        mimeType = state.mimeType.value,
        onDocumentCreated = model::saveVaultIntoUri,
        createDocumentRequestFlow = model.createDocumentRequestFlow,
    )

    val error = state.error
    if (error != null) {
        UiAlertDialog(
            title = stringResource(R.string.dialog_default_error_title),
            text = error.asString(),
            confirmTitle = stringResource(R.string.try_again),
            onDismiss = model::dismissError,
        )
    }

    BackupPasswordRequestScreen(
        onNoPasswordClick = model::backupWithoutPassword,
        onWithPasswordClick = model::backupWithPassword,
        onBackClick = model::back,
        isSettingsLook = isBackupOpenedOutsideKeygen<Route.BackupPasswordRequest> { it.backupType },
    )
}

/** Only keygen passes a vault type here, and it keeps the look it always had. */
@Composable
internal inline fun <reified T : Any> isBackupOpenedOutsideKeygen(
    crossinline backupType: (T) -> BackupType
): Boolean {
    val entry = LocalViewModelStoreOwner.current as? NavBackStackEntry ?: return false
    return remember(entry) {
        when (val type = backupType(entry.toRoute<T>())) {
            BackupType.AllVaults -> true
            is BackupType.CurrentVault -> type.vaultType == null
        }
    }
}

@Preview
@Composable
private fun BackupPasswordRequestScreenPreview() {
    BackupPasswordRequestScreen(onNoPasswordClick = {}, onWithPasswordClick = {}, onBackClick = {})
}

internal object BackupPasswordRequestScreenTags {
    const val BACKUP_WITHOUT_PASSWORD = "BackupPasswordRequestScreen.withoutPassword"
}

private enum class BackupRequestLook(
    val topGap: Dp,
    val bottomGap: Dp,
    @DrawableRes val headerIcon: Int,
    val headerTint: Color,
    @DrawableRes val secureIcon: Int,
    @DrawableRes val encryptIcon: Int,
    @DrawableRes val cannotResetIcon: Int,
    @StringRes val cannotResetText: Int,
    @StringRes val withPasswordAction: Int,
    val cautionRadius: Radius,
    val cautionSideInset: Dp,
    val cautionIconSize: Dp,
    val cautionIconGap: Dp,
) {
    Keygen(
        topGap = 14.dp,
        bottomGap = 0.dp,
        headerIcon = R.drawable.ic_passkeys,
        headerTint = V2.colors.neutrals.n100,
        secureIcon = R.drawable.remember,
        encryptIcon = R.drawable.backup_passowrd_lock,
        cannotResetIcon = R.drawable.encrypt,
        cannotResetText = R.string.backup_password_request_caution_cannot_reset_desc,
        withPasswordAction = R.string.backup_password_request_with_password_action,
        cautionRadius = V2.radius.xl,
        cautionSideInset = 0.dp,
        cautionIconSize = 20.dp,
        cautionIconGap = 16.dp,
    ),
    Settings(
        topGap = 0.dp,
        bottomGap = 10.dp,
        headerIcon = R.drawable.ic_lock_password,
        headerTint = V2.colors.primary.accent4,
        secureIcon = R.drawable.lock_filled,
        encryptIcon = R.drawable.ic_folders,
        cannotResetIcon = R.drawable.ic_triangle_warning_filled,
        cannotResetText = R.string.backup_password_request_settings_cannot_reset,
        withPasswordAction = R.string.backup_password_request_settings_with_password,
        cautionRadius = V2.radius.lg,
        cautionSideInset = 17.dp,
        cautionIconSize = 24.dp,
        cautionIconGap = 12.dp,
    ),
}

@Composable
private fun BackupPasswordRequestScreen(
    onNoPasswordClick: () -> Unit,
    onWithPasswordClick: () -> Unit,
    onBackClick: () -> Unit,
    isSettingsLook: Boolean = false,
) {
    val look = if (isSettingsLook) BackupRequestLook.Settings else BackupRequestLook.Keygen
    V2Scaffold(onBackClick = onBackClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            UiSpacer(size = look.topGap)

            UiIcon(
                modifier =
                    Modifier.size(64.dp)
                        .background(
                            color = Theme.v2.colors.backgrounds.tertiary_2,
                            shape = Theme.v2.radius.lg,
                        )
                        .clip(shape = Theme.v2.radius.lg)
                        .padding(all = 16.dp),
                drawableResId = look.headerIcon,
                size = 32.dp,
                tint = look.headerTint,
            )

            UiSpacer(size = 36.dp)

            Text(
                text = stringResource(R.string.backup_password_request_title),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )

            UiSpacer(size = 16.dp)

            BackupCaution(
                icon = look.secureIcon,
                look = look,
                mainText =
                    stringResource(R.string.backup_password_request_caution_secure_without_desc),
                highlightedWords =
                    listOf(
                        stringResource(R.string.backup_password_request_highlight_secure_without)
                    ),
            )

            UiSpacer(size = 16.dp)
            BackupCaution(
                icon = look.encryptIcon,
                look = look,
                mainText = stringResource(R.string.backup_password_request_caution_encrypt_desc),
                highlightedWords =
                    listOf(stringResource(R.string.backup_password_request_highlight_encrypt)),
            )

            UiSpacer(size = 16.dp)
            BackupCaution(
                icon = look.cannotResetIcon,
                look = look,
                mainText = stringResource(look.cannotResetText),
                highlightedWords =
                    listOf(stringResource(R.string.backup_password_request_highlight_cannot)),
            )

            UiSpacer(weight = 1f)

            UiSpacer(size = 24.dp)

            VsButton(
                label = stringResource(R.string.backup_password_request_no_password_action),
                onClick = onNoPasswordClick,
                variant = VsButtonVariant.Primary,
                size = VsButtonSize.Medium,
                modifier =
                    Modifier.fillMaxWidth()
                        .testTag(BackupPasswordRequestScreenTags.BACKUP_WITHOUT_PASSWORD),
            )

            UiSpacer(12.dp)

            VsButton(
                label = stringResource(look.withPasswordAction),
                onClick = onWithPasswordClick,
                variant = VsButtonVariant.Secondary,
                size = VsButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
            )

            UiSpacer(size = look.bottomGap)
        }
    }
}

@Composable
private fun BackupCaution(
    mainText: String,
    highlightedWords: List<String>,
    @DrawableRes icon: Int,
    look: BackupRequestLook = BackupRequestLook.Keygen,
) {
    V2Container(
        type = ContainerType.SECONDARY,
        borderType = ContainerBorderType.Bordered(),
        radius = look.cautionRadius,
        modifier = Modifier.padding(horizontal = look.cautionSideInset),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(all = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = icon,
                size = look.cautionIconSize,
                tint = Theme.v2.colors.primary.accent4,
            )
            UiSpacer(look.cautionIconGap)

            Text(
                text =
                    highlightedText(
                        mainText = mainText,
                        highlightedWords = highlightedWords,
                        mainTextColor = Theme.v2.colors.text.tertiary,
                        mainTextStyle = Theme.brockmann.supplementary.footnote,
                        highlightTextColor = Theme.v2.colors.text.primary,
                        highlightTextStyle = Theme.brockmann.supplementary.footnote,
                    ),
                // The highlighted spans carry no line height of their own.
                style =
                    if (look == BackupRequestLook.Settings) Theme.brockmann.supplementary.footnote
                    else LocalTextStyle.current,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview
@Composable
private fun BackupCautionPreview() {
    BackupCaution(
        icon = R.drawable.device_backup,
        mainText =
            "By default, your backup is secure without an extra password, since you store Vault shares in different locations.",
        highlightedWords = listOf("secure without"),
    )
}
