package com.vultisig.wallet.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.MultiColorButton
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun  MonthlyBackupReminder (
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onDoNotRemind: () -> Unit,
) {

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
        dragHandle = null,
        containerColor = Theme.colors.oxfordBlue800,
        onDismissRequest = onDismiss,
    ) {
        Column (
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            UiSpacer(size = 16.dp)
            Box {
                UiIcon(
                    modifier = Modifier.align(Alignment.TopEnd),
                    drawableResId = R.drawable.x,
                    size = 20.dp,
                    onClick = onDismiss
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    text = stringResource(id = R.string.monthly_backup_reminder_title),
                    style = Theme.montserrat.subtitle1.copy(fontWeight = FontWeight.Medium),
                    color = Theme.colors.neutral0,
                    textAlign = TextAlign.Center
                )

            }
            UiSpacer(size = 16.dp)
            MultiColorButton(
                text = stringResource(R.string.backup_password_screen_title),
                textColor = Theme.colors.oxfordBlue800,
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = onBackup,
            )

            UiSpacer(size = 16.dp)

            MultiColorButton(
                text = stringResource(R.string.do_not_remind_me_again),
                backgroundColor = Theme.colors.oxfordBlue800,
                textColor = Theme.colors.turquoise800,
                iconColor = Theme.colors.oxfordBlue800,
                borderSize = 1.dp,
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = onDoNotRemind
            )
        }
    }
}