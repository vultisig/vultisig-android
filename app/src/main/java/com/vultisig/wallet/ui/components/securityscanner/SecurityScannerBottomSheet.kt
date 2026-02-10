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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
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
internal fun SecurityScannerBottomSheet(
    securityScannerModel: SecurityScannerResult,
    onContinueAnyway: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val contentStyle = securityScannerModel.getSecurityScannerBottomSheetStyle()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Theme.v2.colors.backgrounds.secondary,
        shape = RoundedCornerShape(24.dp),
        dragHandle = null,
    ) {
        SecurityScannerBottomSheetContent(
            contentStyle = contentStyle,
            securityScannerProvider = securityScannerModel.provider,
            onDismissRequest = onDismissRequest,
            onContinueAnyway = onContinueAnyway
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSecurityScannerBottomSheet(
    onContinueAnyway: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Theme.v2.colors.backgrounds.secondary,
        shape = RoundedCornerShape(24.dp),
        dragHandle = null,
    ) {
        SecurityScannerBottomSheetContent(
            contentStyle = buildSettingsSecurityScannerBottomSheeStyle(),
            securityScannerProvider = null,
            onDismissRequest = onDismissRequest,
            onContinueAnyway = onContinueAnyway
        )
    }
}

@Composable
fun SecurityScannerBottomSheetContent(
    contentStyle: SecurityScannerBottomSheetStyle,
    securityScannerProvider: String?,
    onDismissRequest: () -> Unit,
    onContinueAnyway: () -> Unit,
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
            color = contentStyle.imageColor,
            style = Theme.brockmann.headings.title2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = contentStyle.description,
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.body.s.medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (securityScannerProvider != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.security_scanner_powered_by),
                    color = Theme.v2.colors.text.tertiary,
                    style = Theme.brockmann.body.s.medium,
                    textAlign = TextAlign.Center,
                )

                Image(
                    painter = painterResource(id = getSecurityScannerLogo(securityScannerProvider)),
                    contentDescription = "Provider Logo",
                    modifier = Modifier.height(16.dp)
                )
            }
        }

        VsButton(
            label = stringResource(R.string.security_scanner_continue_go_back),
            variant = Primary,
            state = Enabled,
            size = Medium,
            onClick = onDismissRequest,
            modifier = Modifier.fillMaxWidth()
        )

        VsButton(
            label = stringResource(R.string.security_scanner_continue_anyway),
            variant = Primary,
            state = Disabled,
            size = Medium,
            forceClickable = true,
            onClick = onContinueAnyway,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SecurityScannerResult.getSecurityScannerBottomSheetStyle(): SecurityScannerBottomSheetStyle {
    val title = when (riskLevel) {
        SecurityRiskLevel.MEDIUM -> stringResource(R.string.security_scanner_medium_risk_title)
        SecurityRiskLevel.HIGH -> stringResource(R.string.security_scanner_high_risk_title)
        SecurityRiskLevel.CRITICAL -> stringResource(R.string.security_scanner_critical_risk_title)
        SecurityRiskLevel.NONE,
        SecurityRiskLevel.LOW -> stringResource(R.string.security_scanner_low_risk_title)
    }

    val description = description ?: stringResource(R.string.security_scanner_default_description)
    val (color, icon) = if (riskLevel == SecurityRiskLevel.CRITICAL || riskLevel == SecurityRiskLevel.HIGH) {
        Pair(Theme.v2.colors.alerts.error, Icons.Outlined.Warning)
    } else {
        Pair(Theme.v2.colors.alerts.warning, Icons.Outlined.Info)
    }

    return SecurityScannerBottomSheetStyle(
        title = title,
        description = description,
        imageColor = color,
        image = icon,
    )
}

@Composable
private fun buildSettingsSecurityScannerBottomSheeStyle() = SecurityScannerBottomSheetStyle(
    title = stringResource(R.string.vault_settings_security_screen_title_bottomsheet),
    description = stringResource(R.string.vault_settings_security_screen_content_bottomsheet),
    imageColor = Theme.v2.colors.alerts.warning,
    image = Icons.Outlined.Info,
)

data class SecurityScannerBottomSheetStyle(
    val title: String,
    val description: String,
    val image: ImageVector,
    val imageColor: Color,
)