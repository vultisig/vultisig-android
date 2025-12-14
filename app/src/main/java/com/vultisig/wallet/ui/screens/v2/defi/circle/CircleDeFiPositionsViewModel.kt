package com.vultisig.wallet.ui.screens.v2.defi.circle

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.CircleApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.asString
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
internal class CircleDeFiPositionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val scaCircleAccountRepository: ScaCircleAccountRepository,
    private val circleApi: CircleApi,
    private val evmApi: EvmApiFactory,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val snackbarFlow: SnackbarFlow,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private var vaultId: String = savedStateHandle.toRoute<Route.PositionCircle>().vaultId

    private val _state = MutableStateFlow(
        DefiUiModel(
            totalAmountPrice = "$0.00",
            isTotalAmountLoading = true,
            isBalanceVisible = true,
            supportEditChains = false,
            selectedTab = DeFiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner,
        )
    )

    val state: StateFlow<DefiUiModel> = _state.asStateFlow()

    init {
        loadCirclePositions()
    }

    private fun loadCirclePositions() {
        viewModelScope.launch {
            // Initial UI + Warning Status
            _state.update { currentState ->
                currentState.copy(
                    isTotalAmountLoading = true,
                    circleDefi = currentState.circleDefi.copy(
                        isLoading = true
                    )
                )
            }
            val hideWarning = withContext(Dispatchers.IO) {
                scaCircleAccountRepository.getCloseWarning()
            }
            _state.update { currentState ->
                currentState.copy(
                    circleDefi = currentState.circleDefi.copy(
                        closeWarning = hideWarning
                    )
                )
            }

            // Check account exists
            val addressSca = withContext(Dispatchers.IO) {
                scaCircleAccountRepository.getAccount(vaultId)
            }

            if (addressSca == null) {
                _state.update { currentState ->
                    currentState.copy(
                        totalAmountPrice = "$0",
                        isTotalAmountLoading = false,
                        circleDefi = currentState.circleDefi.copy(
                            isLoading = false,
                            totalDeposit = "0 USDC",
                            totalDepositCurrency = "$0"
                        )
                    )
                }
            }

            /*_state.update { currentState ->
                currentState.copy(
                    totalAmountPrice = "$5,432.10",
                    isTotalAmountLoading = false,
                    supportEditChains = true,
                    circleDefi = currentState.circleDefi.copy(
                        isLoading = false
                    )
                )
            } */
        }
    }

    fun onTabSelected(tab: String) {
        _state.update { currentState ->
            currentState.copy(selectedTab = tab)
        }
        loadTabData(tab)
    }

    private fun loadTabData(tab: String) {
        viewModelScope.launch {
            _state.update { it.copy(isTotalAmountLoading = true) }

            when (tab) {
                DeFiTab.DEPOSITED.displayName -> {
                    _state.update { currentState ->
                        currentState.copy(
                            totalAmountPrice = "$5,432.10",
                            isTotalAmountLoading = false
                        )
                    }
                }

                else -> {
                    _state.update { currentState ->
                        currentState.copy(
                            totalAmountPrice = "$0.00",
                            isTotalAmountLoading = false
                        )
                    }
                }
            }
        }
    }

    fun onBackClick() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun onClickCloseWarning() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                scaCircleAccountRepository.saveCloseWarning()
            }
            _state.update { currentState ->
                currentState.copy(
                    circleDefi = currentState.circleDefi.copy(
                        closeWarning = true,
                    )
                )
            }
        }
    }

    fun onCreateAccount() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val ethereumVaultAddress = getEvmVaultAddress()
                    circleApi.createScAccount(ethereumVaultAddress)
                    snackbarFlow.showMessage(
                        StringResource(R.string.circle_msca_account_created_success).asString(
                            context
                        )
                    )
                } catch (t: Throwable) {
                    Timber.e(t)
                    snackbarFlow.showMessage(
                        StringResource(R.string.circle_msca_account_created_failed).asString(context)
                    )
                }
            }
        }
    }

    private suspend fun getEvmVaultAddress(): String {
        val vault = vaultRepository.get(vaultId)
        if (vault != null) {
            val (address, _) = chainAccountAddressRepository.getAddress(
                Chain.Ethereum,
                vault
            )
            return address
        } else {
            Timber.e("CircleDeFiPositionsViewModel: Vault Null for $vaultId")
            error("CircleDeFiPositionsViewModel: Vault Null for $vaultId")
        }
    }
}