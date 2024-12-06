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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.WarningCard
import com.vultisig.wallet.ui.models.keygen.BackupSuggestionViewModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun BackupSuggestionScreen(
    viewModel: BackupSuggestionViewModel = hiltViewModel()
) {
    BackHandler { }
    BackupSuggestion(
        navigateToBackupPasswordScreen = viewModel::navigateToBackupPasswordScreen
    )
}

@Composable
internal fun BackupSuggestion(
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                        fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 36.sp
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
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .weight(1f),
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
                    modifier = Modifier.fillMaxWidth(),
                    onClick = navigateToBackupPasswordScreen
                )

                UiSpacer(size = 14.dp)
            }
        }
    }
}


@Preview
@Composable
fun BackupSuggestionPreview() {
    BackupSuggestion(navigateToBackupPasswordScreen = {})
}
