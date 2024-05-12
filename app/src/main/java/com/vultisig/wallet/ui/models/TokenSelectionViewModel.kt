package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.chains.MayaChainHelper
import com.vultisig.wallet.chains.PublicKeyHelper
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.TssKeysignType
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.tss.TssKeyType
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import javax.inject.Inject

@Immutable
internal data class TokenSelectionUiModel(
    val tokens: List<TokenUiModel> = emptyList(),
)

@Immutable
internal data class TokenUiModel(
    val isEnabled: Boolean,
    val coin: Coin,
)

@HiltViewModel
internal class TokenSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultDb: VaultDB,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[Destination.SelectTokens.ARG_VAULT_ID])

    private val chainId: String =
        requireNotNull(savedStateHandle[Destination.SelectTokens.ARG_ACCOUNT_ID])

    val uiState = MutableStateFlow(TokenSelectionUiModel())

    init {
        loadChains()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun enableToken(coin: Coin) {
        viewModelScope.launch {
            commitVault(checkNotNull(vaultDb.select(vaultId)) {
                "No vault with $vaultId"
            }.let { vault ->
                when (coin.chain.TssKeysignType) {
                    TssKeyType.ECDSA -> {
                        val derivedPublicKey = PublicKeyHelper.getDerivedPublicKey(
                            vault.pubKeyECDSA,
                            vault.hexChainCode,
                            coin.coinType.derivationPath()
                        )
                        if (coin.chain == Chain.mayaChain) {
                            vault.copy(
                                coins = vault.coins + coin.copy(
                                    address = MayaChainHelper(
                                        vault.pubKeyECDSA,
                                        vault.hexChainCode
                                    ).getCoin()?.address ?: "",
                                    hexPublicKey = derivedPublicKey
                                )
                            )
                        } else {
                            val address = chainAccountAddressRepository.getAddress(
                                coin.coinType,
                                PublicKey(
                                    derivedPublicKey.hexToByteArray(),
                                    PublicKeyType.SECP256K1
                                )
                            )
                            vault.copy(
                                coins = vault.coins + coin.copy(
                                    address = address,
                                    hexPublicKey = derivedPublicKey
                                )
                            )
                        }
                    }

                    TssKeyType.EDDSA -> {
                        val address = chainAccountAddressRepository.getAddress(
                            coin.coinType,
                            PublicKey(vault.pubKeyEDDSA.hexToByteArray(), PublicKeyType.ED25519)
                        )
                        vault.copy(
                            coins = vault.coins + coin.copy(
                                address = address,
                                hexPublicKey = vault.pubKeyEDDSA
                            )
                        )
                    }
                }
            })
        }
    }

    fun disableToken(coin: Coin) {
        commitVault(checkNotNull(vaultDb.select(vaultId)) {
            "No vault with $vaultId"
        }.let { vault ->
            vault.copy(coins = vault.coins.filter { it.id != coin.id })
        })
    }

    private fun commitVault(vault: Vault) {
        vaultDb.upsert(vault)
        loadChains()
    }

    private fun loadChains() {
        viewModelScope.launch {
            val coins = Coins.SupportedCoins
                .filter { it.chain.raw == chainId }

            val enabledTokens = vaultDb.select(vaultId)
                ?.coins
                ?.map { it.id }
                ?.toSet()
                ?: emptySet()

            val chains = coins
                .map { coin ->
                    TokenUiModel(
                        isEnabled = coin.id in enabledTokens,
                        coin = coin,
                    )
                }

            uiState.update { it.copy(tokens = chains) }
        }
    }

}