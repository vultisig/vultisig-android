package com.vultisig.wallet.debug

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBlockedReason
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimError
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.payload.DAppMetadata
import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.MakeQrCodeBitmapShareFormat
import com.vultisig.wallet.data.usecases.QrShareField
import com.vultisig.wallet.data.usecases.QrShareInfo
import com.vultisig.wallet.ui.components.SignMessageCard
import com.vultisig.wallet.ui.components.SignTonDisplayView
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.buttons.FastSignPairedButtons
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.hero.HeroCoinAmount
import com.vultisig.wallet.ui.components.hero.HeroContent
import com.vultisig.wallet.ui.components.securityscanner.SecurityScannerBottomSheetContent
import com.vultisig.wallet.ui.components.securityscanner.SecurityScannerBottomSheetStyle
import com.vultisig.wallet.ui.components.v2.fastselection.SelectPopupUiModel
import com.vultisig.wallet.ui.components.v2.fastselection.components.ChainSelectorPickerItem
import com.vultisig.wallet.ui.components.v2.fastselection.components.SelectPopup
import com.vultisig.wallet.ui.components.v2.snackbar.rememberVsSnackbarState
import com.vultisig.wallet.ui.models.AccountUiModel
import com.vultisig.wallet.ui.models.ChainTokenUiModel
import com.vultisig.wallet.ui.models.ChainTokensUiModel
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosStakingVerifyUiState
import com.vultisig.wallet.ui.models.cosmosstaking.CosmosStakingVerifyValidatorRow
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.keygen.ImportSeedphraseUiModel
import com.vultisig.wallet.ui.models.keygen.VaultBackupState
import com.vultisig.wallet.ui.models.keygen.VerifyPinState
import com.vultisig.wallet.ui.models.keysign.DecodedFunctionParam
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.TonMessageOperation
import com.vultisig.wallet.ui.models.keysign.TonMessageUiModel
import com.vultisig.wallet.ui.models.keysign.TransactionStatus
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import com.vultisig.wallet.ui.models.peer.NetworkOption
import com.vultisig.wallet.ui.models.peer.PeerDiscoveryUiModel
import com.vultisig.wallet.ui.models.qbtc.QbtcClaimUiState
import com.vultisig.wallet.ui.models.qbtc.QbtcClaimUtxoUiModel
import com.vultisig.wallet.ui.models.swap.SwapFormUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.models.toNetworkUiModel
import com.vultisig.wallet.ui.screens.TransactionDoneView
import com.vultisig.wallet.ui.screens.cosmosstaking.CosmosStakingVerifyContent
import com.vultisig.wallet.ui.screens.deposit.BondFormContent
import com.vultisig.wallet.ui.screens.keygen.FastVaultVerificationScreen
import com.vultisig.wallet.ui.screens.keygen.ImportSeedphraseContent
import com.vultisig.wallet.ui.screens.keygen.SelectVaultTypeScreenPreview
import com.vultisig.wallet.ui.screens.keysign.KeysignView
import com.vultisig.wallet.ui.screens.peer.PeerDiscoveryScreen
import com.vultisig.wallet.ui.screens.qbtc.QbtcClaimScreen
import com.vultisig.wallet.ui.screens.referral.ContentRow
import com.vultisig.wallet.ui.screens.referral.EmptyReferralBanner
import com.vultisig.wallet.ui.screens.send.VerifySendScreen
import com.vultisig.wallet.ui.screens.settings.DiscountTiersScreenPreview
import com.vultisig.wallet.ui.screens.settings.TierType
import com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink.TierDiscountBottomSheetContent
import com.vultisig.wallet.ui.screens.swap.SwapScreen
import com.vultisig.wallet.ui.screens.swap.VerifySwapScreen
import com.vultisig.wallet.ui.screens.swap.preview.SwapFormQuoteLoadingPreview
import com.vultisig.wallet.ui.screens.transaction.SendTxOverviewScreen
import com.vultisig.wallet.ui.screens.transaction.TransactionHistoryEmptyState
import com.vultisig.wallet.ui.screens.transaction.UiTransactionInfo
import com.vultisig.wallet.ui.screens.transaction.UiTransactionInfoType
import com.vultisig.wallet.ui.screens.v2.chaintokens.ChainTokensScreen
import com.vultisig.wallet.ui.screens.v2.defi.HeaderDeFiWidget
import com.vultisig.wallet.ui.screens.v2.home.components.AccountList
import com.vultisig.wallet.ui.screens.v2.home.components.AssetAction
import com.vultisig.wallet.ui.screens.v2.home.components.AssetActionButton
import com.vultisig.wallet.ui.screens.v2.home.components.CameraButton
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.UpgradeBanner
import com.vultisig.wallet.ui.screens.v2.home.pager.container.HomePagePagerContainer
import com.vultisig.wallet.ui.theme.OnBoardingComposeTheme
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareQrPreviewEntryPoint {
    fun generateQrBitmap(): GenerateQrBitmap

    fun makeQrCodeBitmapShareFormat(): MakeQrCodeBitmapShareFormat
}

