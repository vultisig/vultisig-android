package com.vultisig.wallet.ui.models.qbtc

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.ClaimableUtxo
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.LoadClaimableQbtcUtxosUseCase
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimAmountFormatter
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBlockedReason
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimConfig
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimError
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimLoadResult
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimOrchestrator
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimPhase
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimRunInput
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcProofService
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.qbtc.QbtcClaimFastVaultRoundRunner
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Immutable
internal data class QbtcClaimUtxoUiModel(
    val key: String,
    val shortId: String,
    val subtitleBlockHeight: Long?,
    val qbtcAmount: String,
    val btcAmount: String,
)

@Immutable
internal sealed interface QbtcClaimUiState {
    data object Loading : QbtcClaimUiState

    data class Blocked(val reason: QbtcClaimBlockedReason) : QbtcClaimUiState

    data class Selecting(
        val utxos: List<QbtcClaimUtxoUiModel>,
        val selectedKeys: Set<String>,
        val totalSelectedSats: Long,
        val canConfirm: Boolean,
        val isAllSelected: Boolean,
    ) : QbtcClaimUiState

    data class Signing(val phase: QbtcClaimPhase) : QbtcClaimUiState

    data class Done(val txHash: String, val totalSats: Long, val explorerUrl: String) :
        QbtcClaimUiState

    data class Failed(val error: QbtcClaimError) : QbtcClaimUiState
}

@HiltViewModel
internal class QbtcClaimViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val loadClaimableUtxos: LoadClaimableQbtcUtxosUseCase,
    private val proofService: QbtcProofService,
    private val fastVaultRoundRunner: QbtcClaimFastVaultRoundRunner,
    private val explorerLinkRepository: ExplorerLinkRepository,
) : ViewModel() {

    private val vaultId = savedStateHandle.toRoute<Route.QbtcClaim>().vaultId

    val uiState = MutableStateFlow<QbtcClaimUiState>(QbtcClaimUiState.Loading)

    private var vault: Vault? = null
    private var btcCoin: Coin? = null
    private var qbtcCoin: Coin? = null
    private var claimable: List<ClaimableUtxo> = emptyList()
    private val selectedKeys = linkedSetOf<String>()
    private var claimJob: Job? = null

    init {
        load()
    }

    fun load() {
        uiState.value = QbtcClaimUiState.Loading
        viewModelScope.safeLaunch {
            val loadedVault = vaultRepository.get(vaultId)
            val btc = loadedVault?.coins?.firstOrNull { it.chain == Chain.Bitcoin }
            val qbtc = loadedVault?.coins?.firstOrNull { it.chain == Chain.Qbtc }
            if (loadedVault == null || btc == null || qbtc == null) {
                uiState.value =
                    QbtcClaimUiState.Blocked(
                        QbtcClaimBlockedReason.UtxoFetchFailed("Missing Bitcoin or QBTC account")
                    )
                return@safeLaunch
            }
            vault = loadedVault
            btcCoin = btc
            qbtcCoin = qbtc

            when (val result = loadClaimableUtxos(btc.address)) {
                is QbtcClaimLoadResult.Blocked -> {
                    uiState.value = QbtcClaimUiState.Blocked(result.reason)
                }
                is QbtcClaimLoadResult.Available -> {
                    claimable = result.utxos
                    // Preselect up to the cap, mirroring iOS's `prefix(maxClaimUtxos)`. Selecting
                    // everything unbounded would push the count past the cap on wallets with >50
                    // claimable UTXOs and permanently disable Confirm.
                    selectedKeys.clear()
                    selectedKeys.addAll(
                        claimable.take(QbtcClaimConfig.MAX_CLAIM_UTXOS).map { it.key() }
                    )
                    emitSelecting()
                }
            }
        }
    }

    fun toggle(key: String) {
        if (key in selectedKeys) {
            selectedKeys.remove(key)
        } else if (selectedKeys.size < QbtcClaimConfig.MAX_CLAIM_UTXOS) {
            selectedKeys.add(key)
        }
        emitSelecting()
    }

    fun confirm(fastVaultPassword: String) {
        // Ignore repeated taps while a claim is already running — a second run would
        // start a duplicate keysign + proof request and risk a double broadcast.
        if (claimJob?.isActive == true) return
        // Enforce the selection invariant in the ViewModel too, not only via the button state.
        if (!canConfirm()) return
        val vault = vault ?: return
        val btcCoin = btcCoin ?: return
        val qbtcCoin = qbtcCoin ?: return
        val selected = claimable.filter { it.key() in selectedKeys }

        val orchestrator =
            QbtcClaimOrchestrator(
                proofService = proofService,
                btcRoundRunner = fastVaultRoundRunner,
            )
        claimJob =
            viewModelScope.safeLaunch {
                // Phase collector lives only for this run, so it can't outlive the job and
                // update the UI out of band after completion.
                val collector = launch { orchestrator.phase.collect { onPhase(it) } }
                try {
                    orchestrator.run(
                        QbtcClaimRunInput(
                            vault = vault,
                            btcCoin = btcCoin,
                            qbtcCoin = qbtcCoin,
                            utxos = selected,
                            fastVaultPassword = fastVaultPassword,
                        )
                    )
                } finally {
                    collector.cancel()
                }
                onPhase(orchestrator.phase.value)
            }
    }

    fun retry() = load()

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun isFastVault(): Boolean = vault?.isFastVault() ?: true

    private fun onPhase(phase: QbtcClaimPhase) {
        uiState.value =
            when (phase) {
                QbtcClaimPhase.Idle,
                QbtcClaimPhase.SigningBtc,
                QbtcClaimPhase.GeneratingProofAndBroadcasting -> QbtcClaimUiState.Signing(phase)
                is QbtcClaimPhase.Done ->
                    QbtcClaimUiState.Done(
                        txHash = phase.result.txHashHex,
                        totalSats = phase.result.totalSatsClaimed,
                        explorerUrl =
                            explorerLinkRepository.getTransactionLink(
                                Chain.Qbtc,
                                phase.result.txHashHex,
                            ),
                    )
                is QbtcClaimPhase.Failed -> QbtcClaimUiState.Failed(phase.errorKind)
            }
    }

    private fun canConfirm(): Boolean =
        selectedKeys.isNotEmpty() && selectedKeys.size <= QbtcClaimConfig.MAX_CLAIM_UTXOS

    private fun emitSelecting() {
        val total = claimable.filter { it.key() in selectedKeys }.sumOf { it.amount }
        val cap = minOf(claimable.size, QbtcClaimConfig.MAX_CLAIM_UTXOS)
        uiState.value =
            QbtcClaimUiState.Selecting(
                utxos = claimable.map { it.toUiModel() },
                selectedKeys = selectedKeys.toSet(),
                totalSelectedSats = total,
                canConfirm = canConfirm(),
                isAllSelected = selectedKeys.size == cap,
            )
    }

    private fun ClaimableUtxo.key(): String = "$txid:$vout"

    private fun ClaimableUtxo.toUiModel(): QbtcClaimUtxoUiModel =
        QbtcClaimUtxoUiModel(
            key = key(),
            shortId = shortTxid(),
            subtitleBlockHeight = blockHeight,
            qbtcAmount = QbtcClaimAmountFormatter.formatQbtc(amount),
            btcAmount = QbtcClaimAmountFormatter.formatBtc(amount),
        )

    private fun ClaimableUtxo.shortTxid(): String =
        if (txid.length <= 14) "$txid:$vout" else "${txid.take(4)}…${txid.takeLast(4)}:$vout"
}
