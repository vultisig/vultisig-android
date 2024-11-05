package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GasFeeRefreshUiRepository @Inject constructor(
    private val gasFeeRepository: GasFeeRepository
) {
    val gasFee = MutableStateFlow<TokenValue?>(null)
    private val chain = MutableStateFlow<Chain?>(null)
    private val address = MutableStateFlow<String?>(null)

    fun updateAddress(address: Address) {
        this.chain.value = address.chain
        this.address.value = address.address
    }

    suspend fun refreshGasFee() {
        val chain = chain.value ?: return
        val address = address.value ?: return
        val gasFee = gasFeeRepository.getGasFee(chain, address)
        this.gasFee.update { gasFee }
    }
}