class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "swap_confirm"
        setContent {
            OnBoardingComposeTheme {
                when (screen) {
                    "swap_confirm" -> SwapConfirmPreview()
                    "swap_confirm_disabled" -> SwapConfirmPreview(allConsents = false)
                    "asset_action_button" -> AssetActionButtonPreview()
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
                    "ton_display_single" -> TonDisplayPreview(messageCount = 1)
                    "ton_display_multi" -> TonDisplayPreview(messageCount = 4)
                    "verify_ton_jetton_before" -> VerifyTonJettonPreview(decoded = false)
                    "verify_ton_jetton_after" -> VerifyTonJettonPreview(decoded = true)
                    "swap_error_before" -> SwapErrorBeforePreview()
                    "swap_error" -> SwapErrorPreview()
                    "swap_quote_loading" -> SwapFormQuoteLoadingPreview()
                    "import_seedphrase" -> ImportSeedphrasePreview()
                    "defi_account_list" -> DeFiAccountListPreview()
                    "share_qr_keysign" -> ShareQrKeysignPreview()
                    "share_qr_keysign_swap" -> ShareQrKeysignSwapPreview()
                    "share_qr_keygen" -> ShareQrKeygenPreview()
                    "blockaid_hero_send" -> BlockaidHeroVerifySendPreview()
                    "blockaid_hero_swap" -> BlockaidHeroVerifySwapPreview()
                    "blockaid_hero_unverified" -> BlockaidHeroVerifyUnverifiedPreview()
                    "blockaid_hero_scanning" -> BlockaidHeroVerifyScanningPreview()
                    "blockaid_hero_not_scanned" -> BlockaidHeroVerifyNotScannedPreview()
                    "blockaid_hero_done_send" -> BlockaidHeroDoneSendPreview()
                    "blockaid_hero_done_swap" -> BlockaidHeroDoneSwapPreview()
                    "blockaid_hero_done_unverified" -> BlockaidHeroDoneUnverifiedPreview()
                    "blockaid_popup_high" -> BlockaidPopupHighRiskPreview()
                    "blockaid_popup_medium" -> BlockaidPopupMediumRiskPreview()
                    "select_chain_popup" -> SelectChainPopupPreview()
                    "dapp_banner_verify_full" -> DappBannerVerifyPreview(DappBannerVariant.FULL)
                    "dapp_banner_verify_name_only" ->
                        DappBannerVerifyPreview(DappBannerVariant.NAME_ONLY)
                    "dapp_banner_verify_host_only" ->
                        DappBannerVerifyPreview(DappBannerVariant.HOST_ONLY)
                    "dapp_banner_send_done" -> DappBannerSendDonePreview()
                    "decoded_function_verify_collapsed" ->
                        VerifyDecodedSendPreview(expanded = false)
                    "decoded_function_verify_expanded_before" ->
                        VerifyDecodedSendPreview(expanded = true, useRichRows = false)
                    "decoded_function_verify_expanded_after" ->
                        VerifyDecodedSendPreview(expanded = true, useRichRows = true)
                    "universal_router_verify_collapsed" ->
                        VerifyUniversalRouterPreview(expanded = false)
                    "universal_router_verify_before" ->
                        VerifyUniversalRouterPreview(expanded = true, useUrRows = false)
                    "universal_router_verify_after" ->
                        VerifyUniversalRouterPreview(expanded = true, useUrRows = true)
                    "qbtc_claim" -> QbtcClaimSelectingPreview()
                    "qbtc_claim_pairing" -> QbtcClaimPairingPreview()
                    "qbtc_claim_done" -> QbtcClaimDonePreview()
                    "qbtc_claim_error" -> QbtcClaimErrorPreview()
                    "qbtc_claim_blocked" -> QbtcClaimBlockedPreview()
                    "keysign_signing_lunc" -> KeysignSigningLuncPreview()
                    "circle_usdc_widget" -> CircleUsdcWidgetPreview()
                    "btc_detail_claim" -> BtcDetailClaimPreview()
                    "qbtc_detail_claim" -> QbtcDetailClaimPreview()
                    "keysign_devices_plus_before" -> KeysignDevicesCountPreview(allowsMore = true)
                    "keysign_devices_plus_after" -> KeysignDevicesCountPreview(allowsMore = false)
                    "staking_verify_before" -> CosmosStakingVerifyCtaPreview(newButtons = false)
                    "staking_verify_after" -> CosmosStakingVerifyCtaPreview(newButtons = true)
                    "staking_verify_qbtc" ->
                        CosmosStakingVerifyCtaPreview(newButtons = true, qbtc = true)
                    "sign_message_before" -> VerifySignMessageCtaPreview(newButtons = false)
                    "sign_message_after" -> VerifySignMessageCtaPreview(newButtons = true)
                    else -> SwapConfirmPreview()
                }
            }
        }
    }
}

@Composable
private fun AssetActionButtonPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
        AssetActionButton(action = AssetAction.SWAP, isSelected = true)
        AssetActionButton(action = AssetAction.SEND, isSelected = false)
        AssetActionButton(action = AssetAction.RECEIVE, isSelected = false)
        AssetActionButton(action = AssetAction.BUY, isSelected = false)
    }
}

@Composable
private fun BannerPreview() {
    HomePagePagerContainer { UpgradeBanner {} }
}

/**
 * Keysign peer-discovery for a 2-of-3 vault. [allowsMore] = true reproduces the old "Devices
 * (1/2+)" (before #4769); false shows the corrected plain "Devices (1/2)" — keysign only ever needs
 * exactly the threshold, so the "+" was misleading.
 */
@Composable
private fun KeysignDevicesCountPreview(allowsMore: Boolean) {
    PeerDiscoveryScreen(
        state =
            PeerDiscoveryUiModel(
                localPartyId = "iPhone-A1B",
                network = NetworkOption.Local,
                devices = emptyList(),
                selectedDevices = emptyList(),
                minimumDevices = 2,
                minimumDevicesDisplayed = 2,
                allowsMoreDevices = allowsMore,
                enableNotification = true,
            ),
        onResendNotification = {},
        onBackClick = {},
        onHelpClick = {},
        onShareQrClick = {},
        onSwitchModeClick = {},
        onDeviceClick = {},
        onNextClick = {},
        onDismissQrHelpModal = {},
        showHelp = false,
    )
}

@Composable
private fun CircleUsdcWidgetPreview() {
    Box(
        modifier =
            Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary).padding(16.dp)
    ) {
        HeaderDeFiWidget(
            title = "USDC deposited",
            iconRes = R.drawable.usdc,
            buttonFirstActionText = "Withdraw",
            buttonSecondActionText = "Deposit",
            onClickFirstAction = {},
            onClickSecondAction = {},
            totalAmount = "1500 USDC",
            totalPrice = "$1,500.34",
        )
    }
}

