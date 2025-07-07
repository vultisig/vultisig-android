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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
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
    onContinueAnyway: () -> Unit,
    onDismissRequest: () -> Unit,
    provider: String,
) {
    val contentStyle = securityScannerdModel.getSecurityScannerBottomSheetStyle()

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
                imageVector = contentStyle.image,
                contentDescription = "Warning",
                tint = contentStyle.imageColor,
                modifier = Modifier.size(32.dp)
            )

            Text(
                text = contentStyle.title,
                color = Theme.colors.alerts.error,
                style = Theme.brockmann.headings.title2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = contentStyle.description,
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

@Composable
fun SecurityScannerResult.getSecurityScannerBottomSheetStyle(): SecurityScannerBottomSheetStyle {
    val title = when (riskLevel) {
        SecurityRiskLevel.MEDIUM -> "Medium Security Risk"
        SecurityRiskLevel.HIGH -> "High Security Risk"
        SecurityRiskLevel.CRITICAL -> "Critical Security Risk"
        SecurityRiskLevel.NONE,
        SecurityRiskLevel.LOW -> "No Security Risk"
    }

    val description = description ?: "This transaction has been flagged as potentially dangerous."
    val (color, icon) = if (riskLevel == SecurityRiskLevel.CRITICAL || riskLevel == SecurityRiskLevel.HIGH) {
        Pair(Theme.colors.alerts.error, Icons.Outlined.Warning)
    } else {
        Pair(Theme.colors.alerts.warning, Icons.Outlined.Info)
    }

    return SecurityScannerBottomSheetStyle(
        title = title,
        description = description,
        imageColor = color,
        image = icon,
    )
}

data class SecurityScannerBottomSheetStyle(
    val title: String,
    val description: String,
    val image: ImageVector,
    val imageColor: Color,
)