package com.vultisig.wallet.ui.screens.keygen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.CheckField
import com.vultisig.wallet.ui.models.keygen.BackupSuggestionViewModel
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.TopBarWithoutNav
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.WarningCard
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BackupSuggestionScreen(
    viewModel: BackupSuggestionViewModel = hiltViewModel()
) {
    val uiModel by viewModel.uiModel.collectAsState()
    BackHandler { viewModel.close() }
    BackupSuggestion(
        ableToSkip = uiModel.ableToSkip,
        showSkipConfirm = uiModel.showSkipConfirm,
        isConsentChecked = uiModel.isConsentChecked,
        skipBackup = viewModel::skipBackup,
        close = viewModel::close,
        closeSkipConfirm = viewModel::closeSkipConfirm,
        checkConsent = viewModel::checkConsent,
        navigateToBackupPasswordScreen = viewModel::navigateToBackupPasswordScreen
    )
}

@Composable
internal fun BackupSuggestion(
    ableToSkip: Boolean = true,
    showSkipConfirm: Boolean = false,
    isConsentChecked: Boolean = false,
    checkConsent: (Boolean) -> Unit,
    skipBackup: () -> Unit,
    closeSkipConfirm: () -> Unit,
    close: () -> Unit,
    navigateToBackupPasswordScreen: () -> Unit
) {
    Box {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Theme.colors.oxfordBlue800),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column (
                horizontalAlignment = Alignment.CenterHorizontally,
            ){
                Image(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    painter = painterResource(id = R.drawable.backup_suggestion_header),
                    contentDescription = "backup suggestion header"
                )
                Image(
                    painter = painterResource(id = R.drawable.backup_suggestion),
                    contentDescription = "backup suggestion"
                )
                UiSpacer(size = 18.dp)
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    text = stringResource(id = R.string.backup_suggestion_header),
                    style = Theme.montserrat.heading4.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp,
                        lineHeight = 36.sp
                    ),
                    color = Theme.colors.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            WarningCard(
                endIcon = R.drawable.ic_warning,
                endIconSize = 24.dp,
                endIconTint = Theme.colors.alert,
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 16.dp).weight(1f),
                    text = stringResource(id = R.string.backup_suggestion_warning),
                    style = Theme.montserrat.subtitle1,
                    color = Theme.colors.neutral0,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                text = stringResource(id = R.string.backup_suggestion_body),
                style = Theme.montserrat.subtitle3.copy(lineHeight = 22.sp),
                color = Theme.colors.neutral0,
                textAlign = TextAlign.Center
            )
            Column(
                modifier = Modifier
                    .background(Theme.colors.oxfordBlue800)
                    .padding(
                        horizontal = 16.dp,
                        vertical = 16.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MultiColorButton(
                    text = stringResource(R.string.vault_settings_backup_title),
                    textColor = Theme.colors.oxfordBlue800,
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = navigateToBackupPasswordScreen
                )

                UiSpacer(size = 14.dp)
            }
        }
        if (ableToSkip)
            UiIcon(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                drawableResId = R.drawable.x,
                size = 20.dp,
                onClick = close
            )
        if (showSkipConfirm)
            SkipBackupConfirm(
                isConsentChecked = isConsentChecked,
                checkConsent = checkConsent,
                skipBackup = skipBackup,
                closeSkipConfirm = closeSkipConfirm
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkipBackupConfirm(
    isConsentChecked: Boolean,
    checkConsent: (Boolean) -> Unit,
    skipBackup: () -> Unit,
    closeSkipConfirm: () -> Unit
){
    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
        dragHandle = null,
        containerColor = Theme.colors.oxfordBlue800,
        onDismissRequest = closeSkipConfirm,
    ) {
        Column {
            TopBarWithoutNav(
                centerText = stringResource(R.string.backup_suggestion_skip_backup),
                startIcon = R.drawable.x,
                onStartIconClick = closeSkipConfirm,
            )

            CheckField(
                modifier = Modifier.padding(16.dp),
                isChecked = isConsentChecked,
                onCheckedChange = checkConsent,
                textStyle = Theme.menlo.body2.copy(
                    color = Theme.colors.neutral0,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                ),
                title = stringResource(id = R.string.backup_suggestion_skip_consent),
            )

            MultiColorButton(
                backgroundColor = Theme.colors.miamiMarmalade,
                textColor = Theme.colors.oxfordBlue600Main,
                iconColor = Theme.colors.turquoise800,
                textStyle = Theme.montserrat.subtitle1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                disabled = !isConsentChecked,
                text = stringResource(id = R.string.backup_suggestion_skip_backup),
                onClick = {
                    skipBackup()
                },
            )
        }
    }
}



@Preview
@Composable
fun BackupSuggestionPreview() {
    BackupSuggestion(
        showSkipConfirm = false,
        isConsentChecked = false,
        checkConsent = {},
        skipBackup = {},
        close = {},
        closeSkipConfirm = {},
        navigateToBackupPasswordScreen = {}
    )
}

@Preview
@Composable
fun BackupSuggestionShowSkipPreview() {
    BackupSuggestion(
        showSkipConfirm = true,
        isConsentChecked = false,
        checkConsent = {},
        skipBackup = {},
        close = {},
        closeSkipConfirm = {},
        navigateToBackupPasswordScreen = {}
    )
}
