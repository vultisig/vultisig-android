package com.vultisig.wallet.ui.models.referral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.models.referral.CreateReferralViewModel.Companion.DATE_FORMAT
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REFERRAL_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.CoinType
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

internal data class ReferralViewUiState(
    val referralFriendCode: String = "",
    val referralVaultCode: String = "",
    val referralVaultExpiration: String = "",
    val vaultName: String = "",
    val rewardsReferral: String = "",
    val isLoadingRewards: Boolean = true,
    val isLoadingExpirationDate: Boolean = true,
    val error: String = "",
)

@HiltViewModel
internal class ViewReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val referralRepository: ReferralCodeSettingsRepository,
    private val vaultRepository: VaultRepository,
    private val thorChainApi: ThorChainApi,
): ViewModel() {
    private var vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])
    private var vaultReferralCode: String = requireNotNull(savedStateHandle[ARG_REFERRAL_ID])

    val state = MutableStateFlow(ReferralViewUiState())

    init {
        onLoadReferralCodeInfo()
    }

    private fun onLoadReferralCodeInfo() {
        viewModelScope.launch {
            val (vaultName, friendReferralCode) = withContext(Dispatchers.IO) {
                val vaultDeferred =
                    async { vaultRepository.get(vaultId)?.name ?: "Default Vault" }
                val friendReferralDeferred =
                    async { referralRepository.getExternalReferralBy(vaultId) }
                    vaultDeferred.await() to friendReferralDeferred.await()
            }

            state.update {
                it.copy(
                    vaultName = vaultName,
                    referralVaultCode = vaultReferralCode,
                    referralFriendCode = friendReferralCode ?: "",
                )
            }

            if (vaultReferralCode.isEmpty()) {
                state.update {
                    it.copy(
                        isLoadingExpirationDate = false,
                        isLoadingRewards = false,
                    )
                }
                return@launch
            }

            try {
                val referralDetails = withContext(Dispatchers.IO) {
                    thorChainApi.getReferralCodeInfo(vaultReferralCode)
                }

                val rewards =
                    referralDetails.affiliateCollectorRune.toBigIntegerOrNull() ?: BigInteger.ZERO
                val ticker = CoinType.THORCHAIN.symbol

                val formattedRewards =
                    "${CoinType.THORCHAIN.toValue(rewards).toPlainString()} $ticker"

                state.update {
                    it.copy(
                        rewardsReferral = formattedRewards,
                        referralVaultExpiration = "",
                        isLoadingRewards = false,
                    )
                }

                val currentBlock = thorChainApi.getLastBlock()
                val expirationBlock = referralDetails.expireBlockHeight

                val expirationDate = calculateExpiration(expirationBlock, currentBlock)

                state.update {
                    it.copy(
                        isLoadingExpirationDate = false,
                        isLoadingRewards = false,
                        referralVaultExpiration = expirationDate,
                    )
                }
            } catch (t: Throwable) {
                Timber.e(t)
                state.update {
                    it.copy(
                        isLoadingRewards = false,
                        isLoadingExpirationDate = false,
                        error = "Can't load information",
                    )
                }
            }
        }
    }

    private fun calculateExpiration(expiryBlock: Long, currentBlock: Long): String {
        val remainingBlocks = maxOf(expiryBlock - currentBlock, 0)
        val remainingDays = remainingBlocks / BLOCKS_PER_DAY

        val nextYearDate = LocalDate.now().plusDays(remainingDays)

        val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.getDefault())
        return nextYearDate.format(formatter)
    }

    fun navigateToStoreFriendReferralBanner() {
        viewModelScope.launch {
            navigator.navigate(Destination.ReferralExternalEdition(vaultId))
        }
    }

    fun onDismissErrorDialog() {
        viewModelScope.launch {
            state.update {
                it.copy(error = "")
            }
        }
    }

    fun onClickedEditReferral() {
        viewModelScope.launch {
            val expiration = state.value.referralVaultExpiration
            navigator.navigate(Destination.ReferralVaultEdition(vaultId, vaultReferralCode, expiration))
        }
    }

    fun onVaultClicked() {
        viewModelScope.launch {
            navigator.navigate(Destination.ReferralListVault(vaultId))
        }
    }

    fun onCreateReferralClicked() {
        viewModelScope.launch {
            navigator.navigate(Destination.ReferralCreation(vaultId))
        }
    }

    fun onVaultSelected(vaultId: String) {
        viewModelScope.launch {
            if (vaultId.isNotEmpty()) {
                this@ViewReferralViewModel.vaultId = vaultId
            }
            val vaultReferral = withContext(Dispatchers.IO){
                referralRepository.getReferralCreatedBy(vaultId)
            }

            if (vaultReferral != null) {
                this@ViewReferralViewModel.vaultReferralCode = vaultReferral
                onLoadReferralCodeInfo()
            } else {
                try {
                    val remoteReferral = withContext(Dispatchers.IO) {
                        val coin = vaultRepository.get(vaultId)?.coins?.find {
                            it.chain.id == Chain.ThorChain.id && it.isNativeToken
                        } ?: error("Coin not found")
                        thorChainApi.getReferralCodesByAddress(coin.address)
                    }.firstOrNull()

                    remoteReferral?.let {
                        referralRepository.saveReferralCreated(vaultId, it)
                    } ?: run {
                        this@ViewReferralViewModel.vaultReferralCode = ""
                    }

                    onLoadReferralCodeInfo()
                } catch (t: Throwable) {
                    Timber.e(t)
                }
            }
        }
    }

    private companion object {
        const val BLOCKS_PER_DAY = (60 / 6) * 60 * 24
    }
}