@Composable
private fun SwapConfirmPreview(allConsents: Boolean = true) {
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
                consentAmount = allConsents,
                consentReceiveAmount = allConsents,
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
        hasToolbar = true,
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
        onRetryClick = {},
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
    Column(
        modifier =
            Modifier.padding(24.dp)
                .fillMaxSize()
                .background(
                    color = Theme.v2.colors.variables.bordersLight,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Transaction Instructions Summary",
            style = Theme.brockmann.button.medium.regular,
            color = Theme.v2.colors.text.primary,
            fontSize = 13.sp,
        )
        SolanaInstructionMock(
            index = 1,
            type = "Transfer",
            programName = "System Program",
            programId = "11111111111111111111111111111111",
            accounts = 3,
            dataLength = 12,
        )
        SolanaInstructionMock(
            index = 2,
            type = "Transfer Checked",
            programName = "Jupiter Aggregator v6 Program - Swap Router",
            programId = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4xbmPcG9R7kwEf3p2HMYQ4u5",
            accounts = 5,
            dataLength = 32,
        )
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
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    shape = RoundedCornerShape(8.dp),
                    color = Theme.v2.colors.backgrounds.dark,
                )
                .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Instruction $index",
                style = Theme.brockmann.button.medium.regular,
                color = Theme.v2.colors.text.primary,
                fontSize = 10.sp,
            )
            Text(
                text = ": $type",
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.text.primary,
                fontSize = 10.sp,
            )
        }
        Text(
            text = "Program: $programName",
            style = Theme.brockmann.button.medium.medium,
            color = Theme.v2.colors.neutrals.n100,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Program ID: $programId",
            color = Theme.v2.colors.neutrals.n100,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Accounts: $accounts | Data length: $dataLength bytes",
            color = Theme.v2.colors.neutrals.n100,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun TonDisplayPreview(messageCount: Int) {
    val messages =
        when (messageCount) {
            1 -> listOf(TON_JETTON_MESSAGE)
            else ->
                listOf(
                    TON_JETTON_MESSAGE,
                    TonMessageUiModel(
                        operation = TonMessageOperation.Transfer,
                        recipient = "EQAB0000000000ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                        amount = "0.25 TON",
                        rawPayload = null,
                        hasStateInit = true,
                    ),
                    TonMessageUiModel(
                        operation = TonMessageOperation.ExcessGasRefund,
                        recipient = null,
                        amount = null,
                        rawPayload = "te6cckEBAQEADgAAGNUydtsAAAAAAAAABxylUgg=",
                        hasStateInit = false,
                    ),
                    TonMessageUiModel(
                        operation = TonMessageOperation.NftTransfer,
                        recipient = "EQAB7777777777ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                        amount = "0.1 TON",
                        rawPayload = "te6cckEBAQEAVAAAo1/MPRQ...",
                        hasStateInit = false,
                    ),
                )
        }
    Column(
        modifier =
            Modifier.padding(24.dp)
                .fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(16.dp)
    ) {
        SignTonDisplayView(messages = messages, initiallyExpanded = true)
    }
}

private val TON_JETTON_MESSAGE =
    TonMessageUiModel(
        operation = TonMessageOperation.JettonTransfer,
        recipient = "EQDrLq9I7m6lvP6zUGZqJ8r4y0sP3pQ1n2vWk5tXcB9aZ7eF",
        amount = "0.05 TON",
        rawPayload =
            "te6cckEBAQEAWQAArg+KfqUAAAAAAAAwOUBfXhAIAf//////////////////" +
                "////////////////////////AAvDfWFG0oYX19jwNDNBBL1rKNT9XfaGP9HyTb5" +
                "nb2Emhh6EgOvlFRU=",
        hasStateInit = false,
    )

/**
 * Full-screen keysign verify for a TonConnect jetton transfer. [decoded] = true shows the resolved
 * jetton hero (100 USDT) + decoded message rows; false is the pre-decode state (the outer gas value
 * as the hero, opaque transfer rows).
 */
@Composable
private fun VerifyTonJettonPreview(decoded: Boolean) {
    VerifySendScreen(
        state = tonJettonSendState(decoded),
        isConsentsEnabled = false,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onConsentAddress = {},
        onConsentAmount = {},
        onBackClick = {},
        onConfirmScanning = {},
        onDismissScanning = {},
        hasToolbar = true,
        initiallyExpandedDetails = true,
    )
}

private fun tonJettonSendState(decoded: Boolean): VerifyTransactionUiModel {
    val senderJettonWallet = "EQByz1234senderJettonWallet5678abcdEFGHijklMNOpqRsT"
    val recipient = "EQDrLq9I7m6lvP6zUGZqJ8r4y0sP3pQ1n2vWk5tXcB9aZ7eF"
    val tx =
        TransactionDetailsUiModel(
            // The outer message value is the forwarded gas, not the jetton amount.
            token = ValuedToken(token = Coins.Ton.TON, value = "0.32", fiatValue = "$1.79"),
            srcAddress = "UQAowner1234567890abcdefVaultTonAddress0987654321xY",
            srcVaultName = "Main Vault",
            dstAddress = senderJettonWallet,
            networkFeeFiatValue = "$0.04",
            networkFeeTokenValue = "0.0066 TON",
            heroContent =
                if (decoded) {
                    HeroContent.Send(
                        title = null,
                        coin =
                            HeroCoinAmount(
                                amount = "100",
                                ticker = "USDT",
                                logo = Coins.Ton.USDT.logo,
                            ),
                    )
                } else {
                    null
                },
            tonMessages =
                if (decoded) {
                    listOf(
                        TonMessageUiModel(
                            operation = TonMessageOperation.JettonTransfer,
                            recipient = recipient,
                            amount = "0.05 TON",
                            rawPayload = TON_JETTON_MESSAGE.rawPayload,
                            hasStateInit = false,
                        )
                    )
                } else {
                    listOf(
                        TonMessageUiModel(
                            operation = TonMessageOperation.Transfer,
                            recipient = senderJettonWallet,
                            amount = "0.32 TON",
                            rawPayload = TON_JETTON_MESSAGE.rawPayload,
                            hasStateInit = false,
                        )
                    )
                },
        )
    return VerifyTransactionUiModel(
        transaction = tx,
        consentAddress = false,
        consentAmount = false,
        hasFastSign = false,
    )
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
        isTransactionDetailVisible = false,
        onTransactionDetailVisibleChange = {},
    )
}

@Composable
private fun ContentRowPreview() {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ContentRow(text = "ABCD-1234") { UiIcon(drawableResId = R.drawable.ic_copy, size = 18.dp) }
        ContentRow(
            text =
                "https://vultisig.com/referral/very-long-referral-code-that-would-definitely-overflow-the-container-width"
        ) {
            UiIcon(drawableResId = R.drawable.ic_copy, size = 18.dp)
        }
    }
}

@Composable
private fun TierBottomSheetFullPreview() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A2335)),
        contentAlignment = Alignment.BottomCenter,
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
        state = ImportSeedphraseUiModel(wordCount = 0, expectedWordCount = 12),
        mnemonicFieldState = TextFieldState(),
        onBackClick = {},
        onImportClick = {},
    )
}

@Composable
private fun DeFiAccountListPreview() {
    val accounts =
        listOf(
            deFiAccount(
                chain = Chain.ThorChain,
                chainName = "THORChain",
                address = "thor1mtqtupwgjwn397w3dx9fqmqgzrjcal5yxz8q7v",
                fiat = "$32,201.15",
                native = "32,020.12 RUNE",
            ),
            deFiAccount(
                chain = Chain.Ethereum,
                chainName = "Ethereum",
                address = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12",
                fiat = "$7,400.00",
                assets = 4,
            ),
            deFiAccount(
                chain = Chain.MayaChain,
                chainName = "Maya",
                address = "maya1mtqtupwgjwn397w3dx9fqmqgzrjcal5yxz8q7v",
                fiat = "$1,240.50",
                assets = 3,
            ),
            deFiAccount(
                chain = Chain.Solana,
                chainName = "Solana",
                address = "8FE27ioQh3T7o22QsYVT5Re8NnHFqmFNbdqwiF3ywuZQ",
                fiat = "$990.00",
                assets = 3,
            ),
            deFiAccount(
                chain = Chain.Tron,
                chainName = "Tron",
                address = "TXYZopq123abc456def789ghi012jkl345mno678",
                fiat = "$865.75",
                native = "10,829.10 TRX",
            ),
        )

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Theme.v2.colors.backgrounds.primary)
                .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Column(
            modifier =
                Modifier.background(
                    color = Theme.v2.colors.backgrounds.secondary,
                    shape = RoundedCornerShape(12.dp),
                )
        ) {
            AccountList(
                onAccountClick = {},
                snackbarState = rememberVsSnackbarState(),
                accounts = accounts,
                isBalanceVisible = true,
                showAddress = false,
            )
        }
    }
}

private fun deFiAccount(
    chain: Chain,
    chainName: String,
    address: String,
    fiat: String,
    native: String? = null,
    assets: Int = 0,
): AccountUiModel =
    AccountUiModel(
        model = Address(chain = chain, address = address, accounts = emptyList()),
        chainName = chainName,
        logo = chain.logo,
        address = address,
        nativeTokenAmount = native,
        fiatAmount = fiat,
        assetsSize = assets,
    )

