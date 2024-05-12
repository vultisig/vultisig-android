package com.vultisig.wallet.presenter.keysign

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.google.gson.Gson
import com.vultisig.wallet.chains.AtomHelper
import com.vultisig.wallet.chains.EvmHelper
import com.vultisig.wallet.chains.KujiraHelper
import com.vultisig.wallet.chains.MayaChainHelper
import com.vultisig.wallet.chains.SolanaHelper
import com.vultisig.wallet.chains.THORCHainHelper
import com.vultisig.wallet.chains.utxoHelper
import com.vultisig.wallet.common.md5
import com.vultisig.wallet.common.toHexBytes
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.mediator.MediatorService
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.tss.LocalStateAccessor
import com.vultisig.wallet.tss.TssKeyType
import com.vultisig.wallet.tss.TssMessagePuller
import com.vultisig.wallet.tss.TssMessenger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.ServiceImpl
import tss.Tss
import java.util.Base64

enum class KeysignState {
    CreatingInstance,
    KeysignECDSA,
    KeysignEdDSA,
    KeysignFinished,
    ERROR
}

internal class KeysignViewModel(
    private val vault: Vault,
    private val keysignCommittee: List<String>,
    private val serverAddress: String,
    private val sessionId: String,
    private val encryptionKeyHex: String,
    private val messagesToSign: List<String>,
    private val keyType: TssKeyType,
    private val keysignPayload: KeysignPayload,
    private val gson: Gson,
    private val thorChainApi: ThorChainApi,
    private val blockChairApi: BlockChairApi,
) {
    private var tssInstance: ServiceImpl? = null
    private val tssMessenger: TssMessenger =
        TssMessenger(serverAddress, sessionId, encryptionKeyHex)
    private val localStateAccessor: LocalStateAccessor = LocalStateAccessor(vault)
    val currentState: MutableState<KeysignState> = mutableStateOf(KeysignState.CreatingInstance)
    val errorMessage: MutableState<String> = mutableStateOf("")
    private var _messagePuller: TssMessagePuller? = null
    private val signatures: MutableMap<String, tss.KeysignResponse> = mutableMapOf()
    val txHash: MutableState<String> = mutableStateOf("")
    suspend fun startKeysign() {
        withContext(Dispatchers.IO) {
            signAndBroadcast()
        }
    }

    private suspend fun signAndBroadcast() {
        currentState.value = KeysignState.CreatingInstance
        try {
            this.tssInstance = Tss.newService(this.tssMessenger, this.localStateAccessor, false)
            this.tssInstance ?: run {
                throw Exception("Failed to create TSS instance")
            }
            _messagePuller = TssMessagePuller(
                service = this.tssInstance!!,
                hexEncryptionKey = encryptionKeyHex,
                serverAddress = serverAddress,
                localPartyKey = vault.localPartyID,
                sessionID = sessionId
            )
            this.messagesToSign.forEach() { message ->
                signMessageWithRetry(this.tssInstance!!, message, 1)
            }
            broadcastTransaction()
            currentState.value = KeysignState.KeysignFinished
            this._messagePuller?.stop()
        } catch (e: Exception) {
            Timber.e(e)
            currentState.value = KeysignState.ERROR
            errorMessage.value = e.message ?: "Unknown error"
        }
    }

    private suspend fun signMessageWithRetry(service: ServiceImpl, message: String, attempt: Int) {
        val keysignVerify = KeysignVerify(serverAddress, sessionId, gson)
        try {
            Timber.d("signMessageWithRetry: $message, attempt: $attempt")
            val msgHash = message.md5()

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
            Thread.sleep(1000) // backoff for 1 second
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
        try {
            val signedTransaction = getSignedTransaction()
            when (keysignPayload.coin.chain) {
                Chain.thorChain -> {
                    this.thorChainApi.broadcastTransaction(signedTransaction.rawTransaction)
                        ?.let {
                            txHash.value = it
                            Timber.d("transaction hash:$it")
                        }
                }

                Chain.bitcoin, Chain.bitcoinCash, Chain.litecoin, Chain.dogecoin, Chain.dash -> {
                    this.blockChairApi.broadcastTransaction(
                        keysignPayload.coin,
                        signedTransaction.rawTransaction
                    ).let {
                        txHash.value = it
                        Timber.d("transaction hash:$it")
                    }
                }

                else -> {
                    throw Exception("Not implemented")
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
            errorMessage.value = e.message ?: "Unknown error"
            currentState.value = KeysignState.ERROR
        }
    }

    private fun getSignedTransaction(): SignedTransactionResult {
        if (keysignPayload.swapPayload != null) {
            throw Exception("Not implemented")
        }
        if (keysignPayload.approvePayload != null) {
            throw Exception("Not implemented")
        }
        // we could define an interface to make the following more simpler,but I will leave it for later
        when (keysignPayload.coin.chain) {
            Chain.bitcoin, Chain.dash, Chain.bitcoinCash, Chain.dogecoin, Chain.litecoin -> {
                val utxo = utxoHelper.getHelper(vault, keysignPayload.coin.coinType)
                return utxo.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.thorChain -> {
                val thorHelper = THORCHainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                return thorHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.gaiaChain -> {
                val atomHelper = AtomHelper(vault.pubKeyECDSA, vault.hexChainCode)
                return atomHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.kujira -> {
                val kujiraHelper = KujiraHelper(vault.pubKeyECDSA, vault.hexChainCode)
                return kujiraHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.solana -> {
                val solanaHelper = SolanaHelper(vault.pubKeyEDDSA)
                return solanaHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.ethereum, Chain.avalanche, Chain.bscChain, Chain.cronosChain, Chain.blast, Chain.arbitrum, Chain.optimism, Chain.polygon, Chain.base -> {
                val evmHelper = EvmHelper(
                    keysignPayload.coin.coinType,
                    vault.pubKeyECDSA,
                    vault.hexChainCode
                )
                return evmHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.mayaChain -> {
                val mayaHelper = MayaChainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                return mayaHelper.getSignedTransaction(keysignPayload, signatures)
            }
        }
    }

    fun stopService(context: Context) {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stop MediatorService: Mediator service stopped")

    }
}
