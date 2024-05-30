package com.vultisig.wallet.presenter.keysign

import androidx.lifecycle.ViewModel
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
internal class KeysignShareViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val transactionRepository: TransactionRepository,
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
                memo = null,
                swapPayload = null,
                approvePayload = null,
                vaultPublicKeyECDSA = pubKeyECDSA,
                utxos = transaction.utxos,
                vaultLocalPartyID = vault.localPartyID,
            )
        }
    }

}