@Composable
private fun ShareQrKeysignPreview() {
    val context = LocalContext.current
    val usdcIcon = remember { BitmapFactory.decodeResource(context.resources, R.drawable.usdc) }
    ShareQrPreview(
        info =
            QrShareInfo(
                title = "Join Keysign",
                fields =
                    listOf(
                        QrShareField("Vault", "Honeypot Vault DKLS"),
                        QrShareField("Amount", "100 USDC", usdcIcon),
                        QrShareField("To", "0xe3F83...6CE1e8b2"),
                    ),
            )
    )
}

@Composable
private fun ShareQrKeysignSwapPreview() {
    val context = LocalContext.current
    val srcIcon = remember { BitmapFactory.decodeResource(context.resources, R.drawable.usdc) }
    val dstIcon = remember { BitmapFactory.decodeResource(context.resources, R.drawable.bitcoin) }
    ShareQrPreview(
        info =
            QrShareInfo(
                title = "Join Keysign Swap",
                fields =
                    listOf(
                        QrShareField("Vault", "Honeypot Vault DKLS"),
                        QrShareField("From", "100 USDC", srcIcon),
                        QrShareField("To", "0.0012 BTC", dstIcon),
                    ),
            )
    )
}

@Composable
private fun ShareQrKeygenPreview() {
    ShareQrPreview(
        info =
            QrShareInfo(
                title = "Join Keygen",
                fields =
                    listOf(
                        QrShareField("Vault", "Honeypot Vault DKLS"),
                        QrShareField("Type", "Fast Vault"),
                    ),
            )
    )
}

// Token logos and fiat balances are mocked, but the HeroContent shape
// (transfer / swap / unverified) matches what the production parser produces
// from a real Blockaid response.
@Composable
private fun BlockaidHeroVerifySendPreview() {
    VerifySendScreen(
        state = blockaidHeroSendState(),
        isConsentsEnabled = true,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onConsentAddress = {},
        onConsentAmount = {},
        onBackClick = {},
        onConfirmScanning = {},
        onDismissScanning = {},
        hasToolbar = true,
    )
}

@Composable
private fun BlockaidHeroVerifySwapPreview() {
    VerifySendScreen(
        state = blockaidHeroSwapState(),
        isConsentsEnabled = true,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onConsentAddress = {},
        onConsentAmount = {},
        onBackClick = {},
        onConfirmScanning = {},
        onDismissScanning = {},
        hasToolbar = true,
    )
}

@Composable
private fun BlockaidHeroVerifyUnverifiedPreview() {
    VerifySendScreen(
        state = blockaidHeroUnverifiedState(),
        isConsentsEnabled = true,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onConsentAddress = {},
        onConsentAmount = {},
        onBackClick = {},
        onConfirmScanning = {},
        onDismissScanning = {},
        hasToolbar = true,
    )
}

@Composable
private fun BlockaidHeroVerifyScanningPreview() {
    VerifySendScreen(
        state = blockaidHeroSendState().copy(txScanStatus = TransactionScanStatus.Scanning),
        isConsentsEnabled = true,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onConsentAddress = {},
        onConsentAmount = {},
        onBackClick = {},
        onConfirmScanning = {},
        onDismissScanning = {},
        hasToolbar = true,
    )
}

@Composable
private fun BlockaidHeroVerifyNotScannedPreview() {
    VerifySendScreen(
        state =
            blockaidHeroSendState()
                .copy(
                    txScanStatus =
                        TransactionScanStatus.Error(
                            message = "chain not supported",
                            provider = "blockaid",
                        )
                ),
        isConsentsEnabled = true,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onConsentAddress = {},
        onConsentAmount = {},
        onBackClick = {},
        onConfirmScanning = {},
        onDismissScanning = {},
        hasToolbar = true,
    )
}

@Composable
private fun BlockaidHeroDoneSendPreview() {
    TransactionDoneView(
        showToolbar = true,
        transactionHash = "0xabc123def456...",
        approveTransactionHash = "",
        transactionLink = "https://etherscan.io/tx/0xabc123",
        approveTransactionLink = "",
        onComplete = {},
        onBack = {},
        onUriClick = {},
        transactionTypeUiModel = TransactionTypeUiModel.Send(blockaidHeroSendDetails()),
    )
}

@Composable
private fun BlockaidHeroDoneSwapPreview() {
    TransactionDoneView(
        showToolbar = true,
        transactionHash = "0xabc123def456...",
        approveTransactionHash = "",
        transactionLink = "https://etherscan.io/tx/0xabc123",
        approveTransactionLink = "",
        onComplete = {},
        onBack = {},
        onUriClick = {},
        transactionTypeUiModel = TransactionTypeUiModel.Send(blockaidHeroSwapDetails()),
    )
}

@Composable
private fun BlockaidPopupHighRiskPreview() {
    BlockaidPopupOverlay(
        title = "High risk transaction detected",
        description =
            "This transaction involves a malicious address. Interacting with it may compromise your assets. Proceed only if you are certain.",
        iconRes = R.drawable.ic_triangle_alert,
        iconColor = Theme.v2.colors.alerts.error,
    )
}

@Composable
private fun BlockaidPopupMediumRiskPreview() {
    BlockaidPopupOverlay(
        title = "Medium risk transaction detected",
        description =
            "This transaction involves a malicious address. Interacting with it may compromise your assets. Proceed only if you are certain.",
        iconRes = R.drawable.alert,
        iconColor = Theme.v2.colors.alerts.warning,
    )
}

/**
 * Renders the security scanner popup the way users actually see it: a modal sheet docked to the
 * bottom of the screen, sitting on top of a scrim that partially obscures the verify screen behind.
 * Hand-assembled here because the real popup is a [androidx.compose.material3.ModalBottomSheet]
 * that can't be captured statically.
 */
@Composable
private fun BlockaidPopupOverlay(
    title: String,
    description: String,
    iconRes: Int,
    iconColor: Color,
) {
    Box(modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.background)) {
        // Stand-in for the verify screen visible behind the modal.
        VerifySendScreen(
            state = blockaidHeroSendState(),
            isConsentsEnabled = true,
            confirmTitle = "Sign",
            onFastSignClick = {},
            onConfirm = {},
            onConsentAddress = {},
            onConsentAmount = {},
            onBackClick = {},
            onConfirmScanning = {},
            onDismissScanning = {},
            hasToolbar = true,
        )

        // Scrim — Material's bottom-sheet scrim is black @ ~32% alpha.
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)))

        // Sheet docked at the bottom with rounded top corners.
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        color = Theme.v2.colors.backgrounds.background,
                        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
                    )
        ) {
            SecurityScannerBottomSheetContent(
                contentStyle =
                    SecurityScannerBottomSheetStyle(
                        title = title,
                        description = description,
                        image = iconRes,
                        imageColor = iconColor,
                    ),
                securityScannerProvider = "blockaid",
                onDismissRequest = {},
                onContinueAnyway = {},
            )
        }
    }
}

@Composable
private fun BlockaidHeroDoneUnverifiedPreview() {
    TransactionDoneView(
        showToolbar = true,
        transactionHash = "0xabc123def456...",
        approveTransactionHash = "",
        transactionLink = "https://etherscan.io/tx/0xabc123",
        approveTransactionLink = "",
        onComplete = {},
        onBack = {},
        onUriClick = {},
        transactionTypeUiModel = TransactionTypeUiModel.Send(blockaidHeroUnverifiedDetails()),
    )
}

