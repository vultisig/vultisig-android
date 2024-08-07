package com.vultisig.wallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun InformationNoteSnackBar(
    text: String,
    modifier: Modifier = Modifier,
){
    Snackbar(containerColor = Color.Transparent){
        InformationNote(
            modifier = modifier,
            text = text
        )
    }
}

@Composable
internal fun InformationNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                bottom = 16.dp,
                start = 8.dp,
                end = 8.dp,
            ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, Theme.colors.miamiMarmalade),
        colors = CardDefaults.cardColors(
            containerColor = Theme.colors.miamiMarmaladeFaded
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.Left,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = R.drawable.ic_warning,
                size = 24.dp,
                tint = Theme.colors.miamiMarmalade,
                modifier = Modifier.padding(16.dp),
            )
            Text(
                modifier = Modifier.padding(end = 16.dp, top = 16.dp, bottom = 16.dp),
                text = text,
                style = Theme.montserrat.caption.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Theme.colors.neutral100,
            )
        }
    }
}

@Preview
@Composable
private fun InformationNotePreview() {
    InformationNote(
        text = "This is a warning message,\nThis is a warning message",
    )
}