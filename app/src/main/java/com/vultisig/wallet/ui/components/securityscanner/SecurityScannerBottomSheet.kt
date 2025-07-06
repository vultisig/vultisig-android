package com.vultisig.wallet.ui.components.securityscanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Disabled
import com.vultisig.wallet.ui.components.buttons.VsButtonState.Enabled
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant.Primary
import com.vultisig.wallet.ui.theme.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScannerBottomSheet(
    securityScannerdModel: SecurityScannerResult,
    title: String,
    description: String,
    onContinueAnyway: () -> Unit,
    onDismissRequest: () -> Unit,
    provider: String,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Theme.colors.backgrounds.secondary,
        shape = RoundedCornerShape(24.dp),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = "Warning",
                tint = Theme.colors.alerts.error,
                modifier = Modifier.size(32.dp)
            )

            Text(
                text = title,
                color = Theme.colors.alerts.error,
                style = Theme.brockmann.headings.title2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = description,
                color = Theme.colors.text.extraLight,
                style = Theme.brockmann.body.s.medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Powered by ",
                    color = Theme.colors.text.extraLight,
                    style = Theme.brockmann.body.s.medium,
                    textAlign = TextAlign.Center,
                )

                Image(
                    painter = painterResource(id = getSecurityScannerLogo(provider)),
                    contentDescription = "Provider Logo",
                    modifier = Modifier.height(16.dp)
                )
            }

            VsButton(
                label = "Go Back",
                variant = Primary,
                state = Enabled,
                size = Medium,
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )

            VsButton(
                label = "Continue anyway",
                variant = Primary,
                state = Disabled,
                size = Medium,
                onClick = onContinueAnyway,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}