// ---- Fixtures ----

private fun blockaidHeroSendDetails(): TransactionDetailsUiModel {
    val ethCoin = Coins.Ethereum.ETH
    return TransactionDetailsUiModel(
        token = ValuedToken(token = ethCoin, value = "0", fiatValue = "$0.00"),
        srcAddress = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12",
        srcVaultName = "Honeypot Vault DKLS",
        dstAddress = "0x9876543210FeDcBa9876543210FeDcBa98765432",
        dstLabel = "Aave V3: Pool",
        memo =
            "0xa9059cbb000000000000000000000000fedcba98765432109876543210fedcba9876543200000000000000000000000000000000000000000000000000000000077359400",
        functionName = "Approve",
        functionSignature = "approve(address,uint256)",
        functionInputs =
            "[\n  {\"name\": \"spender\", \"value\": \"0x9876...432\"},\n  {\"name\": \"amount\", \"value\": \"125000000\"}\n]",
        networkFeeFiatValue = "$1.84",
        networkFeeTokenValue = "0.000482 ETH",
        heroContent =
            HeroContent.Send(
                title = "Approve",
                coin =
                    HeroCoinAmount(
                        amount = "125",
                        ticker = "USDC",
                        logo = "https://assets.coingecko.com/coins/images/6319/large/usdc.png",
                    ),
            ),
    )
}

private fun blockaidHeroSwapDetails(): TransactionDetailsUiModel {
    val ethCoin = Coins.Ethereum.ETH
    return TransactionDetailsUiModel(
        token = ValuedToken(token = ethCoin, value = "1.0", fiatValue = "$3,847.50"),
        srcAddress = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12",
        srcVaultName = "Honeypot Vault DKLS",
        dstAddress = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45",
        dstLabel = "Uniswap V3: Router 2",
        memo = "0x5ae401dc...",
        functionName = "Multicall",
        functionSignature = "multicall(uint256,bytes[])",
        functionInputs = "[{\"name\": \"deadline\", \"value\": \"1730000000\"}]",
        networkFeeFiatValue = "$4.15",
        networkFeeTokenValue = "0.00108 ETH",
        heroContent =
            HeroContent.Swap(
                title = "Multicall",
                from = HeroCoinAmount(amount = "1", ticker = "ETH", logo = ""),
                to =
                    HeroCoinAmount(
                        amount = "3,150.42",
                        ticker = "USDC",
                        logo = "https://assets.coingecko.com/coins/images/6319/large/usdc.png",
                    ),
            ),
    )
}

private fun blockaidHeroUnverifiedDetails(): TransactionDetailsUiModel {
    val ethCoin = Coins.Ethereum.ETH
    return TransactionDetailsUiModel(
        token = ValuedToken(token = ethCoin, value = "0", fiatValue = "$0.00"),
        srcAddress = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12",
        srcVaultName = "Honeypot Vault DKLS",
        dstAddress = "0x1F98431c8aD98523631AE4a59f267346ea31F984",
        dstLabel = "Unknown Contract",
        memo = "0xdeadbeef00000000000000000000000000000000",
        functionName = "Pause",
        functionSignature = "pause()",
        functionInputs = "[]",
        networkFeeFiatValue = "$0.95",
        networkFeeTokenValue = "0.00025 ETH",
        heroContent = HeroContent.Unverified,
    )
}

private fun blockaidHeroSendState() =
    VerifyTransactionUiModel(
        transaction = blockaidHeroSendDetails(),
        consentAddress = false,
        consentAmount = false,
        hasFastSign = true,
        txScanStatus =
            TransactionScanStatus.Scanned(
                SecurityScannerResult(
                    provider = "blockaid",
                    isSecure = true,
                    riskLevel = SecurityRiskLevel.NONE,
                    warnings = emptyList(),
                    description = "Transaction is safe",
                    recommendations = "",
                )
            ),
    )

private fun blockaidHeroSwapState() =
    VerifyTransactionUiModel(
        transaction = blockaidHeroSwapDetails(),
        consentAddress = false,
        consentAmount = false,
        hasFastSign = true,
        txScanStatus =
            TransactionScanStatus.Scanned(
                SecurityScannerResult(
                    provider = "blockaid",
                    isSecure = true,
                    riskLevel = SecurityRiskLevel.NONE,
                    warnings = emptyList(),
                    description = "Transaction is safe",
                    recommendations = "",
                )
            ),
    )

private fun blockaidHeroUnverifiedState() =
    VerifyTransactionUiModel(
        transaction = blockaidHeroUnverifiedDetails(),
        consentAddress = false,
        consentAmount = false,
        hasFastSign = true,
        // No security scanner result — this case maps to the title-only hero
        // because Blockaid couldn't simulate (chain unsupported / 0x function
        // body / network failure).
        txScanStatus = TransactionScanStatus.NotStarted,
    )

private enum class DappBannerVariant {
    FULL,
    NAME_ONLY,
    HOST_ONLY,
}

@Composable
private fun DappBannerVerifyPreview(variant: DappBannerVariant) {
    val metadata =
        when (variant) {
            DappBannerVariant.FULL ->
                DAppMetadata(
                    name = "Uniswap",
                    url = "https://app.uniswap.org/swap",
                    iconUrl = "https://app.uniswap.org/favicon.ico",
                )
            DappBannerVariant.NAME_ONLY -> DAppMetadata(name = "Uniswap", url = "", iconUrl = "")
            DappBannerVariant.HOST_ONLY ->
                DAppMetadata(name = "", url = "https://app.uniswap.org/swap", iconUrl = "")
        }
    VerifySendScreen(
        state = blockaidHeroSendState(),
        dappMetadata = metadata,
        isConsentsEnabled = false,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onConsentAddress = {},
        onConsentAmount = {},
        onBackClick = {},
        onConfirmScanning = {},
        onDismissScanning = {},
        hasToolbar = true,
    )
}

@Composable
private fun DappBannerSendDonePreview() {
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
        isTransactionDetailVisible = false,
        onTransactionDetailVisibleChange = {},
        dappMetadata =
            DAppMetadata(
                name = "Uniswap",
                url = "https://app.uniswap.org/swap",
                iconUrl = "https://app.uniswap.org/favicon.ico",
            ),
    )
}

@Composable
private fun SelectChainPopupPreview() {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val networks =
        listOf(
            Chain.Ethereum.toNetworkUiModel(),
            Chain.Bitcoin.toNetworkUiModel(),
            Chain.Solana.toNetworkUiModel(),
            Chain.ThorChain.toNetworkUiModel(),
            Chain.MayaChain.toNetworkUiModel(),
            Chain.BitcoinCash.toNetworkUiModel(),
            Chain.Hyperliquid.toNetworkUiModel(),
            Chain.TerraClassic.toNetworkUiModel(),
        )

    val pressX = with(density) { (configuration.screenWidthDp.dp / 2).toPx() }
    val pressY = with(density) { (configuration.screenHeightDp.dp / 2).toPx() }

    SwapScreen(state = SwapFormUiModel(), srcAmountTextFieldState = TextFieldState())

    SelectPopup(
        uiModel =
            SelectPopupUiModel(
                items = networks,
                initialIndex = 2,
                isLongPressActive = true,
                pressPosition = Offset(pressX, pressY),
            ),
        key = { it.chain },
        onItemSelected = {},
        itemContent = { item, distanceFromCenter ->
            ChainSelectorPickerItem(item = item, distanceFromCenter = distanceFromCenter)
        },
    )
}

