package com.vultisig.wallet.ui.models.keysign

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.OPERATION_CIRCLE_WITHDRAW
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.DeFiAction
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.CustomMessagePayloadRepo
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.MakeQrCodeBitmapShareFormat
import com.vultisig.wallet.data.usecases.QrShareInfo
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import vultisig.keysign.v1.CustomMessagePayload

@HiltViewModel
internal class KeysignShareViewModel
@Inject
constructor(
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val vaultRepository: VaultRepository,
    private val transactionRepository: TransactionRepository,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val depositTransaction: DepositTransactionRepository,
    private val customMessagePayloadRepo: CustomMessagePayloadRepo,
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat,
    private val generateQrBitmap: GenerateQrBitmap,
    private val snackbarFlow: SnackbarFlow,
) : ViewModel() {
    var vault: Vault? = null
    var keysignPayload: KeysignPayload? = null
    var customMessagePayload: CustomMessagePayload? = null

    val hasAllData: Boolean
        get() = vault != null && (keysignPayload != null || customMessagePayload != null)

    val amount = MutableStateFlow("")
    val toAmount = MutableStateFlow("")

    val qrBitmapPainter = MutableStateFlow<BitmapPainter?>(null)
    private var qrBitmap: Bitmap? = null
    internal val qrLink: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    internal val shareQrBitmap: StateFlow<Bitmap?>
        field = MutableStateFlow<Bitmap?>(null)

    private var loadQrPainterJob: Job? = null
    private var saveShareQrBitmapJob: Job? = null

    suspend fun loadTransaction(transactionId: TransactionId) {
        val transaction =
            transactionRepository.getTransaction(transactionId)
                ?: run {
                    Timber.e("Transaction not found: %s", transactionId)
                    throw IllegalStateException()
                }

        val vault = vaultRepository.get(transaction.vaultId)!!

        val pubKeyECDSA = vault.pubKeyECDSA
        val coin =
            vault.coins.find { it.id == transaction.token.id && it.chain.id == transaction.chainId }
                // The DeFi-only receipts are never vault coins — they are kept out of token
                // discovery so a position can't show up as a wallet holding — yet they are plain
                // bank denoms the vault can transfer. The account the send form was built from
                // carries the chain's own address and derived key, so the staged token is complete
                // and is the only place the receipt can be resolved from.
                ?: transaction.token.takeIf { Coins.isDefiOnly(it) }
                ?: error("Coin ${transaction.token.id} is not in vault ${transaction.vaultId}")

        this@KeysignShareViewModel.vault = vault
        amount.value = mapTokenValueToStringWithUnit(transaction.tokenValue)
        customMessagePayload = null
        keysignPayload =
            KeysignPayload(
                coin = coin,
                toAddress = transaction.dstAddress,
                toAmount = transaction.tokenValue.value,
                blockChainSpecific = transaction.blockChainSpecific,
                memo = transaction.memo,
                vaultPublicKeyECDSA = pubKeyECDSA,
                utxos = transaction.utxos,
                vaultLocalPartyID = vault.localPartyID,
                libType = vault.libType,
                wasmExecuteContractPayload = null,
            )
    }

    suspend fun loadSignMessageTx(id: String) {
        val dto =
            customMessagePayloadRepo.get(id)
                ?: run {
                    Timber.e("Sign message payload not found: %s", id)
                    throw IllegalStateException()
                }

        val vault = vaultRepository.get(dto.vaultId)!!

        this@KeysignShareViewModel.vault = vault
        keysignPayload = null
        customMessagePayload = dto.payload
    }

    suspend fun loadSwapTransaction(transactionId: TransactionId) {
        val transaction = swapTransactionRepository.getTransaction(transactionId)

        val vault =
            vaultRepository.get(transaction.vaultId)
                ?: error("Vault not found: ${transaction.vaultId}")

        val pubKeyECDSA = vault.pubKeyECDSA
        val srcToken = transaction.srcToken

        val specific = transaction.blockChainSpecific

        this@KeysignShareViewModel.vault = vault

        amount.value = mapTokenValueToStringWithUnit(transaction.srcTokenValue)
        toAmount.value = mapTokenValueToStringWithUnit(transaction.expectedDstTokenValue)

        customMessagePayload = null
        keysignPayload =
            when (transaction) {
                is SwapTransaction.RegularSwapTransaction -> {
                    var swapPayload: SwapPayload = transaction.payload
                    var dstToken = swapPayload.dstToken
                    if (
                        swapPayload is SwapPayload.ThorChain && dstToken.chain == Chain.BitcoinCash
                    ) {
                        dstToken = dstToken.adjustBitcoinCashAddressFormat()
                        swapPayload =
                            swapPayload.copy(data = swapPayload.data.copy(toCoin = dstToken))
                    }

                    KeysignPayload(
                        coin = srcToken,
                        toAddress = transaction.dstAddress,
                        toAmount = transaction.srcTokenValue.value,
                        blockChainSpecific = specific.blockChainSpecific,
                        swapPayload = swapPayload,
                        vaultPublicKeyECDSA = pubKeyECDSA,
                        utxos = specific.utxos,
                        vaultLocalPartyID = vault.localPartyID,
                        memo = transaction.memo,
                        approvePayload =
                            if (transaction.isApprovalRequired)
                                ERC20ApprovePayload(
                                    amount = transaction.srcTokenValue.value,
                                    spender = transaction.approveSpender,
                                    resetAllowanceFirst = transaction.resetAllowanceFirst,
                                )
                            else null,
                        libType = vault.libType,
                        wasmExecuteContractPayload = null,
                    )
                }
            }
    }

    private fun Coin.adjustBitcoinCashAddressFormat() =
        copy(address = address.replace("bitcoincash:", ""))

    suspend fun loadDepositTransaction(transactionId: TransactionId) {
        val transaction = depositTransaction.getTransaction(transactionId)

        val vault =
            vaultRepository.get(transaction.vaultId)
                ?: error("Vault not found: ${transaction.vaultId}")

        val pubKeyECDSA = vault.pubKeyECDSA
        val srcToken = transaction.srcToken

        val specific = transaction.blockChainSpecific

        this@KeysignShareViewModel.vault = vault

        // A TON message batch signs from the chain's native coin whatever the deposit names for
        // display: its value is TON, the vault's ed25519 key is read off that coin, and every
        // co-signer builds the transfer from the batch only when the payload coin is the fee coin.
        // The amount echoes the first message so the sidecar and the signed bytes agree.
        val signTon = transaction.signTon
        val payloadCoin =
            if (signTon != null && !srcToken.isNativeToken) {
                vault.coins.firstOrNull { it.chain == srcToken.chain && it.isNativeToken }
                    ?: error("Native ${srcToken.chain} coin not found for a TON message batch")
            } else {
                srcToken
            }
        val toAmount =
            signTon?.tonMessages?.firstOrNull()?.amount?.toBigIntegerOrNull()
                ?: transaction.srcTokenValue.value

        customMessagePayload = null
        keysignPayload =
            KeysignPayload(
                coin = payloadCoin,
                toAddress = transaction.dstAddress,
                toAmount = toAmount,
                blockChainSpecific = specific,
                vaultPublicKeyECDSA = pubKeyECDSA,
                utxos = transaction.utxos,
                vaultLocalPartyID = vault.localPartyID,
                memo = transaction.memo,
                libType = vault.libType,
                wasmExecuteContractPayload = transaction.wasmExecuteContractPayload,
                signDirect = transaction.signDirect,
                signSolana = transaction.signSolana,
                signTon = signTon,
                defiAction =
                    if (transaction.operation == OPERATION_CIRCLE_WITHDRAW) {
                        DeFiAction.CIRCLE_USDC_WITHDRAW
                    } else {
                        DeFiAction.NONE
                    },
            )
    }

    fun loadQrPainter(address: String): Job {
        // Drop everything built for the previous QR before rendering the new one, so the link, the
        // painter and the share image never describe different sessions. Cancelling on the main
        // thread is enough: `withContext` discards a result whose job was cancelled meanwhile.
        loadQrPainterJob?.cancel()
        saveShareQrBitmapJob?.cancel()
        qrLink.value = null
        qrBitmap = null
        qrBitmapPainter.value = null
        shareQrBitmap.value?.recycle()
        shareQrBitmap.value = null

        return viewModelScope
            .launch {
                val bitmap =
                    withContext(Dispatchers.IO) {
                        generateQrBitmap(
                            address,
                            Color.White.toArgb(),
                            Color.Transparent.toArgb(),
                            null,
                        )
                    }
                qrBitmap = bitmap
                qrBitmapPainter.value =
                    BitmapPainter(bitmap.asImageBitmap(), filterQuality = FilterQuality.None)
                qrLink.value = address
            }
            .also { loadQrPainterJob = it }
    }

    internal fun shareQRCode(activity: Context) {
        val qrBitmap = shareQrBitmap.value ?: return
        activity.share(qrBitmap, shareFileName(requireNotNull(vault), ShareType.SEND))
    }

    /** Copies the join link the QR encodes, so another device can open it without scanning. */
    internal fun copyQrLink(context: Context) {
        val link = qrLink.value ?: return
        VsClipboardService.copy(context, link)
        viewModelScope.launch {
            snackbarFlow.showMessage(UiText.StringResource(R.string.keysign_share_qr_link_copied))
        }
    }

    internal fun saveShareQrBitmap(context: Context, color: Int, info: QrShareInfo, logo: Bitmap) {
        // Cancel any in-flight render so rapid `qrShareInfo` updates (painter then icons) can't
        // race and let a stale bitmap overwrite a fresher one. The previous rendered bitmap is
        // recycled on replacement to avoid leaks when icons/amounts change repeatedly.
        saveShareQrBitmapJob?.cancel()
        saveShareQrBitmapJob =
            viewModelScope.launch {
                val bitmap = qrBitmap ?: return@launch
                // Allocation happens on IO; if cancellation races with the render, `withContext`'s
                // prompt-cancellation guarantee discards the returned bitmap before we can recycle
                // it. Recycle in-place and return null so the native memory is released instead of
                // waiting for GC.
                val rendered =
                    withContext(Dispatchers.IO) {
                        val bmp =
                            try {
                                makeQrCodeBitmapShareFormat(context, bitmap, color, logo, info)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // A failed share render must not crash the keysign flow; the share
                                // bitmap simply stays unset and the user can retry.
                                Timber.e(e, "Failed to render share QR bitmap")
                                null
                            }
                        if (bmp != null && !isActive) {
                            bmp.recycle()
                            null
                        } else {
                            bmp
                        }
                    } ?: return@launch
                val previous = shareQrBitmap.value
                shareQrBitmap.value = rendered
                if (previous != null && previous !== rendered) previous.recycle()
            }
    }
}
