package com.vultisig.wallet.ui.components.securityscanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.securityscanner.BLOCKAID_PROVIDER
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.theme.Theme
import timber.log.Timber

@Composable
internal fun SecurityScannerBadget(
    status: TransactionScanStatus,
) {
    Row(
        modifier = Modifier
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            is TransactionScanStatus.Scanned -> {
                ScanStatusContentWithLogo(
                    image = Icons.Default.Check,
                    imageColor = Theme.v2.colors.alerts.success,
                    message = stringResource(R.string.security_scanner_transaction_scanned_by),
                    providerLogoId = status.result.provider,
                )
            }

            is TransactionScanStatus.Scanning -> {
                CircularProgressIndicator(
                    color = Theme.v2.colors.text.secondary,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = stringResource(R.string.security_scanner_transaction_scanning),
                    fontSize = 14.sp,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.secondary
                )
            }

            is TransactionScanStatus.Error -> {
                ScanStatusContentWithLogo(
                    image = Icons.Default.Warning,
                    imageColor = Theme.v2.colors.text.secondary,
                    message = stringResource(R.string.security_scanner_transaction_not_scanned),
                    providerLogoId = status.provider,
                )
            }

            else -> Timber.d("Status not reflected in UI: $status")
        }
    }
}

@Composable
private fun ScanStatusContentWithLogo(
    image: ImageVector,
    imageColor: Color,
    message: String,
    providerLogoId: String,
) {
    Icon(
        imageVector = image,
        contentDescription = image.name,
        tint = imageColor,
        modifier = Modifier.size(16.dp)
    )

    Spacer(modifier = Modifier.width(6.dp))

    Text(
        text = message,
        fontSize = 14.sp,
        style = Theme.brockmann.supplementary.footnote,
        color = Theme.v2.colors.text.secondary
    )

    Spacer(modifier = Modifier.width(4.dp))

    Image(
        painter = painterResource(id = getSecurityScannerLogo(providerLogoId)),
        contentDescription = "Provider Logo",
        modifier = Modifier.height(16.dp)
    )
}


internal fun getSecurityScannerLogo(provider: String): Int {
    return when (provider) {
        BLOCKAID_PROVIDER -> R.drawable.blockaid_logo
        else -> {
            Timber.w("SecurityScanner: Unknown provider logo requested: $provider")
            R.drawable.blockaid_logo
        }
    }
}