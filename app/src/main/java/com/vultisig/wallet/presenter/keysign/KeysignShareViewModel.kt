package com.vultisig.wallet.presenter.keysign

import androidx.lifecycle.ViewModel
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.SwapTransaction
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.ERC20ApprovePayload
import com.vultisig.wallet.models.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
internal class KeysignShareViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val transactionRepository: TransactionRepository,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val depositTransaction: DepositTransactionRepository,
) : ViewModel() {
    var vault: Vault? = null
    var keysignPayload: KeysignPayload? = null

    fun loadTransaction(transactionId: TransactionId) {
        runBlocking {
            val transaction = transactionRepository.getTransaction(transactionId)
                .first()

            val vault = vaultRepository.get(transaction.vaultId)!!

            val pubKeyECDSA = vault.pubKeyECDSA
            val coin =
                vault.coins.find { it.id == transaction.tokenId && it.chain.id == transaction.chainId }!!

            this@KeysignShareViewModel.vault = vault
            keysignPayload = KeysignPayload(
                coin = coin,
                toAddress = transaction.dstAddress,
                toAmount = transaction.tokenValue.value,
                blockChainSpecific = transaction.blockChainSpecific,
                memo = transaction.memo,
                swapPayload = null,
                approvePayload = null,
                vaultPublicKeyECDSA = pubKeyECDSA,
                utxos = transaction.utxos,
                vaultLocalPartyID = vault.localPartyID,
            )
        }
    }

    fun loadSwapTransaction(transactionId: TransactionId) {
        runBlocking {
            val transaction = swapTransactionRepository.getTransaction(transactionId)

            val vault = vaultRepository.get(transaction.vaultId)!!

            val pubKeyECDSA = vault.pubKeyECDSA
            val srcToken = transaction.srcToken

            val specific = transaction.blockChainSpecific

            this@KeysignShareViewModel.vault = vault

            keysignPayload = KeysignPayload(
                coin = srcToken,
                toAddress = transaction.dstAddress,
                toAmount = transaction.srcTokenValue.value,
                blockChainSpecific = specific.blockChainSpecific,
                swapPayload = transaction.payload,
                vaultPublicKeyECDSA = pubKeyECDSA,
                utxos = specific.utxos,
                vaultLocalPartyID = vault.localPartyID,
                approvePayload = if (transaction.isApprovalRequired)
                    ERC20ApprovePayload(
                        amount = SwapTransaction.maxAllowance,
                        spender = transaction.dstAddress,
                    )
                else null,
                memo = null,
            )
        }
    }

    fun loadDepositTransaction(transactionId: TransactionId) {
        runBlocking {
            val transaction = depositTransaction.getTransaction(transactionId)

            val vault = vaultRepository.get(transaction.vaultId)!!

            val pubKeyECDSA = vault.pubKeyECDSA
            val srcToken = transaction.srcToken

            val specific = transaction.blockChainSpecific

            this@KeysignShareViewModel.vault = vault

            keysignPayload = KeysignPayload(
                coin = srcToken,
                toAddress = transaction.dstAddress,
                toAmount = transaction.srcTokenValue.value,
                blockChainSpecific = specific,
                vaultPublicKeyECDSA = pubKeyECDSA,
                utxos = emptyList(),
                vaultLocalPartyID = vault.localPartyID,
                memo = transaction.memo,
            )
        }
    }

}