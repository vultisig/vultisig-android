package com.vultisig.wallet.ui.screens.keysign

import android.content.Context
import android.net.nsd.NsdManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.errors.ErrorState
import com.vultisig.wallet.ui.components.errors.ErrorView
import com.vultisig.wallet.ui.components.errors.ErrorViewButtonUiModel
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.KeySignWrapperViewModel
import com.vultisig.wallet.ui.models.keysign.JoinKeysignError
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.DiscoverService
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.DiscoveringSessionID
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.Error
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.JoinKeysign
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.Keysign
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.QbtcClaim
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.QbtcClaimConsent
import com.vultisig.wallet.ui.models.keysign.JoinKeysignState.WaitingForKeysignStart
import com.vultisig.wallet.ui.models.keysign.JoinKeysignViewModel
import com.vultisig.wallet.ui.models.keysign.KeysignState
import com.vultisig.wallet.ui.models.keysign.VerifyUiModel
import com.vultisig.wallet.ui.screens.deposit.VerifyDepositScreen
import com.vultisig.wallet.ui.screens.qbtc.QbtcClaimDoneContent
import com.vultisig.wallet.ui.screens.send.VerifySendScreen
import com.vultisig.wallet.ui.screens.sign.VerifySignMessageScreen
import com.vultisig.wallet.ui.screens.swap.VerifySwapScreen
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

@Composable
internal fun JoinKeysignView() {
    val viewModel: JoinKeysignViewModel = hiltViewModel()
    val context = LocalContext.current
    var keysignState: KeysignState by remember { mutableStateOf(KeysignState.CreatingInstance) }

    if (keysignState is KeysignState.KeysignFinished) {
        viewModel.enableNavigationToHome()
    }
    val state by viewModel.currentState.collectAsState()
    val verifyUiModel by viewModel.verifyUiModel.collectAsState()
    val dappMetadata by viewModel.dappMetadata.collectAsState()
    val isKeysignFinished = keysignState is KeysignState.KeysignFinished
    val isKeysignInProgress = state == Keysign && keysignState.isInProgress
    val isSignMessageDone = isKeysignFinished && verifyUiModel is VerifyUiModel.SignMessage

    if (state == JoinKeysign) {
        // Each Verify*Screen owns its scaffold + toolbar, so the join path renders
        // them directly — avoids the double V2Scaffold padding that previously
        // pushed the verify card and its button off-position vs the initiator path.
        BackHandler(onBack = viewModel::navigateToHome)
        when (val model = verifyUiModel) {
            is VerifyUiModel.Send -> {
                VerifySendScreen(
                    state = model.model,
                    dappMetadata = dappMetadata,
                    isConsentsEnabled = false,
                    hasToolbar = true,
                    confirmTitle = stringResource(R.string.verify_swap_sign_button),
                    onBackClick = viewModel::navigateToHome,
                    onFastSignClick = {},
                    onConfirm = viewModel::joinKeysign,
                )
            }

            is VerifyUiModel.Swap -> {
                VerifySwapScreen(
                    state = model.model,
                    dappMetadata = dappMetadata,
                    hasToolbar = true,
                    onBackClick = viewModel::navigateToHome,
                    confirmTitle = stringResource(R.string.verify_swap_sign_button),
                    isConsentsEnabled = false,
                    onFastSignClick = {},
                    onConfirm = viewModel::joinKeysign,
                )
            }

            is VerifyUiModel.Deposit -> {
                VerifyDepositScreen(
                    state = model.model,
                    dappMetadata = dappMetadata,
                    hasToolbar = true,
                    confirmTitle = stringResource(R.string.verify_swap_sign_button),
                    onBackClick = viewModel::navigateToHome,
                    onFastSignClick = {},
                    onConfirm = viewModel::joinKeysign,
                )
            }

            is VerifyUiModel.SignMessage -> {
                VerifySignMessageScreen(
                    state = model.model,
                    hasToolbar = true,
                    confirmTitle = stringResource(R.string.verify_swap_sign_button),
                    onBackClick = viewModel::navigateToHome,
                    onFastSignClick = {},
                    onConfirm = viewModel::joinKeysign,
                )
            }
        }
        return
    }

    val title =
        stringResource(
            when {
                isSignMessageDone -> R.string.transaction_done_overview
                isKeysignFinished -> R.string.transaction_complete_screen_title
                else -> R.string.sign_transaction
            }
        )
    JoinKeysignScreen(
        title = title,
        onBack = viewModel::navigateToHome,
        isError = state is Error,
        fullScreen = isKeysignInProgress,
        applyDefaultPaddings = !isKeysignFinished,
    ) {
        when (state) {
            DiscoveringSessionID,
            WaitingForKeysignStart -> {
                val text =
                    when (state) {
                        DiscoveringSessionID ->
                            stringResource(R.string.join_keysign_discovering_session_id)
                        WaitingForKeysignStart ->
                            stringResource(R.string.join_keysign_waiting_keysign_start)
                        else -> ""
                    }
                KeysignLoadingScreen(text = text)
            }

            DiscoverService -> {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                viewModel.discoveryMediator(nsdManager)
                KeysignLoadingScreen(text = stringResource(R.string.join_keysign_discovery_service))
            }

            JoinKeysign -> Unit // handled above, before the JoinKeysignScreen wrapper

            is QbtcClaimConsent -> {
                val consent = state as QbtcClaimConsent
                QbtcClaimConsentContent(
                    btcAddress = consent.btcAddress,
                    qbtcAddress = consent.qbtcAddress,
                    onApprove = viewModel::joinKeysign,
                )
            }

            is QbtcClaim -> {
                val claim = state as QbtcClaim
                if (claim.txHash == null) {
                    KeysignLoadingScreen(text = stringResource(R.string.qbtc_claim_proving))
                } else {
                    QbtcClaimDoneContent(
                        txHash = claim.txHash,
                        explorerUrl = claim.explorerUrl.orEmpty(),
                        totalSats = claim.totalSats ?: 0L,
                        onComplete = viewModel::complete,
                    )
                }
            }

            Keysign -> {
                val wrapperViewModel =
                    hiltViewModel(
                        creationCallback = { factory: KeySignWrapperViewModel.Factory ->
                            factory.create(viewModel.keysignViewModel)
                        }
                    )
                val keysignViewModel = wrapperViewModel.viewModel
                val keysignUiState = keysignViewModel.state.collectAsState().value
                val kState = keysignUiState.signingState
                keysignState = kState
                KeysignView(
                    state = kState,
                    txHash = keysignUiState.txHash,
                    approveTransactionHash = keysignUiState.approveTxHash,
                    transactionLink = keysignUiState.txLink,
                    approveTransactionLink = keysignUiState.approveTxLink,
                    progressLink = keysignUiState.swapProgressLink,
                    onComplete = viewModel::complete,
                    onBack = keysignViewModel::navigateToHome,
                    transactionTypeUiModel = keysignUiState.transactionUiModel,
                    onAddToAddressBook = keysignViewModel::navigateToAddressBook,
                    showSaveToAddressBook = keysignUiState.showSaveToAddressBook,
                    hasBackClick = false,
                    dappMetadata = dappMetadata,
                    coinLogoRes = keysignViewModel.coinLogoRes,
                )
            }

            is Error -> {
                val error = state as Error
                val title: String
                val description: String?
                val errorState: ErrorState
                val buttonText: String
                var rawError: String? = null
                when (error.errorType) {
                    is JoinKeysignError.WrongVaultShare -> {
                        title = stringResource(R.string.error_same_vault_share_title)
                        description = stringResource(R.string.error_same_vault_share_description)
                        errorState = ErrorState.WARNING
                        buttonText =
                            stringResource(
                                R.string.join_keysign_error_wrong_vault_share_try_again_button
                            )
                    }

                    is JoinKeysignError.WrongVault,
                    is JoinKeysignError.MissingRequiredVault -> {
                        title = stringResource(R.string.error_vault_not_loaded_title)
                        description = stringResource(R.string.error_vault_not_loaded_description)
                        errorState = ErrorState.WARNING
                        buttonText =
                            stringResource(
                                R.string.join_keysign_error_wrong_vault_share_try_again_button
                            )
                    }

                    else -> {
                        val rawMessage = error.errorType.message.asString()
                        val signingError = resolveSigningError(rawMessage)
                        title = signingError.title
                        description = signingError.description
                        errorState = signingError.errorState
                        rawError = rawMessage.ifBlank { null }
                        buttonText = stringResource(R.string.try_again)
                    }
                }
                ErrorView(
                    title = title,
                    description = description,
                    errorState = errorState,
                    rawError = rawError,
                    buttonUiModel =
                        ErrorViewButtonUiModel(text = buttonText, onClick = viewModel::tryAgain),
                )
            }
        }
    }
}