/**
 * Renders the real verify card with a mocked decoded `approve(USDC, ∞)` call. `useRichRows = false`
 * strips the decoded rows so the raw-JSON variant renders; `useRichRows = true` shows the labelled
 * rows with copy icons. Both variants are otherwise identical.
 */
@Composable
private fun VerifyDecodedSendPreview(expanded: Boolean = false, useRichRows: Boolean = true) {
    VerifySendScreen(
        state = decodedApproveSendState(useRichRows),
        isConsentsEnabled = false,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onConsentAddress = {},
        onConsentAmount = {},
        onBackClick = {},
        onConfirmScanning = {},
        onDismissScanning = {},
        hasToolbar = true,
        initiallyExpandedDetails = expanded,
    )
}

private fun decodedApproveSendState(useRichRows: Boolean): VerifyTransactionUiModel {
    val ethCoin = Coins.Ethereum.ETH
    val spender = "0x7a250d5630b4cf539739df2c5dacb4c659f2488d"
    val rawArgs =
        "[\"$spender\",\"115792089237316195423570985008687907853269984665640564039457584007913129639935\"]"
    val richRows =
        listOf(
            DecodedFunctionParam(
                label = UiText.StringResource(R.string.erc20_approval_spender),
                value = UiText.DynamicString(spender),
                copyableValue = spender,
                secondary = "Uniswap V2 Router",
            ),
            DecodedFunctionParam(
                label = UiText.StringResource(R.string.decoded_function_amount),
                value =
                    UiText.FormattedText(
                        R.string.decoded_function_unlimited_amount,
                        listOf("USDC"),
                    ),
                isWarning = true,
            ),
        )
    val tx =
        TransactionDetailsUiModel(
            token = ValuedToken(token = ethCoin, value = "0", fiatValue = "$0.00"),
            srcAddress = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12",
            srcVaultName = "Honeypot Vault DKLS",
            // Realistic mock: approve(USDC, spender) — the call's destination is the USDC token
            // contract, not the spender. `dstContractLabel` stays null because the USDC contract
            // isn't on the [KnownEvmContracts] allowlist; the user sees "USDC" via `dstLabel`
            // and the Uniswap V2 Router label only surfaces on the spender row.
            dstAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            dstLabel = "USDC",
            functionName = "Approve",
            functionSignature = "approve(address,uint256)",
            functionInputs = rawArgs,
            isUnlimitedApproval = true,
            approvalSpender = spender,
            approvalTokenTicker = "USDC",
            dstContractLabel = null,
            decodedFunctionParams = if (useRichRows) richRows else null,
            networkFeeFiatValue = "$1.84",
            networkFeeTokenValue = "0.000482 ETH",
            heroContent =
                HeroContent.Send(
                    title = "Approve",
                    coin =
                        HeroCoinAmount(
                            amount = "Unlimited",
                            ticker = "USDC",
                            logo = "https://assets.coingecko.com/coins/images/6319/large/usdc.png",
                        ),
                ),
        )
    return VerifyTransactionUiModel(
        transaction = tx,
        consentAddress = false,
        consentAmount = false,
        hasFastSign = false,
        txScanStatus =
            TransactionScanStatus.Scanned(
                SecurityScannerResult(
                    provider = "blockaid",
                    isSecure = true,
                    riskLevel = SecurityRiskLevel.NONE,
                    warnings = emptyList(),
                    description = "Transaction is safe",
                    recommendations = "",
                )
            ),
    )
}

/**
 * Renders the real verify card with a mocked decoded Uniswap Universal Router `execute(...)` call
 * (V3_SWAP_EXACT_IN USDC → DAI). `useUrRows = false` shows the generic positional decoder that
 * can't see inside `bytes[]`; `useUrRows = true` shows the four labelled swap rows.
 */
@Composable
private fun VerifyUniversalRouterPreview(expanded: Boolean = true, useUrRows: Boolean = true) {
    VerifySendScreen(
        state = decodedUniversalRouterSendState(useUrRows),
        isConsentsEnabled = false,
        confirmTitle = "Sign",
        onFastSignClick = {},
        onConfirm = {},
        onConsentAddress = {},
        onConsentAmount = {},
        onBackClick = {},
        onConfirmScanning = {},
        onDismissScanning = {},
        hasToolbar = true,
        initiallyExpandedDetails = expanded,
    )
}

private fun decodedUniversalRouterSendState(useUrRows: Boolean): VerifyTransactionUiModel {
    val ethCoin = Coins.Ethereum.ETH
    val urAddress = "0x66a9893cc07d91d95644aedd05d03f95e1dba8af"
    val usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    val dai = "0x6b175474e89094c44da98b954eedeac495271d0f"
    // Realistic V3_SWAP_EXACT_IN inputs blob — the same shape the 4byte path emits for a
    // 1 USDC → 0.99 DAI Universal Router swap.
    val v3SwapInput =
        "0x" +
            "0000000000000000000000001111111111111111111111111111111111111111" +
            "00000000000000000000000000000000000000000000000000000000000f4240" +
            "0000000000000000000000000000000000000000000000000dbd2fca82fa0000" +
            "00000000000000000000000000000000000000000000000000000000000000a0" +
            "0000000000000000000000000000000000000000000000000000000000000001" +
            "0000000000000000000000000000000000000000000000000000000000000002" +
            "000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" +
            "0000000000000000000000006b175474e89094c44da98b954eedeac495271d0f"
    val rawArgs = """["0x08",["$v3SwapInput"],"0"]"""
    val urRows =
        listOf(
            DecodedFunctionParam(
                label = UiText.StringResource(R.string.decoded_function_from_token),
                value = UiText.DynamicString("USDC"),
                copyableValue = usdc,
                secondary = usdc,
            ),
            DecodedFunctionParam(
                label = UiText.StringResource(R.string.decoded_function_amount_in),
                value = UiText.DynamicString("1 USDC"),
            ),
            DecodedFunctionParam(
                label = UiText.StringResource(R.string.decoded_function_to_token),
                value = UiText.DynamicString("DAI"),
                copyableValue = dai,
                secondary = dai,
            ),
            DecodedFunctionParam(
                label = UiText.StringResource(R.string.decoded_function_min_amount_out),
                value = UiText.DynamicString("0.99 DAI"),
            ),
        )
    val genericRows =
        listOf(
            DecodedFunctionParam(
                label = UiText.DynamicString("#1 (bytes)"),
                value = UiText.DynamicString("0x08"),
            ),
            DecodedFunctionParam(
                label = UiText.DynamicString("#2 (bytes[])"),
                value = UiText.DynamicString("[$v3SwapInput]".take(64) + "…"),
            ),
            DecodedFunctionParam(
                label = UiText.DynamicString("#3 (uint256)"),
                value = UiText.DynamicString("0"),
            ),
        )
    val tx =
        TransactionDetailsUiModel(
            token = ValuedToken(token = ethCoin, value = "0", fiatValue = "$0.00"),
            srcAddress = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12",
            srcVaultName = "Honeypot Vault DKLS",
            dstAddress = urAddress,
            dstContractLabel = "Uniswap Universal Router V2",
            functionName = "Execute",
            functionSignature = "execute(bytes,bytes[],uint256)",
            functionInputs = rawArgs,
            decodedFunctionParams = if (useUrRows) urRows else genericRows,
            isUniversalRouterSwap = useUrRows,
            networkFeeFiatValue = "$2.41",
            networkFeeTokenValue = "0.000632 ETH",
        )
    return VerifyTransactionUiModel(
        transaction = tx,
        consentAddress = false,
        consentAmount = false,
        hasFastSign = false,
        txScanStatus = TransactionScanStatus.NotStarted,
    )
}

