package com.vultisig.wallet.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.keygen.VaultBackupState
import com.vultisig.wallet.ui.models.keygen.VerifyPinState
import com.vultisig.wallet.ui.models.keysign.TransactionStatus
import com.vultisig.wallet.ui.models.swap.SwapFormUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.screens.deposit.BondFormContent
import com.vultisig.wallet.ui.screens.keygen.FastVaultVerificationScreen
import com.vultisig.wallet.ui.screens.keygen.ImportSeedphraseContent
import com.vultisig.wallet.ui.screens.keygen.SelectVaultTypeScreenPreview
import com.vultisig.wallet.ui.screens.referral.ContentRow
import com.vultisig.wallet.ui.screens.referral.EmptyReferralBanner
import com.vultisig.wallet.ui.screens.settings.DiscountTiersScreenPreview
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink.TierDiscountBottomSheetContent
import com.vultisig.wallet.ui.screens.swap.SwapScreen
import com.vultisig.wallet.ui.screens.swap.VerifySwapScreen
import com.vultisig.wallet.ui.screens.transaction.SendTxOverviewScreen
import com.vultisig.wallet.ui.screens.transaction.TransactionHistoryEmptyState
import com.vultisig.wallet.ui.screens.transaction.UiTransactionInfo
import com.vultisig.wallet.ui.screens.transaction.UiTransactionInfoType
import com.vultisig.wallet.ui.screens.v2.home.components.CameraButton
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionType
import com.vultisig.wallet.ui.screens.v2.home.components.TransactionTypeButton
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.UpgradeBanner
import com.vultisig.wallet.ui.screens.v2.home.pager.container.HomePagePagerContainer
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.utils.UiText

class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "swap_confirm"
        setContent {
            OnBoardingComposeTheme {
                when (screen) {
                    "swap_confirm" -> SwapConfirmPreview()
                    "transaction_type_button" -> TransactionTypeButtonPreview()
                    "camera_button" -> CameraButton(onClick = {})
                    "banner" -> BannerPreview()
                    "send_tx_done" -> SendTxDonePreview()
                    "transaction_history_empty" -> TransactionHistoryEmptyState()
                    "empty_referral" -> EmptyReferralBanner(onClickedCreateReferral = {})
                    "fast_vault_verification" -> FastVaultVerificationPreview()
                    "bond_form_thor" -> BondFormThorPreview()
                    "bond_form_maya" -> BondFormMayaPreview()
                    "discount_tiers" -> DiscountTiersScreenPreview()
                    "tier_bottom_sheet" -> TierBottomSheetFullPreview()
                    "choose_vault" -> SelectVaultTypeScreenPreview()
                    "content_row" -> ContentRowPreview()
                    "solana_display" -> SolanaDisplayPreview()
                    "swap_error_before" -> SwapErrorBeforePreview()
                    "swap_error" -> SwapErrorPreview()
                    "import_seedphrase" -> ImportSeedphrasePreview()
                    else -> SwapConfirmPreview()
                }
            }
        }
    }
}

@Composable
private fun TransactionTypeButtonPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
        TransactionTypeButton(txType = TransactionType.SWAP, isSelected = true)
        TransactionTypeButton(txType = TransactionType.SEND, isSelected = false)
        TransactionTypeButton(txType = TransactionType.RECEIVE, isSelected = false)
        TransactionTypeButton(txType = TransactionType.BUY, isSelected = false)
    }
}

