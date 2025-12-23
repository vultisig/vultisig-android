package com.vultisig.wallet.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiAlertDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    confirmTitle: String = stringResource(R.string.ok),
) {
    AlertDialog(
        containerColor = Theme.colors.backgrounds.secondary,
        title = {
            Text(
                text = title,
                color = Theme.colors.neutrals.n100,
                style = Theme.montserrat.heading5,
            )
        },
        text = {
            Text(
                text = text,
                color = Theme.colors.neutrals.n100,
                style = Theme.montserrat.body2,
            )
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = confirmTitle,
                    color = Theme.colors.neutrals.n100,
                    style = Theme.montserrat.body3,
                )
            }
        },
    )
}

@Preview
@Composable
private fun UiAlertDialogPreview() {
    MaterialTheme {
        UiAlertDialog(
            title = "Error",
            text = "Something went wrong",
            onDismiss = {},
        )
    }
}