package com.vultisig.wallet.ui.models

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.BanxaAssetName
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnRampViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
) : ViewModel() {
    
    private val vaultId: String = requireNotNull(savedStateHandle[Destination.ARG_VAULT_ID])
    private val chainId: String = requireNotNull(savedStateHandle[Destination.ARG_CHAIN_ID])
    
    private val _banxaUrl = MutableStateFlow<String?>(null)
    val banxaUrl = _banxaUrl.asStateFlow()
    
    fun openBanxaWebsite() {
        viewModelScope.launch {
            try {
                val vault = vaultRepository.get(vaultId) ?: run {
                    Timber.e("Vault not found: $vaultId")
                    return@launch
                }
                
                val chain = Chain.fromRaw(chainId)
                
                // Find the native coin for this chain
                val coin = vault.coins.find { 
                    it.chain == chain && it.isNativeToken 
                } ?: run {
                    Timber.e("Native coin not found for chain: $chainId")
                    return@launch
                }
                
                val url = getBuyURL(
                    address = coin.address,
                    blockChainCode = chain.BanxaAssetName,
                    coinType = chain.coinType.symbol,
                )
                
                // Set the URL to trigger the UI to open it
                _banxaUrl.value = url
                
            } catch (e: Exception) {
                Timber.e(e, "Error opening Banxa website")
            }
        }
    }
    
    fun onUrlOpened() {
        _banxaUrl.value = null
    }
    
    private fun getBuyURL(address: String, blockChainCode: String, coinType: String): String {
        val queryParams = buildString {
            append("?walletAddress=")
            append(Uri.encode(address))
            append("&blockchain=")
            append(Uri.encode(blockChainCode))
            append("&coinType=")
            append(Uri.encode(coinType))
        }
        return BANXA_URL + queryParams
    }

    companion object {
        val BANXA_URL = "https://vultisig.banxa.com/"
    }
}