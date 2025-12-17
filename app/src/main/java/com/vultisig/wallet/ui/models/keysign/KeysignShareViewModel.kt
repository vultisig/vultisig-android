package com.vultisig.wallet.ui.models.keysign

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
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
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import vultisig.keysign.v1.CustomMessagePayload
import javax.inject.Inject

@HiltViewModel
internal class KeysignShareViewModel @Inject constructor(
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val vaultRepository: VaultRepository,
    private val transactionRepository: TransactionRepository,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val depositTransaction: DepositTransactionRepository,
    private val customMessagePayloadRepo: CustomMessagePayloadRepo,
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat,
    private val generateQrBitmap: GenerateQrBitmap,
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
    private val shareQrBitmap = MutableStateFlow<Bitmap?>(null)

    fun loadTransaction(transactionId: TransactionId) {
        runBlocking {
            val transaction = transactionRepository.getTransaction(transactionId)

            val vault = vaultRepository.get(transaction.vaultId)!!

            val pubKeyECDSA = vault.pubKeyECDSA
            val coin =
                vault.coins.find { it.id == transaction.token.id && it.chain.id == transaction.chainId }!!

            this@KeysignShareViewModel.vault = vault
            amount.value = mapTokenValueToStringWithUnit(transaction.tokenValue)
            customMessagePayload = null
            keysignPayload = KeysignPayload(
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
    }

    fun loadSignMessageTx(id: String) {
        runBlocking {
            val dto = customMessagePayloadRepo.get(id)
                ?: return@runBlocking

            val vault = vaultRepository.get(dto.vaultId)!!

            this@KeysignShareViewModel.vault = vault
            keysignPayload = null
            customMessagePayload = dto.payload
        }
    }

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    fun loadSwapTransaction(transactionId: TransactionId) {
        runBlocking {
            val transaction = swapTransactionRepository.getTransaction(transactionId)

            val vault = vaultRepository.get(transaction.vaultId)!!

            val pubKeyECDSA = vault.pubKeyECDSA
            val srcToken = transaction.srcToken

            val specific = transaction.blockChainSpecific

            this@KeysignShareViewModel.vault = vault

            amount.value = mapTokenValueToStringWithUnit(transaction.srcTokenValue)
            toAmount.value = mapTokenValueToStringWithUnit(transaction.expectedDstTokenValue)

            customMessagePayload = null
            keysignPayload = when (transaction) {

                is SwapTransaction.RegularSwapTransaction -> {
                    var swapPayload: SwapPayload = transaction.payload
                    var dstToken = swapPayload.dstToken
                    if (swapPayload is SwapPayload.ThorChain && dstToken.chain == Chain.BitcoinCash) {
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
                        approvePayload = if (transaction.isApprovalRequired) ERC20ApprovePayload(
                            amount = transaction.srcTokenValue.value,
                            spender = transaction.dstAddress,
                        )
                        else null,
                        libType = vault.libType,
                        wasmExecuteContractPayload = null,
                    )
                }
            }
        }
    }

    private fun Coin.adjustBitcoinCashAddressFormat() = copy(
        address = address.replace(
            "bitcoincash:", ""
        )
    )

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    fun loadDepositTransaction(transactionId: TransactionId) {
        runBlocking {
            val transaction = depositTransaction.getTransaction(transactionId)

            val vault = vaultRepository.get(transaction.vaultId)!!

            val pubKeyECDSA = vault.pubKeyECDSA
            val srcToken = transaction.srcToken

            val specific = transaction.blockChainSpecific

            this@KeysignShareViewModel.vault = vault

            customMessagePayload = null
            keysignPayload = KeysignPayload(
                coin = srcToken,
                toAddress = transaction.dstAddress,
                toAmount = transaction.srcTokenValue.value,
                blockChainSpecific = specific,
                vaultPublicKeyECDSA = pubKeyECDSA,
                utxos = emptyList(),
                vaultLocalPartyID = vault.localPartyID,
                memo = transaction.memo,
                libType = vault.libType,
                wasmExecuteContractPayload = transaction.wasmExecuteContractPayload,
                defiAction = if (transaction.operation == OPERATION_CIRCLE_WITHDRAW) {
                    DeFiAction.CIRCLE_USDC_WITHDRAW
                } else {
                    DeFiAction.NONE
                }
            )
        }
    }

    fun loadQrPainter(address: String) =
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val qrBitmap = generateQrBitmap(address, Color.White, Color.Transparent, null)
                this@KeysignShareViewModel.qrBitmap = qrBitmap
                val bitmapPainter = BitmapPainter(
                    qrBitmap.asImageBitmap(), filterQuality = FilterQuality.None
                )
                qrBitmapPainter.value = bitmapPainter
            }
        }

    internal fun shareQRCode(activity: Context) {
        val qrBitmap = shareQrBitmap.value ?: return
        activity.share(
            qrBitmap,
            shareFileName(
                requireNotNull(vault),
                ShareType.SEND
            )
        )
    }

    internal fun saveShareQrBitmap(
        context: Context,
        color: Int,
        title: String,
        description: String,
        logo: Bitmap,
    ) = viewModelScope.launch {
        val bitmap = qrBitmap ?: return@launch
        val qrBitmap = withContext(Dispatchers.IO) {
            makeQrCodeBitmapShareFormat(context, bitmap, color, logo, title, description)
        }
        shareQrBitmap.value = qrBitmap
    }
}