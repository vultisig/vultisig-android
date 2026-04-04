package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.v2.loading.V2Loading
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun PercentageChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .then(
                    if (isSelected)
                        Modifier.background(
                            color = Theme.v2.colors.primary.accent3,
                            shape = CircleShape,
                        )
                    else
                        Modifier.border(
                            width = 1.dp,
                            color = Theme.v2.colors.border.light,
                            shape = CircleShape,
                        )
                )
                .padding(all = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading && isSelected) {
            V2Loading(modifier = Modifier.size(16.dp))
        } else {
            Text(
                text = title,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