@Composable
private fun QbtcClaimConsentContent(
    btcAddress: String,
    qbtcAddress: String,
    onApprove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.qbtc_claim_cosign_description),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )

        UiSpacer(size = 24.dp)

        val shape = RoundedCornerShape(16.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier =
                Modifier.fillMaxWidth()
                    .clip(shape)
                    .background(Theme.v2.colors.backgrounds.surface1)
                    .border(1.dp, Theme.v2.colors.border.light, shape)
                    .padding(16.dp),
        ) {
            QbtcClaimAccountRow(
                logo = painterResource(R.drawable.bitcoin),
                label = stringResource(R.string.qbtc_claim_cosign_btc_account),
                address = btcAddress,
            )
            QbtcClaimAccountRow(
                logo = painterResource(R.drawable.qbtc),
                label = stringResource(R.string.qbtc_claim_cosign_qbtc_account),
                address = qbtcAddress,
            )
        }

        UiSpacer(weight = 1f)

        VsButton(
            label = stringResource(R.string.qbtc_claim_cosign_approve),
            onClick = onApprove,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
    }
}

@Composable
private fun QbtcClaimAccountRow(logo: Painter, label: String, address: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = logo,
                contentDescription = null,
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(50)),
            )
            Text(
                text = label,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )
        }
        Text(
            text = address,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}

@Composable
private fun JoinKeysignScreen(
    title: String,
    isError: Boolean,
    applyDefaultPaddings: Boolean = true,
    fullScreen: Boolean = false,
    onBack: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    if (fullScreen) {
        content()
    } else {
        V2Scaffold(
            onBackClick = onBack.takeIf { !isError },
            rightIcon = R.drawable.big_close.takeIf { isError },
            onRightIconClick = onBack.takeIf { isError },
            title = title,
            applyDefaultPaddings = applyDefaultPaddings,
            content = content,
        )
    }
}

@Preview
@Composable
private fun JoinKeysignViewPreview() {
    JoinKeysignScreen(
        title = stringResource(R.string.keysign),
        isError = true,
        content = {
            ErrorView(
                title = stringResource(R.string.signing_error_please_try_again_s, ""),
                buttonUiModel =
                    ErrorViewButtonUiModel(text = stringResource(R.string.try_again), onClick = {}),
            )
        },
    )
}
