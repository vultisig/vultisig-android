package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.keygen.BackupSuggestionViewModel
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.theme.dimens

@Composable
internal fun BackupSuggestionScreen(
    viewModel : BackupSuggestionViewModel = hiltViewModel()
){
    BackupSuggestion(
        onSkipClick = viewModel::skip,
        navigateToBackupPasswordScreen = viewModel::navigateToBackupPasswordScreen
    )
}

@Composable
internal fun BackupSuggestion(
    onSkipClick: () -> Unit,
    navigateToBackupPasswordScreen: () -> Unit
){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.oxfordBlue800),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Image(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            painter = painterResource(id = R.drawable.backup_suggestion_header),
            contentDescription = "backup suggestion header"
        )
        Image(
            painter = painterResource(id = R.drawable.backup_suggestion),
            contentDescription = "backup suggestion"
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .padding(top = 20.dp),
            text = stringResource(id = R.string.backup_suggestion_text),
            style = Theme.montserrat.body1,
            color = Theme.colors.neutral0,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .padding(top = 20.dp),
            text = stringResource(id = R.string.backup_suggestion_note),
            style = Theme.montserrat.body1,
            color = Theme.colors.neutral0,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier)
        Column(
            modifier = Modifier
                .background(Theme.colors.oxfordBlue800),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MultiColorButton(
                text = stringResource(R.string.vault_settings_backup_title),
                textColor = Theme.colors.oxfordBlue800,
                minHeight = MaterialTheme.dimens.minHeightButton,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.dimens.buttonMargin,
                        end = MaterialTheme.dimens.buttonMargin
                    ),
                onClick = navigateToBackupPasswordScreen
            )

            MultiColorButton(
                text = stringResource(R.string.welcome_screen_skip),
                backgroundColor = Theme.colors.oxfordBlue800,
                textColor = Theme.colors.turquoise800,
                iconColor = Theme.colors.oxfordBlue800,
                minHeight = MaterialTheme.dimens.minHeightButton,
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                onClick = onSkipClick
            )
        }
    }
}

@Preview
@Composable
fun BackupSuggestionPreview(){
    BackupSuggestion(
        onSkipClick = {},
        navigateToBackupPasswordScreen = {}
    )
}