@Composable
private fun BannerPreview() {
    HomePagePagerContainer { UpgradeBanner {} }
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
                            provider = "1Inch",
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

@Composable
private fun FastVaultVerificationPreview() {
    FastVaultVerificationScreen(
        state =
            VaultBackupState(
                verifyPinState = VerifyPinState.Idle,
                sentEmailTo = "user@example.com",
            ),
        codeFieldState = TextFieldState(),
        onBackClick = {},
        onCodeChanged = {},
        onPasteClick = {},
        onChangeEmailClick = {},
    )
}

@Composable
private fun BondFormThorPreview() {
    BondFormContent(
        state = DepositFormUiModel(depositChain = Chain.ThorChain),
        nodeAddressFieldState = TextFieldState("thor1mtqtupwgjwn397w3dx9fqmqgzr"),
        providerFieldState = TextFieldState(),
        operatorFeeFieldState = TextFieldState("2000"),
        tokenAmountFieldState = TextFieldState("500"),
        lpUnitsFieldState = TextFieldState(),
    )
}

@Composable
private fun BondFormMayaPreview() {
    BondFormContent(
        state =
            DepositFormUiModel(
                depositChain = Chain.MayaChain,
                bondableAssets = listOf("RUNE", "CACAO"),
                selectedBondAsset = "RUNE",
            ),
        nodeAddressFieldState = TextFieldState("mayaxyxf1515615s"),
        providerFieldState = TextFieldState(),
        operatorFeeFieldState = TextFieldState(),
        tokenAmountFieldState = TextFieldState(),
        lpUnitsFieldState = TextFieldState("0"),
    )
}

@Composable
private fun SolanaDisplayPreview() {
    // Mock the expanded instruction view directly to show Program ID overflow behavior
    androidx.compose.foundation.layout.Column(
        modifier =
            Modifier.padding(24.dp)
                .fillMaxSize()
                .background(
                    color = com.vultisig.wallet.ui.theme.Theme.v2.colors.variables.bordersLight,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                )
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Text(
            text = "Transaction Instructions Summary",
            style = com.vultisig.wallet.ui.theme.Theme.brockmann.button.medium.regular,
            color = com.vultisig.wallet.ui.theme.Theme.v2.colors.text.primary,
            fontSize = 13.sp,
        )
        // Instruction 1 - System Program (short ID)
        SolanaInstructionMock(
            index = 1,
            type = "Transfer",
            programName = "System Program",
            programId = "11111111111111111111111111111111",
            accounts = 3,
            dataLength = 12,
        )
        // Instruction 2 - Jupiter aggregator (long program name + ID)
        SolanaInstructionMock(
            index = 2,
            type = "Transfer Checked",
            programName = "Jupiter Aggregator v6 Program - Swap Router",
            programId = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4xbmPcG9R7kwEf3p2HMYQ4u5",
            accounts = 5,
            dataLength = 32,
        )
        // Instruction 3 - DeFi program with very long ID
        SolanaInstructionMock(
            index = 3,
            type = "Create Associated Token Account",
            programName = "Raydium Liquidity Pool V4 Automated Market Maker",
            programId = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8xbmPcG9R7kwEf3p2HMYQ4u5",
            accounts = 7,
            dataLength = 0,
        )
    }
}

@Composable
private fun SolanaInstructionMock(
    index: Int,
    type: String,
    programName: String,
    programId: String,
    accounts: Int,
    dataLength: Int,
) {
    androidx.compose.foundation.layout.Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = com.vultisig.wallet.ui.theme.Theme.v2.colors.backgrounds.dark,
                )
                .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.material3.Text(
                text = "Instruction $index",
                style = com.vultisig.wallet.ui.theme.Theme.brockmann.button.medium.regular,
                color = com.vultisig.wallet.ui.theme.Theme.v2.colors.text.primary,
                fontSize = 10.sp,
            )
            androidx.compose.material3.Text(
                text = ": $type",
                style = com.vultisig.wallet.ui.theme.Theme.brockmann.button.medium.medium,
                color = com.vultisig.wallet.ui.theme.Theme.v2.colors.text.primary,
                fontSize = 10.sp,
            )
        }
        androidx.compose.material3.Text(
            text = "Program: $programName",
            style = com.vultisig.wallet.ui.theme.Theme.brockmann.button.medium.medium,
            color = com.vultisig.wallet.ui.theme.Theme.v2.colors.neutrals.n100,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        androidx.compose.material3.Text(
            text = "Program ID: $programId",
            color = com.vultisig.wallet.ui.theme.Theme.v2.colors.neutrals.n100,
            style = com.vultisig.wallet.ui.theme.Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        androidx.compose.material3.Text(
            text = "Accounts: $accounts | Data length: $dataLength bytes",
            color = com.vultisig.wallet.ui.theme.Theme.v2.colors.neutrals.n100,
            style = com.vultisig.wallet.ui.theme.Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun SendTxDonePreview() {
    val ethCoin = Coins.Ethereum.ETH

    SendTxOverviewScreen(
        showToolbar = true,
        showSaveToAddressBook = true,
        transactionHash = "0x1a2b3c...d4e5f6",
        transactionLink = "https://etherscan.io/tx/0x1a2b3c",
        transactionStatus = TransactionStatus.Broadcasted,
        onComplete = {},
        onBack = {},
        onAddToAddressBook = {},
        tx =
            UiTransactionInfo(
                type = UiTransactionInfoType.Send,
                token = ValuedToken(token = ethCoin, value = "1.5", fiatValue = "$3,847.50"),
                from = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12",
                to = "0x9876543210FeDcBa9876543210FeDcBa98765432",
                memo = "",
                networkFeeTokenValue = "0.0024 ETH",
                networkFeeFiatValue = "$6.15",
            ),
    )
}

@Composable
private fun ContentRowPreview() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ContentRow(text = "ABCD-1234") {
            UiIcon(drawableResId = com.vultisig.wallet.R.drawable.ic_copy, size = 18.dp)
        }
        ContentRow(
            text =
                "https://vultisig.com/referral/very-long-referral-code-that-would-definitely-overflow-the-container-width"
        ) {
            UiIcon(drawableResId = com.vultisig.wallet.R.drawable.ic_copy, size = 18.dp)
        }
    }
}

@Composable
private fun TierBottomSheetFullPreview() {
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF1A2335)),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
    ) {
        TierDiscountBottomSheetContent(tier = TierType.BRONZE, onContinue = {})
    }
}

@Composable
private fun SwapErrorBeforePreview() {
    SwapScreen(
        state =
            SwapFormUiModel(
                formError =
                    UiText.DynamicString(
                        "ExactOutRoute: slippage tolerance exceeded; inputAmount 542891003 is higher than the desired 502212966"
                    ),
                isSwapDisabled = true,
            ),
        srcAmountTextFieldState = TextFieldState("1000"),
    )
}

@Composable
private fun SwapErrorPreview() {
    val errorMessage = "Price impact is too high. Try a smaller amount or a different token pair."
    SwapScreen(
        state =
            SwapFormUiModel(formError = UiText.DynamicString(errorMessage), isSwapDisabled = true),
        srcAmountTextFieldState = TextFieldState("1000"),
    )
}

@Composable
private fun ImportSeedphrasePreview() {
    ImportSeedphraseContent(
        state =
            com.vultisig.wallet.ui.models.keygen.ImportSeedphraseUiModel(
                wordCount = 0,
                expectedWordCount = 12,
            ),
        mnemonicFieldState = TextFieldState(),
        onBackClick = {},
        onImportClick = {},
    )
}
