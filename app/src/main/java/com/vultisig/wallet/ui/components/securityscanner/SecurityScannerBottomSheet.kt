package com.vultisig.wallet.ui.components.securityscanner

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.ui.components.bottomsheet.SheetGrabber
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize.Medium
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
        containerColor = Theme.v2.colors.backgrounds.background,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        dragHandle = null,
    ) {
        SecurityScannerBottomSheetContent(
            contentStyle = contentStyle,
            securityScannerProvider = securityScannerModel.provider,
            onDismissRequest = onDismissRequest,
            onContinueAnyway = onContinueAnyway,
            modifier = Modifier.padding(16.dp).navigationBarsPadding(),
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.v2.colors.backgrounds.background,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        scrimColor = Theme.v2.colors.neutrals.n900.copy(alpha = 0.5f),
        dragHandle = { SheetGrabber(top = 5.dp, bottom = 16.dp) },
    ) {
        SettingsSecurityScannerContent(
            onGoBack = onDismissRequest,
            onContinueAnyway = onContinueAnyway,
            modifier = Modifier.padding(horizontal = 16.dp).navigationBarsPadding(),
        )
    }
}

/** The settings disable warning: names its provider first, then the medallion and the choice. */
@Composable
private fun SettingsSecurityScannerContent(
    onGoBack: () -> Unit,
    onContinueAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.security_scanner_powered_by).trim(),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.supplementary.caption,
            )
            Image(
                painter = painterResource(R.drawable.blockaid_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Theme.v2.colors.text.tertiary),
                modifier = Modifier.height(BLOCKAID_LOGO_HEIGHT),
            )
        }

        Image(
            painter = painterResource(R.drawable.ic_security_scanner_warning_medallion),
            contentDescription = null,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.vault_settings_security_screen_title_bottomsheet),
                color = Theme.v2.colors.alerts.warning,
                style = Theme.brockmann.headings.title2,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.vault_settings_security_screen_content_bottomsheet),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VsButton(
                label = stringResource(R.string.security_scanner_continue_go_back),
                variant = Primary,
                state = Enabled,
                size = Medium,
                labelStyle = Theme.brockmann.button.medium.medium,
                onClick = onGoBack,
                modifier = Modifier.fillMaxWidth(),
            )
            ContinueAnywayButton(enabled = true, onClick = onContinueAnyway)
        }
    }
}

@Composable
fun SecurityScannerBottomSheetContent(
    contentStyle: SecurityScannerBottomSheetStyle,
    securityScannerProvider: String?,
    onDismissRequest: () -> Unit,
    onContinueAnyway: () -> Unit,
    modifier: Modifier = Modifier,
    isContinueAnywayEnabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            painter = painterResource(contentStyle.image),
            contentDescription = null,
            tint = contentStyle.imageColor,
            modifier = Modifier.size(24.dp),
        )

        Text(
            text = contentStyle.title,
            color = contentStyle.imageColor,
            style = Theme.brockmann.headings.title2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = contentStyle.description,
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.body.s.medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (securityScannerProvider != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.security_scanner_powered_by),
                    color = Theme.v2.colors.text.tertiary,
                    style = Theme.brockmann.body.s.medium,
                    textAlign = TextAlign.Center,
                )

                Image(
                    painter = painterResource(id = getSecurityScannerLogo(securityScannerProvider)),
                    contentDescription = null,
                    // Figma node 39523:57131 specs the inline brand mark at 13px
                    // tall — same hierarchy as the surrounding "Powered by" copy.
                    // The previous 16dp made it read like a separate badge.
                    modifier = Modifier.height(13.dp),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VsButton(
                label = stringResource(R.string.security_scanner_continue_go_back),
                variant = Primary,
                state = Enabled,
                size = Medium,
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            )

            ContinueAnywayButton(enabled = isContinueAnywayEnabled, onClick = onContinueAnyway)
        }
    }
}

// Figma's 10sp text/disabled was barely legible, so this is caption on text/tertiary; the 48dp box
// keeps a real tap target on the override of a high-risk warning.
@Composable
private fun ContinueAnywayButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .alpha(if (enabled) 1f else DisabledContinueAnywayAlpha)
                .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.security_scanner_continue_anyway),
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.supplementary.caption,
        )
    }
}

/**
 * A scan that found nothing, opened from the scanner's mark: the verdict's clean counterpart, with
 * a single way back to the figures.
 */
@Composable
internal fun SecurityScannerSafeContent(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_security_scanner_safe_medallion),
            contentDescription = null,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.security_scanner_safe_title),
                color = Theme.v2.colors.text.primary,
                style = Theme.brockmann.headings.title2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.security_scanner_safe_description),
                color = Theme.v2.colors.text.tertiary,
                style = Theme.brockmann.body.s.medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        VsButton(
            label = stringResource(R.string.security_scanner_safe_continue),
            variant = Primary,
            state = Enabled,
            size = Medium,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SecurityScannerResult.getSecurityScannerBottomSheetStyle(): SecurityScannerBottomSheetStyle {
    val title =
        when (riskLevel) {
            SecurityRiskLevel.MEDIUM -> stringResource(R.string.security_scanner_medium_risk_title)
            SecurityRiskLevel.HIGH -> stringResource(R.string.security_scanner_high_risk_title)
            SecurityRiskLevel.CRITICAL ->
                stringResource(R.string.security_scanner_critical_risk_title)
            SecurityRiskLevel.NONE,
            SecurityRiskLevel.LOW -> stringResource(R.string.security_scanner_low_risk_title)
        }

    // Surface the most specific reason the scanner gave, so the sheet explains *why* it flagged
    // the tx instead of showing a bare risk level. Order: the provider's own description, then the
    // individual feature warnings, then the classification recommendation, falling back to the
    // generic copy only when nothing usable is present. Guard on blank, not just null — Blockaid
    // sometimes returns an empty (non-null) description, which `?:` would let render as an empty
    // line (the symptom that prompted this).
    val description =
        description?.takeIf { it.isNotBlank() }
            ?: warnings
                .mapNotNull { warning -> warning.message.takeIf { it.isNotBlank() } }
                .distinct()
                .joinToString(separator = "\n")
                .takeIf { it.isNotBlank() }
            ?: recommendations.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.security_scanner_default_description)
    val (color, icon) =
        if (riskLevel == SecurityRiskLevel.CRITICAL || riskLevel == SecurityRiskLevel.HIGH) {
            Pair(Theme.v2.colors.alerts.error, R.drawable.ic_triangle_alert)
        } else {
            Pair(Theme.v2.colors.alerts.warning, R.drawable.alert)
        }

    return SecurityScannerBottomSheetStyle(
        title = title,
        description = description,
        imageColor = color,
        image = icon,
    )
}

@Immutable
data class SecurityScannerBottomSheetStyle(
    val title: String,
    val description: String,
    @DrawableRes val image: Int,
    val imageColor: Color,
)

private const val DisabledContinueAnywayAlpha = 0.4f
private val BLOCKAID_LOGO_HEIGHT = 9.dp
