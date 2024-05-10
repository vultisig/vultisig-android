package com.vultisig.wallet.presenter.keysign

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.google.gson.Gson
import com.vultisig.wallet.chains.thorchainHelper
import com.vultisig.wallet.chains.utxoHelper
import com.vultisig.wallet.common.md5
import com.vultisig.wallet.common.toHexBytes
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.SignedTransactionResult
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.service.THORChainService
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

class KeysignViewModel(
    private val vault: Vault,
    private val keysignCommittee: List<String>,
    private val serverAddress: String,
    private val sessionId: String,
    private val encryptionKeyHex: String,
    private val messagesToSign: List<String>,
    private val keyType: TssKeyType,
    private val keysignPayload: KeysignPayload,
) {
    private var tssInstance: tss.ServiceImpl? = null
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
        val keysignVerify = KeysignVerify(serverAddress, sessionId)
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
            Log.d("KeysignViewModel", "signMessageWithRetry error: ${e.stackTraceToString()}")
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

    private fun broadcastTransaction() {
        val signedTransaction = getSignedTransaction()
        when (keysignPayload.coin.chain) {
            Chain.thorChain -> {
                THORChainService(Gson()).broadcastTransaction(signedTransaction.rawTransaction)
                    ?.let {
                        txHash.value = it
                    }
            }

            else -> {
                throw Exception("Not implemented")
            }
        }
    }

    private fun getSignedTransaction(): SignedTransactionResult {
        if (keysignPayload.swapPayload != null) {
            // TODO deal with swap
        }
        if (keysignPayload.approvePayload != null) {
            // TODO: deal with EVM ERC20 approval
        }
        when (keysignPayload.coin.chain) {
            Chain.bitcoin, Chain.dash, Chain.bitcoinCash, Chain.dogecoin, Chain.litecoin -> {
                val utxo = utxoHelper.getHelper(vault, keysignPayload.coin)
                return utxo.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.thorChain -> {
                val thorHelper = thorchainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                return thorHelper.getSignedTransaction(keysignPayload, signatures)
            }

            Chain.gaiaChain -> {
                throw Exception("Not implemented")
            }

            Chain.kujira -> {
                throw Exception("Not implemented")
            }

            Chain.solana -> {
                throw Exception("Not implemented")
            }

            Chain.ethereum, Chain.avalanche, Chain.bscChain, Chain.cronosChain, Chain.blast, Chain.arbitrum, Chain.optimism, Chain.polygon, Chain.base -> {
                throw Exception("Not implemented")
            }

            Chain.mayaChain -> {
                throw Exception("Not implemented")
            }
        }
    }
}