@Composable
private fun ShareQrPreview(info: QrShareInfo) {
    val context = LocalContext.current
    val bgColor = Theme.v2.colors.backgrounds.primary
    val bitmap =
        remember(info) {
            val entry =
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ShareQrPreviewEntryPoint::class.java,
                )
            val qr =
                entry
                    .generateQrBitmap()
                    .invoke(
                        "vultisig://preview/${info.title.replace(" ", "_")}",
                        Color.White,
                        Color.Transparent,
                        null,
                    )
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo)
            entry.makeQrCodeBitmapShareFormat().invoke(context, qr, bgColor.toArgb(), logo, info)
        }
    Box(
        modifier = Modifier.fillMaxSize().background(bgColor).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The "Claim your QBTC" promo banner on the Bitcoin chain-detail screen, surfaced via
 * `showQbtcClaimBanner = true`.
 */
@Composable
private fun BtcDetailClaimPreview() {
    ChainTokensScreen(
        uiModel =
            ChainTokensUiModel(
                chainName = "Bitcoin",
                chainAddress = "btc1qPgm5x8d3f4a2b9c0e1f2g3h4i5j6k97b454",
                totalBalance = "$31,010.77",
                chainLogo = R.drawable.bitcoin,
                explorerURL = "https://mempool.space/",
                showQbtcClaimBanner = true,
                tokens =
                    listOf(
                        ChainTokenUiModel(
                            id = "btc-1",
                            name = "BTC",
                            balance = "0.4372 BTC",
                            fiatBalance = "$31,010.77",
                            price = "$76,694.00",
                            tokenLogo = R.drawable.bitcoin,
                            chainLogo = R.drawable.bitcoin,
                        )
                    ),
            ),
        onBackClick = {},
        onRefresh = {},
        onShowSearchBar = {},
        onHideSearchBar = {},
        onSend = {},
        onSwap = {},
        onBuy = {},
        onDeposit = {},
        onReceive = {},
        onHistory = {},
        onSelectTokens = {},
        onTokenClick = {},
        onShowReviewPopUp = {},
        onClaimQbtc = {},
    )
}

/**
 * The bottom "Claim QBTC" CTA on the QBTC chain-detail screen, surfaced via `showClaimQbtcButton =
 * true`.
 */
@Composable
private fun QbtcDetailClaimPreview() {
    ChainTokensScreen(
        uiModel =
            ChainTokensUiModel(
                chainName = "Quantum Bitcoin",
                chainAddress = "btc1qPgm5x8d3f4a2b9c0e1f2g3h4i5j6k97b454",
                totalBalance = "$31,010.77",
                chainLogo = R.drawable.qbtc,
                explorerURL = "https://mempool.space/",
                showClaimQbtcButton = true,
                tokens =
                    listOf(
                        ChainTokenUiModel(
                            id = "qbtc-1",
                            name = "QBTC",
                            balance = "0.4372 QBTC",
                            fiatBalance = "$31,010.77",
                            price = "$76,694.00",
                            tokenLogo = R.drawable.qbtc,
                            chainLogo = R.drawable.qbtc,
                        )
                    ),
            ),
        onBackClick = {},
        onRefresh = {},
        onShowSearchBar = {},
        onHideSearchBar = {},
        onSend = {},
        onSwap = {},
        onBuy = {},
        onDeposit = {},
        onReceive = {},
        onHistory = {},
        onSelectTokens = {},
        onTokenClick = {},
        onShowReviewPopUp = {},
        onClaimQbtc = {},
    )
}

@Composable
private fun QbtcClaimSelectingPreview() {
    val utxos =
        listOf(
            QbtcClaimUtxoUiModel(
                key = "a3f1:0",
                shortId = "a3f1…8d2c:0",
                subtitleConfirmations = 142,
                qbtcAmount = "0.75 QBTC",
                btcAmount = "0.75 BTC",
            ),
            QbtcClaimUtxoUiModel(
                key = "b7c4:2",
                shortId = "b7c4…1e9f:2",
                subtitleConfirmations = 38,
                qbtcAmount = "0.25 QBTC",
                btcAmount = "0.25 BTC",
            ),
            QbtcClaimUtxoUiModel(
                key = "d9e2:1",
                shortId = "d9e2…4a7b:1",
                subtitleConfirmations = 7,
                qbtcAmount = "0.10 QBTC",
                btcAmount = "0.10 BTC",
            ),
            QbtcClaimUtxoUiModel(
                key = "f0a1:0",
                shortId = "f0a1…22c5:0",
                subtitleConfirmations = 91,
                qbtcAmount = "0.20 QBTC",
                btcAmount = "0.20 BTC",
            ),
            QbtcClaimUtxoUiModel(
                key = "9b6e:3",
                shortId = "9b6e…77fa:3",
                subtitleConfirmations = 256,
                qbtcAmount = "0.15 QBTC",
                btcAmount = "0.15 BTC",
            ),
            QbtcClaimUtxoUiModel(
                key = "4c2d:0",
                shortId = "4c2d…be10:0",
                subtitleConfirmations = 12,
                qbtcAmount = "0.05 QBTC",
                btcAmount = "0.05 BTC",
            ),
            QbtcClaimUtxoUiModel(
                key = "e8f7:1",
                shortId = "e8f7…3d44:1",
                subtitleConfirmations = 504,
                qbtcAmount = "0.00 QBTC",
                btcAmount = "0.00 BTC",
            ),
        )
    val state =
        QbtcClaimUiState.Selecting(
            utxos = utxos,
            selectedKeys = utxos.take(5).map { it.key }.toSet(),
            totalSelectedSats = 125_000_000L,
            totalEligibleSats = 150_000_000L,
            canConfirm = true,
            isAllSelected = false,
        )
    QbtcClaimScreen(
        state = state,
        isFastVault = true,
        onBackClick = {},
        onToggle = {},
        onConfirm = {},
        onStartSecureVault = {},
        onRetry = {},
    )
}

/**
 * The QBTC claim co-sign pairing step, now rendered via the shared PeerDiscoveryScreen. The QR is
 * built exactly like [com.vultisig.wallet.ui.models.qbtc.QbtcClaimViewModel.renderPairingQr] —
 * black-on-white, no logo — so the painter's intrinsic size matches production. Shown in the
 * waiting state of a 2-device co-sign ("1 of 2", one device still to scan).
 */
@Composable
private fun QbtcClaimPairingPreview() {
    val context = LocalContext.current
    val qr = remember {
        val entry =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                ShareQrPreviewEntryPoint::class.java,
            )
        val deepLink =
            "https://vultisig.com?type=SignTransaction&resharePrefix=0&vault=03a1b2c3" +
                "&jsonData=" +
                "H4sIAAAAAAAA_y2OQQ6CMBBF7zJrFy1Q2rIzkRhciAvdGBfYTrCJgGlL1Bju" +
                "biTu5r2X-fMnsK7vIIPe9Q5KZIxnPGM5zxjnGUtZyhJWZJxJkQqRZpKkUmRS" +
                "ZHLPF_kRbEpyqLclNtyV-7LQ3ko9-Wxqo7VqTpX5-pSXatbdatu1b16VI_qW" +
                "T2rV_WuPtSn-lJf62v9rb_1r_42IIAdwAEcwRm8wAd8wQ_8AQ"
        val bitmap = entry.generateQrBitmap().invoke(deepLink, Color.White, Color.Transparent, null)
        BitmapPainter(bitmap.asImageBitmap(), filterQuality = FilterQuality.None)
    }
    QbtcClaimScreen(
        state =
            QbtcClaimUiState.Pairing(
                qr = qr,
                joinedDevices = emptyList(),
                localPartyId = "Pixel 9 Pro-C3D4",
                minimumDevices = 2,
            ),
        isFastVault = false,
        onBackClick = {},
        onToggle = {},
        onConfirm = {},
        onStartSecureVault = {},
        onRetry = {},
    )
}

