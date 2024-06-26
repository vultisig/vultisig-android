package com.vultisig.wallet.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vultisig.wallet.R
import com.vultisig.wallet.presenter.common.ClickOnce
import com.vultisig.wallet.ui.navigation.Screen
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun UiDialog(
    title: String, text: String, onDismiss: () -> Unit,
    confirmTitle: String = stringResource(R.string.ok)
) {

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Theme.colors.semiTransparentBlack
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Theme.colors.neutral0,
                        RoundedCornerShape(8.dp),

                        )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 20.dp
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,

                    ) {
                    Image(
                        painter = painterResource(id = R.drawable.vultsing2),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .size(64.dp)
                            .background(Theme.colors.oxfordBlue600Main)
                            .padding(6.dp),

                        contentDescription = "logo"
                    )

                    UiSpacer(size = 16.dp)
                    Text(
                        text = title,
                        style = Theme.menlo.subtitle1,
                        color = Theme.colors.neutral900,
                        textAlign = TextAlign.Center
                    )
                    UiSpacer(size = 16.dp)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Theme.colors.neutral900,
                        textAlign = TextAlign.Center
                    )
                    UiSpacer(size = 16.dp)
                    MultiColorButton(
                        text = confirmTitle,
                        textColor = Theme.colors.oxfordBlue800,
                        backgroundColor = Theme.colors.semiTransparentBlack2,
                        minHeight = 44.dp,
                        modifier = Modifier
                            .clip(shape = RoundedCornerShape(2.dp))
                            .fillMaxWidth()
                            .padding(
                                vertical = 16.dp,
                            ),
                        onClick = ClickOnce { onDismiss()}
                    )
                }
            }
        }

    }
}

@Preview
@Composable
private fun UiDialogPreview() {
    MaterialTheme {
        UiDialog(
            title = "Error",
            text = "Something went wrong",
            onDismiss = {},
        )
    }
}