package com.vultisig.wallet.ui.models

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
import com.vultisig.wallet.ui.navigation.Screen.VaultDetail.AddChainAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import javax.inject.Inject

internal data class ChainSelectionUiModel(
    val chains: List<ChainUiModel> = emptyList(),
)

internal data class ChainUiModel(
    val isEnabled: Boolean,
    val coin: Coin,
)

@HiltViewModel
internal class ChainSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultDb: VaultDB,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[AddChainAccount.ARG_VAULT_ID])

    val uiState = MutableStateFlow(ChainSelectionUiModel())

    init {
        loadChains()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun enableAccount(coin: Coin) {
        viewModelScope.launch {
            commitVault(checkNotNull(vaultDb.select(vaultId)) {
                "No vault with $vaultId"
            }.let { vault ->
                // When user add a coin to the vault , we need to derive the public key and address
                // and save the address and public key with the coin , thus don't need to derive in the future
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

    fun disableAccount(coin: Coin) {
        commitVault(checkNotNull(vaultDb.select(vaultId)) {
            "No vault with $vaultId"
        }.let { vault ->
            vault.copy(coins = vault.coins.filter { it.chain != coin.chain })
        })
    }

    private fun commitVault(vault: Vault) {
        vaultDb.upsert(vault)
        loadChains()
    }

    private fun loadChains() {
        viewModelScope.launch {
            val coins = Coins.SupportedCoins
            val enabledChains = vaultDb.select(vaultId)
                ?.coins
                ?.filter { it.isNativeToken }
                ?.map { it.chain }
                ?.toSet()
                ?: emptySet()

            val chains = coins
                .filter { it.isNativeToken }
                .map { coin ->
                    ChainUiModel(
                        isEnabled = coin.chain in enabledChains,
                        coin = coin,
                    )
                }

            uiState.update { it.copy(chains = chains) }
        }
    }

}