@Composable
private fun QbtcClaimDonePreview() {
    QbtcClaimScreen(
        state =
            QbtcClaimUiState.Done(
                txHash = "774365959AC251DA340851945A053D508A5F2F6CE98861556F83783C72E9CAFC",
                totalSats = 5607L,
                explorerUrl =
                    "https://explorer.qbtc.network/tx/774365959AC251DA340851945A053D508A5F2F6CE98861556F83783C72E9CAFC",
            ),
        isFastVault = true,
        onBackClick = {},
        onToggle = {},
        onConfirm = {},
        onStartSecureVault = {},
        onRetry = {},
    )
}

@Composable
private fun QbtcClaimErrorPreview() {
    QbtcClaimScreen(
        state = QbtcClaimUiState.Failed(error = QbtcClaimError.BROADCAST_UNAVAILABLE),
        isFastVault = true,
        onBackClick = {},
        onToggle = {},
        onConfirm = {},
        onStartSecureVault = {},
        onRetry = {},
    )
}

@Composable
private fun QbtcClaimBlockedPreview() {
    QbtcClaimScreen(
        state = QbtcClaimUiState.Blocked(reason = QbtcClaimBlockedReason.NoUtxos),
        isFastVault = true,
        onBackClick = {},
        onToggle = {},
        onConfirm = {},
        onStartSecureVault = {},
        onRetry = {},
    )
}

// Renders the real keysign "Signing" Rive animation with the non-square LUNC logo injected into the
// "toToken" slot — the repro for issue #4755.
@Composable
private fun KeysignSigningLuncPreview() {
    KeysignView(
        state = KeysignState.KeysignECDSA,
        txHash = "",
        approveTransactionHash = "",
        transactionLink = "",
        approveTransactionLink = "",
        onComplete = {},
        onAddToAddressBook = {},
        progressLink = null,
        transactionTypeUiModel = null,
        hasBackClick = false,
        showSaveToAddressBook = false,
        coinLogoRes = R.drawable.lunc,
    )
}

@Composable
private fun CosmosStakingVerifyCtaPreview(newButtons: Boolean, qbtc: Boolean = false) {
    val state =
        if (qbtc) {
            // QBTC rides the same chain-generic verify screen as LUNA/LUNC. No price feed, so the
            // fiat fee is hidden ($0.00 equivalent); the fee is the qbtc-testnet min_tx_fee.
            CosmosStakingVerifyUiState(
                headlineRes = R.string.cosmos_staking_youre_staking,
                amount = "0.5",
                ticker = "QBTC",
                vaultName = "Main Vault",
                fromAddress = "qbtc1delegatorxxxxxxxxxxxxxxxxxxxxxxxxxx78wk",
                validatorRows =
                    listOf(
                        CosmosStakingVerifyValidatorRow(
                            labelRes = R.string.cosmos_staking_validator_picker,
                            value = "QBTC Labs (3% commission)",
                        )
                    ),
                networkName = "QBTC",
                feeCrypto = "0.000008 QBTC",
                feeFiat = "",
                hasFastSign = true,
                isLoading = false,
            )
        } else {
            CosmosStakingVerifyUiState(
                headlineRes = R.string.cosmos_staking_youre_staking,
                amount = "12.5",
                ticker = "LUNA",
                vaultName = "Main Vault",
                fromAddress = "terra1delegatorxxxxxxxxxxxxxxxxxxxxxxxxxx78wk",
                validatorRows =
                    listOf(
                        CosmosStakingVerifyValidatorRow(
                            labelRes = R.string.cosmos_staking_validator_picker,
                            value = "Allnodes (5% commission)",
                        )
                    ),
                networkName = "Terra",
                feeCrypto = "0.01 LUNA",
                feeFiat = "$0.02",
                hasFastSign = true,
                isLoading = false,
            )
        }
    CosmosStakingVerifyContent(state = state, onClose = {}) {
        val ctaModifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)
        if (newButtons) {
            FastSignPairedButtons(
                onFastSignClick = {},
                onPairedSignClick = {},
                modifier = ctaModifier,
            )
        } else {
            VsButton(
                label = stringResource(R.string.cosmos_staking_verify_sign),
                variant = VsButtonVariant.CTA,
                onClick = {},
                modifier = ctaModifier,
            )
        }
    }
}

@Composable
private fun VerifySignMessageCtaPreview(newButtons: Boolean) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(all = 16.dp)) {
                VerifySignMessageCta(newButtons = newButtons)
            }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SignMessageCard(
                title = stringResource(R.string.verify_sign_message_signing_method),
                value = "personal_sign",
            )
            SignMessageCard(
                title = stringResource(R.string.verify_sign_message_message_sign),
                value = "Sign in to Uniswap",
            )
        }
    }
}

@Composable
private fun ColumnScope.VerifySignMessageCta(newButtons: Boolean) {
    if (newButtons) {
        FastSignPairedButtons(onFastSignClick = {}, onPairedSignClick = {})
    } else {
        VsButton(
            label = stringResource(R.string.verify_transaction_fast_sign_btn_title),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.padding(top = 16.dp))
        VsButton(
            label = stringResource(R.string.verify_swap_sign_button),
            onClick = {},
            variant = VsButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
