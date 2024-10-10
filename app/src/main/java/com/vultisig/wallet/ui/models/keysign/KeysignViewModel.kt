package com.vultisig.wallet.ui.models.keysign

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.KeysignVerify
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.chains.helpers.CosmosHelper
import com.vultisig.wallet.data.chains.helpers.ERC20Helper
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessagePuller
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.wallet.OneInchSwap
import com.vultisig.wallet.ui.models.TransactionUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.ServiceImpl
import tss.Tss
import wallet.core.jni.CoinType
import java.math.BigInteger
import java.util.Base64

enum class KeysignState {
    CreatingInstance,
    KeysignECDSA,
    KeysignEdDSA,
    KeysignFinished,
    ERROR
}


internal sealed interface TransitionTypeUiModel {
    data class Send(val transactionUiModel: TransactionUiModel) : TransitionTypeUiModel
    data class Swap(val swapTransactionUiModel: SwapTransactionUiModel) : TransitionTypeUiModel
    data class Deposit(val depositTransactionUiModel: DepositTransactionUiModel) : TransitionTypeUiModel
}

internal class KeysignViewModel(
    val vault: Vault,
    private val keysignCommittee: List<String>,
    private val serverAddress: String,
    private val sessionId: String,
    private val encryptionKeyHex: String,
    private val messagesToSign: List<String>,
    private val keyType: TssKeyType,
    private val keysignPayload: KeysignPayload,
    private val thorChainApi: ThorChainApi,
    private val blockChairApi: BlockChairApi,
    private val evmApiFactory: EvmApiFactory,
    private val mayaChainApi: MayaChainApi,
    private val cosmosApiFactory: CosmosApiFactory,
    private val solanaApi: SolanaApi,
    private val polkadotApi: PolkadotApi,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val navigator: Navigator<Destination>,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
    val transitionTypeUiModel: TransitionTypeUiModel?,
) : ViewModel() {
    private var tssInstance: ServiceImpl? = null
    private var tssMessenger: TssMessenger? = null
    private val localStateAccessor: LocalStateAccessor = LocalStateAccessor(vault)
    var isThorChainSwap =
        keysignPayload.swapPayload is SwapPayload.ThorChain
    val currentState: MutableStateFlow<KeysignState> =
        MutableStateFlow(KeysignState.CreatingInstance)
    val errorMessage: MutableState<String> = mutableStateOf("")
    private var _messagePuller: TssMessagePuller? = null
    private val signatures: MutableMap<String, tss.KeysignResponse> = mutableMapOf()
    val txHash = MutableStateFlow("")
    val txLink = txHash.map {
        explorerLinkRepository.getTransactionLink(keysignPayload.coin.chain, it)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        ""
    )
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
            this.featureFlag = featureFlagApi.getFeatureFlag()
            this.tssMessenger = TssMessenger(
                serverAddress,
                sessionId,
                encryptionKeyHex,
                sessionApi,
                viewModelScope,
                encryption,
                featureFlag?.isEncryptGcmEnabled == true,
            )
            this.tssInstance = Tss.newService(this.tssMessenger, this.localStateAccessor, false)
            this.tssInstance ?: run {
                throw Exception("Failed to create TSS instance")
            }
            _messagePuller = TssMessagePuller(
                service = this.tssInstance!!,
                hexEncryptionKey = encryptionKeyHex,
                serverAddress = serverAddress,
                localPartyKey = vault.localPartyID,
                sessionApi = sessionApi,
                sessionID = sessionId,
                encryption = encryption,
                isEncryptionGCM = featureFlag?.isEncryptGcmEnabled == true,
            )
            this.messagesToSign.forEach { message ->
                Timber.d("signing message: $message")
                signMessageWithRetry(this.tssInstance!!, message, 1)
            }
            broadcastTransaction()
            currentState.value = KeysignState.KeysignFinished
            isNavigateToHome = true
            this._messagePuller?.stop()
        } catch (e: Exception) {
            Timber.e(e)
            currentState.value = KeysignState.ERROR
            errorMessage.value = e.message ?: "Unknown error"
        }
    }

    private suspend fun signMessageWithRetry(service: ServiceImpl, message: String, attempt: Int) {
        val keysignVerify = KeysignVerify(serverAddress, sessionId, sessionApi)
        try {
            Timber.d("signMessageWithRetry: $message, attempt: $attempt")
            val msgHash = message.md5()
            this.tssMessenger?.setMessageID(msgHash)
            Timber.d("signMessageWithRetry: msgHash: $msgHash")
            this._messagePuller?.pullMessages(msgHash)
            val keysignReq = tss.KeysignRequest()
            keysignReq.localPartyKey = vault.localPartyID
            keysignReq.keysignCommitteeKeys = keysignCommittee.joinToString(",")
            keysignReq.messageToSign = Base64.getEncoder().encodeToString(message.toHexBytes())
            keysignReq.derivePath = keysignPayload.coin.coinType.derivationPath()
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
            this.signatures[message] = keysignResp
            keysignVerify.markLocalPartyKeysignComplete(message, keysignResp)
            this._messagePuller?.stop()
            delay(1000) // backoff for 1 second
        } catch (e: Exception) {
            this._messagePuller?.stop()
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

    private suspend fun broadcastTransaction() {
        val signedTransaction = getSignedTransaction()
        val txHash = when (keysignPayload.coin.chain) {
            Chain.ThorChain -> {
                thorChainApi.broadcastTransaction(signedTransaction.rawTransaction)
            }

            Chain.Bitcoin, Chain.BitcoinCash, Chain.Litecoin, Chain.Dogecoin, Chain.Dash -> {
                blockChairApi.broadcastTransaction(
                    keysignPayload.coin,
                    signedTransaction.rawTransaction
                )
            }

            Chain.Ethereum, Chain.CronosChain, Chain.Blast, Chain.BscChain, Chain.Avalanche,
            Chain.Base, Chain.Polygon, Chain.Optimism, Chain.Arbitrum, Chain.ZkSync -> {
                val evmApi = evmApiFactory.createEvmApi(keysignPayload.coin.chain)
                evmApi.sendTransaction(signedTransaction.rawTransaction)
            }

            Chain.Solana -> {
                solanaApi.broadcastTransaction(signedTransaction.rawTransaction)
            }

            Chain.GaiaChain, Chain.Kujira, Chain.Dydx -> {
                val cosmosApi = cosmosApiFactory.createCosmosApi(keysignPayload.coin.chain)
                cosmosApi.broadcastTransaction(signedTransaction.rawTransaction)
            }

            Chain.MayaChain -> {
                mayaChainApi.broadcastTransaction(signedTransaction.rawTransaction)
            }

            Chain.Polkadot -> {
                polkadotApi.broadcastTransaction(signedTransaction.rawTransaction)
                    ?: signedTransaction.transactionHash
            }
        }
        Timber.d("transaction hash: $txHash")
        if (txHash != null) {
            this.txHash.value = txHash
        }
    }

    private suspend fun getSignedTransaction(): SignedTransactionResult {
        val swapPayload = keysignPayload.swapPayload

        var nonceAcc = BigInteger.ZERO

        val approvePayload = keysignPayload.approvePayload
        if (approvePayload != null) {
            val signedTransaction = THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                .getSignedApproveTransaction(
                    approvePayload,
                    keysignPayload,
                    signatures
                )

            val evmApi = evmApiFactory.createEvmApi(keysignPayload.coin.chain)
            evmApi.sendTransaction(signedTransaction.rawTransaction)

            nonceAcc++
        }

        if (swapPayload != null && swapPayload !is SwapPayload.MayaChain) {
            when (swapPayload) {
                is SwapPayload.ThorChain -> {
                    return THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                        .getSignedTransaction(
                            swapPayload.data,
                            keysignPayload,
                            signatures,
                            nonceAcc
                        )
                }

                is SwapPayload.OneInch -> {
                    return OneInchSwap(vault.pubKeyECDSA, vault.hexChainCode)
                        .getSignedTransaction(
                            swapPayload.data,
                            keysignPayload,
                            signatures,
                            nonceAcc
                        )
                }

                else -> {}
            }
        }

        // we could define an interface to make the following more simpler,but I will leave it for later
        when (keysignPayload.coin.chain) {
            Chain.Bitcoin, Chain.Dash, Chain.BitcoinCash, Chain.Dogecoin, Chain.Litecoin -> {
                val utxo = UtxoHelper.getHelper(vault, keysignPayload.coin.coinType)
                return utxo.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.ThorChain -> {
                val thorHelper = ThorChainHelper.thor(vault.pubKeyECDSA, vault.hexChainCode)
                return thorHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.GaiaChain -> {
                val atomHelper = CosmosHelper(
                    coinType = CoinType.COSMOS,
                    denom = CosmosHelper.ATOM_DENOM,
                )
                return atomHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Kujira -> {
                val kujiraHelper = CosmosHelper(
                    coinType = CoinType.KUJIRA,
                    denom = CosmosHelper.KUJI_DENOM,
                )
                return kujiraHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Dydx -> {
                val dydxHelper = CosmosHelper(
                    coinType = CoinType.DYDX,
                    denom = CosmosHelper.DYDX_DENOM,
                )
                return dydxHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Solana -> {
                val solanaHelper = SolanaHelper(vault.pubKeyEDDSA)
                return solanaHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Ethereum, Chain.Avalanche, Chain.BscChain, Chain.CronosChain, Chain.Blast,
            Chain.Arbitrum, Chain.Optimism, Chain.Polygon, Chain.Base, Chain.ZkSync -> {
                if (keysignPayload.coin.isNativeToken) {
                    val evmHelper = EvmHelper(
                        keysignPayload.coin.coinType,
                        vault.pubKeyECDSA,
                        vault.hexChainCode
                    )
                    return evmHelper.getSignedTransaction(keysignPayload, signatures)
                } else {
                    val erc20Helper = ERC20Helper(
                        keysignPayload.coin.coinType,
                        vault.pubKeyECDSA,
                        vault.hexChainCode
                    )
                    return erc20Helper.getSignedTransaction(keysignPayload, signatures)
                }

            }

            Chain.MayaChain -> {
                val mayaHelper = ThorChainHelper.maya(vault.pubKeyECDSA, vault.hexChainCode)
                return mayaHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.Polkadot -> {
                val dotHelper = PolkadotHelper(vault.pubKeyEDDSA)
                return dotHelper.getSignedTransaction(keysignPayload, signatures)
            }
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
