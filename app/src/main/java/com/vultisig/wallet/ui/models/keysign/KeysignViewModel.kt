package com.vultisig.wallet.ui.models.keysign

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
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.TonApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.chains.helpers.CosmosHelper
import com.vultisig.wallet.data.chains.helpers.ERC20Helper
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.chains.helpers.SolanaHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.chains.helpers.TerraHelper
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.crypto.TonHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.tss.PullTssMessagesUseCase
import com.vultisig.wallet.data.wallet.OneInchSwap
import com.vultisig.wallet.ui.models.TransactionUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.ServiceImpl
import tss.Tss
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
}

internal class KeysignViewModel(
    val vault: Vault,
    private val keysignCommittee: List<String>,
    private val serverUrl: String,
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
    private val suiApi: SuiApi,
    private val tonApi: TonApi,
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
    val txLink = txHash.map {
        explorerLinkRepository.getTransactionLink(keysignPayload.coin.chain, it)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        ""
    )
    val swapProgressLink = txHash.map {
        explorerLinkRepository.getSwapProgressLink(it, keysignPayload.swapPayload)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), null
    )

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
        val chainSpecific = keysignPayload.blockChainSpecific
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
            if (keysignResp.r.isNullOrEmpty() || keysignResp.s.isNullOrEmpty()) {
                throw Exception("Failed to sign message")
            }
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

            Chain.GaiaChain, Chain.Kujira, Chain.Dydx, Chain.Osmosis, Chain.Terra,
            Chain.TerraClassic, Chain.Noble -> {
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

            Chain.Sui -> {
                suiApi.executeTransactionBlock(
                    signedTransaction.rawTransaction,
                    signedTransaction.signature ?: ""
                )
            }

            Chain.Ton -> {
                tonApi.broadcastTransaction(signedTransaction.rawTransaction)
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

        val chain = keysignPayload.coin.chain
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

            Chain.GaiaChain, Chain.Kujira, Chain.Dydx, Chain.Osmosis, Chain.Noble -> {
                return CosmosHelper(
                    coinType = chain.coinType,
                    denom = chain.feeUnit,
                    gasLimit = CosmosHelper.getChainGasLimit(chain),
                ).getSignedTransaction(keysignPayload, signatures)
            }

            Chain.TerraClassic, Chain.Terra -> {
                return TerraHelper(
                    coinType = chain.coinType,
                    denom = chain.feeUnit,
                    gasLimit = CosmosHelper.getChainGasLimit(chain),
                ).getSignedTransaction(keysignPayload, signatures)
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

            Chain.Sui -> {
                return SuiHelper.getSignedTransaction(
                    vault.pubKeyEDDSA,
                    keysignPayload, signatures
                )
            }

            Chain.Ton -> {
                return TonHelper.getSignedTransaction(
                    vaultHexPublicKey = vault.pubKeyEDDSA,
                    payload = keysignPayload,
                    signatures = signatures,
                )
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
