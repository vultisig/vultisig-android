package com.vultisig.wallet.ui.models.keysign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.KeysignVerify
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.tss.PullTssMessagesUseCase
import com.vultisig.wallet.ui.models.TransactionUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.KeysignResponse
import tss.ServiceImpl
import tss.Tss
import vultisig.keysign.v1.CustomMessagePayload
import wallet.core.jni.CoinType
import java.math.BigInteger
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

internal sealed class KeysignState {
    data object CreatingInstance : KeysignState()
    data object KeysignECDSA : KeysignState()
    data object KeysignEdDSA : KeysignState()
    data object KeysignFinished : KeysignState()
    data class Error(val errorMessage: String) : KeysignState()
}


internal sealed interface TransactionTypeUiModel {
    data class Send(val transactionUiModel: TransactionUiModel) : TransactionTypeUiModel
    data class Swap(val swapTransactionUiModel: SwapTransactionUiModel) : TransactionTypeUiModel
    data class Deposit(val depositTransactionUiModel: DepositTransactionUiModel) : TransactionTypeUiModel
    data class SignMessage(val model: SignMessageTransactionUiModel) : TransactionTypeUiModel
}

internal class KeysignViewModel(
    val vault: Vault,
    private val keysignCommittee: List<String>,
    private val serverUrl: String,
    private val sessionId: String,
    private val encryptionKeyHex: String,
    private val messagesToSign: List<String>,
    private val keyType: TssKeyType,
    private val keysignPayload: KeysignPayload?,
    private val customMessagePayload: CustomMessagePayload?,
    private val thorChainApi: ThorChainApi,
    private val evmApiFactory: EvmApiFactory,
    private val broadcastTx: BroadcastTxUseCase,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val navigator: Navigator<Destination>,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
    val transactionTypeUiModel: TransactionTypeUiModel?,
    private val pullTssMessages: PullTssMessagesUseCase,
) : ViewModel() {
    val currentState: MutableStateFlow<KeysignState> =
        MutableStateFlow(KeysignState.CreatingInstance)
    val txHash = MutableStateFlow("")
    val txLink = MutableStateFlow("")
    val swapProgressLink = MutableStateFlow<String?>(null)

    private var tssInstance: ServiceImpl? = null
    private var tssMessenger: TssMessenger? = null
    private val localStateAccessor: LocalStateAccessor = LocalStateAccessor(vault)

    private var pullTssMessagesJob: Job? = null

    private val signatures: MutableMap<String, tss.KeysignResponse> = mutableMapOf()
    private var featureFlag: FeatureFlagJson? = null

    private var isNavigateToHome: Boolean = false

    fun startKeysign() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                signAndBroadcast()
            }
        }
    }

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    private suspend fun signAndBroadcast() {
        Timber.d("Start to SignAndBroadcast")
        currentState.value = KeysignState.CreatingInstance
        try {
            featureFlag = featureFlagApi.getFeatureFlag()
            val isEncryptionGcm = featureFlag?.isEncryptGcmEnabled == true

            tssMessenger = TssMessenger(
                serverUrl,
                sessionId,
                encryptionKeyHex,
                sessionApi,
                viewModelScope,
                encryption,
                isEncryptionGcm,
            )
            tssInstance = Tss.newService(tssMessenger, localStateAccessor, false)
                ?: error("Failed to create TSS instance")

            messagesToSign.forEach { message ->
                Timber.d("signing message: $message")
                signMessageWithRetry(tssInstance!!, message, 1)
            }

            Timber.d("All messages signed, broadcasting transaction")

            broadcastTransaction()
            checkThorChainTxResult()

            currentState.value = KeysignState.KeysignFinished
            isNavigateToHome = true

            pullTssMessagesJob?.cancel()
        } catch (e: Exception) {
            Timber.e(e)
            currentState.value = KeysignState.Error( e.message ?: "Unknown error")
        }
    }

    private suspend fun checkThorChainTxResult() {
        val chainSpecific = keysignPayload?.blockChainSpecific
        if (chainSpecific !is BlockChainSpecific.THORChain)
            return
        if (!chainSpecific.isDeposit)
            return
        val transactionDetail = thorChainApi.getTransactionDetail(txHash.value)

        // https://docs.cosmos.network/v0.46/building-modules/errors.html#registration
        if (transactionDetail.code != null && transactionDetail.codeSpace != null) {
            throw Exception(transactionDetail.rawLog)
        }
    }

    private suspend fun signMessageWithRetry(service: ServiceImpl, message: String, attempt: Int) {
        val keysignVerify = KeysignVerify(serverUrl, sessionId, sessionApi)
        try {
            Timber.d("signMessageWithRetry: $message, attempt: $attempt")
            val msgHash = message.md5()
            this.tssMessenger?.setMessageID(msgHash)
            Timber.d("signMessageWithRetry: msgHash: $msgHash")

            val isEncryptionGcm = featureFlag?.isEncryptGcmEnabled == true
            pullTssMessagesJob = viewModelScope.launch {
                pullTssMessages(
                    serverUrl = serverUrl,
                    sessionId = sessionId,
                    localPartyId = vault.localPartyID,
                    hexEncryptionKey = encryptionKeyHex,
                    isEncryptionGcm = isEncryptionGcm,
                    messageId = null,
                    service = tssInstance!!,
                ).collect()
            }

            val keysignReq = tss.KeysignRequest()
            keysignReq.localPartyKey = vault.localPartyID
            keysignReq.keysignCommitteeKeys = keysignCommittee.joinToString(",")
            keysignReq.messageToSign = Base64.getEncoder().encodeToString(message.toHexBytes())
            keysignReq.derivePath =
                (keysignPayload?.coin?.coinType ?: CoinType.ETHEREUM).derivationPath()

            val keysignResp = when (keyType) {
                TssKeyType.ECDSA -> {
                    keysignReq.pubKey = vault.pubKeyECDSA
                    currentState.value = KeysignState.KeysignECDSA
                    service.keysignECDSA(keysignReq)
                }

                TssKeyType.EDDSA -> {
                    keysignReq.pubKey = vault.pubKeyEDDSA
                    currentState.value = KeysignState.KeysignEdDSA
                    service.keysignEdDSA(keysignReq)
                }
            }
            if (keysignResp.r.isNullOrEmpty() || keysignResp.s.isNullOrEmpty()) {
                throw Exception("Failed to sign message")
            }
            calculateCustomMessageSignature(keysignResp)
            this.signatures[message] = keysignResp
            keysignVerify.markLocalPartyKeysignComplete(message, keysignResp)

            pullTssMessagesJob?.cancel()

            delay(1.seconds)
        } catch (e: Exception) {
            pullTssMessagesJob?.cancel()
            Timber.tag("KeysignViewModel")
                .d("signMessageWithRetry error: %s", e.stackTraceToString())
            val resp = keysignVerify.checkKeysignComplete(message)
            resp?.let {
                this.signatures[message] = it
                return
            }
            if (attempt > 3) {
                throw e
            }
            signMessageWithRetry(service, message, attempt + 1)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun calculateCustomMessageSignature(keysignResp: KeysignResponse) {
        if (customMessagePayload == null)
            return
        txHash.value = keysignResp.getSignature().toHexString()
    }

    private suspend fun broadcastTransaction() {
        val payload = keysignPayload ?: return

        var nonceAcc = BigInteger.ZERO

        val approvePayload = payload.approvePayload
        if (approvePayload != null) {
            val signedTransaction = THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                .getSignedApproveTransaction(
                    approvePayload,
                    payload,
                    signatures
                )

            val evmApi = evmApiFactory.createEvmApi(payload.coin.chain)
            evmApi.sendTransaction(signedTransaction.rawTransaction)

            nonceAcc++
        }

        val signedTx = SigningHelper.getSignedTransaction(
            keysignPayload = payload,
            vault = vault,
            signatures = signatures,
            nonceAcc = nonceAcc
        )

        val txHash = broadcastTx(
            chain = payload.coin.chain,
            tx = signedTx,
        )

        Timber.d("transaction hash: $txHash")
        if (txHash != null) {
            this.txHash.value = txHash
            txLink.value = explorerLinkRepository.getTransactionLink(payload.coin.chain, txHash)
            swapProgressLink.value = explorerLinkRepository.getSwapProgressLink(txHash, payload.swapPayload)
        }
    }

    fun navigateToHome() {
        viewModelScope.launch {
            if (isNavigateToHome) {
                navigator.navigate(
                    Destination.Home(),
                    NavigationOptions(
                        clearBackStack = true
                    )
                )
            } else {
                navigator.navigate(Destination.Back)
            }
        }
    }
}
