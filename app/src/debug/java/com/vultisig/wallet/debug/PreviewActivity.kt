package com.vultisig.wallet.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.screens.swap.VerifySwapScreen
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme

class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "swap_confirm"
        setContent {
            OnBoardingComposeTheme {
                when (screen) {
                    "swap_confirm" -> SwapConfirmPreview()
                    else -> SwapConfirmPreview()
                }
            }
        }
    }
}

@Composable
private fun SwapConfirmPreview() {
    val ethCoin = Coins.Ethereum.ETH
    val btcCoin = Coins.Bitcoin.BTC

    val tx =
        SwapTransactionUiModel(
            src = ValuedToken(token = ethCoin, value = "1.5", fiatValue = "$3,847.50"),
            dst = ValuedToken(token = btcCoin, value = "0.0589", fiatValue = "$3,820.00"),
            networkFee = ValuedToken(token = ethCoin, value = "0.0024", fiatValue = "$6.15"),
            providerFee = ValuedToken(token = ethCoin, value = "0.0045", fiatValue = "$11.52"),
            totalFee = "$17.67",
            networkFeeFormatted = "0.0024 ETH ($6.15)",
            providerFeeFormatted = "0.0045 ETH ($11.52)",
            hasConsentAllowance = false,
        )

    VerifySwapScreen(
        state =
            VerifySwapUiModel(
                tx = tx,
                consentAmount = true,
                consentReceiveAmount = true,
                consentAllowance = false,
                hasFastSign = true,
                txScanStatus =
                    TransactionScanStatus.Scanned(
                        SecurityScannerResult(
                            provider = "Blowfish",
                            isSecure = true,
                            riskLevel = SecurityRiskLevel.NONE,
                            warnings = emptyList(),
                            description = "Transaction is safe",
                            recommendations = "",
                        )
                    ),
                vaultName = "Main Vault",
            ),
        showToolbar = true,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onBackClick = {},
    )
}
