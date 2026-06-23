package com.vultisig.wallet.ui.models.governance

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovProposal
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovTallyResult
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingConfig
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import vultisig.keysign.v1.TransactionType

/** On-chain governance vote option — [wireName] is the value embedded in the QBTC_VOTE memo. */
enum class VoteOption(val wireName: String, @StringRes val labelRes: Int) {
    YES("YES", R.string.governance_vote_yes),
    NO("NO", R.string.governance_vote_no),
    ABSTAIN("ABSTAIN", R.string.governance_vote_abstain),
    NO_WITH_VETO("NO_WITH_VETO", R.string.governance_vote_no_with_veto);

    companion object {
        /** Maps the cosmos `VOTE_OPTION_*` enum string to a [VoteOption], or `null` if unknown. */
        fun fromWire(value: String?): VoteOption? =
            when (value) {
                "VOTE_OPTION_YES" -> YES
                "VOTE_OPTION_NO" -> NO
                "VOTE_OPTION_ABSTAIN" -> ABSTAIN
                "VOTE_OPTION_NO_WITH_VETO" -> NO_WITH_VETO
                else -> null
            }
    }
}

enum class ProposalStatus {
    Active,
    Passed,
    Rejected,
}

/** Per-option share of the final tally, as fractions (0..1) for the bar and pre-formatted %s. */
@Immutable
data class TallyUi(
    val yesFraction: Float = 0f,
    val noFraction: Float = 0f,
    val abstainFraction: Float = 0f,
    val vetoFraction: Float = 0f,
    val yesPercent: String = "0%",
    val noPercent: String = "0%",
    val abstainPercent: String = "0%",
    val vetoPercent: String = "0%",
    val hasVotes: Boolean = false,
    // The winning option + its share, for the bold result headline on the card.
    val leadingOption: VoteOption? = null,
    val leadingPercent: String = "",
)

@Immutable
data class ProposalUi(
    val id: String,
    // Raw on-chain title; blank proposals fall back to a numbered label in the UI layer.
    val title: String,
    val summary: String,
    val status: ProposalStatus,
    val timeLabel: UiText,
    val isVotable: Boolean,
    val tally: TallyUi,
    val yourVote: VoteOption?,
)

@Immutable
data class GovernanceUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedTab: ProposalStatus = ProposalStatus.Active,
    val active: List<ProposalUi> = emptyList(),
    val passed: List<ProposalUi> = emptyList(),
    val rejected: List<ProposalUi> = emptyList(),
    val voteSheetProposal: ProposalUi? = null,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
) {
    fun proposalsFor(tab: ProposalStatus): List<ProposalUi> =
        when (tab) {
            ProposalStatus.Active -> active
            ProposalStatus.Passed -> passed
            ProposalStatus.Rejected -> rejected
        }
}

/**
 * Lists QBTC governance proposals (active / passed / rejected) and stages a vote into the existing
 * deposit → verify → keysign pipeline. Only active proposals whose voting window is still open are
 * votable — closed proposals are read-only (the chain rejects late votes), which is the structural
 * fix for the "voting on an elapsed proposal fails" report.
 *
 * A vote is encoded as a `QBTC_VOTE:<OPTION>:<proposalId>` memo on a
 * [TransactionType.TRANSACTION_TYPE_VOTE] Cosmos `BlockChainSpecific`; signing lives in the shared
 * keysign flow, so this view-model only fetches proposals + the user's existing votes and persists
 * the staged transaction before navigating to [Route.VerifyDeposit].
 */
@HiltViewModel
internal class GovernanceViewModel
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val cosmosApiFactory: CosmosApiFactory,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val navigator: Navigator<Destination>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private var vaultId: String? = null
    private var voterCoin: Coin? = null
    private var userPickedTab = false
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(GovernanceUiState())
    val state = _state.asStateFlow()

    /**
     * Idempotent — re-invoking with the same vault is a no-op so recomposition doesn't re-fetch.
     */
    fun setData(vaultId: String) {
        if (this.vaultId == vaultId) return
        this.vaultId = vaultId
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        val vaultId = vaultId ?: return
        _state.update {
            val firstLoad = it.active.isEmpty() && it.passed.isEmpty() && it.rejected.isEmpty()
            it.copy(isRefreshing = isRefresh, isLoading = !isRefresh && firstLoad)
        }
        // Cancel any in-flight load so a slow earlier response can't land last and pin stale data.
        loadJob?.cancel()
        loadJob =
            viewModelScope.safeLaunch(
                onError = {
                    _state.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = R.string.governance_error_load_proposals.asUiText(),
                        )
                    }
                }
            ) {
                val coin = resolveCoin(vaultId)
                val api = cosmosApiFactory.createCosmosApi(Chain.Qbtc)
                val now = Instant.now()

                val (activeResult, passedResult, rejectedResult) =
                    withContext(ioDispatcher) {
                        val a = async { runCatching { api.getGovProposals(STATUS_VOTING_PERIOD) } }
                        val p = async { runCatching { api.getGovProposals(STATUS_PASSED) } }
                        val r = async { runCatching { api.getGovProposals(STATUS_REJECTED) } }
                        Triple(a.await(), p.await(), r.await())
                    }

                // A total failure (offline / RPC down) must surface as an error, not a misleading
                // empty "no proposals" state. Once any list is on screen, transient per-status
                // failures degrade to empty — mirroring CosmosStakingPositionsViewModel's
                // primary/auxiliary split.
                val results = listOf(activeResult, passedResult, rejectedResult)
                val current = _state.value
                val hadData =
                    current.active.isNotEmpty() ||
                        current.passed.isNotEmpty() ||
                        current.rejected.isNotEmpty()
                if (results.all { it.isFailure } && !hadData) {
                    throw results.firstNotNullOf { it.exceptionOrNull() }
                }
                val active = activeResult.getOrDefault(emptyList())
                val passed = passedResult.getOrDefault(emptyList())
                val rejected = rejectedResult.getOrDefault(emptyList())

                // The voter's current vote — only for active proposals. The chain prunes votes once
                // a proposal closes, so closed-proposal lookups would 404 on every load.
                val votes =
                    withContext(ioDispatcher) {
                        active
                            .map { proposal ->
                                async {
                                    val option =
                                        runCatching { api.getGovVote(proposal.id, coin.address) }
                                            .getOrNull()
                                            ?.options
                                            ?.firstOrNull()
                                            ?.option
                                    proposal.id to VoteOption.fromWire(option)
                                }
                            }
                            .awaitAll()
                            .toMap()
                    }

                val activeUi = active.map { it.toUi(ProposalStatus.Active, now, votes[it.id]) }
                val passedUi = passed.map { it.toUi(ProposalStatus.Passed, now, yourVote = null) }
                val rejectedUi =
                    rejected.map { it.toUi(ProposalStatus.Rejected, now, yourVote = null) }

                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        active = activeUi,
                        passed = passedUi,
                        rejected = rejectedUi,
                        selectedTab =
                            if (userPickedTab) it.selectedTab
                            else firstNonEmptyTab(activeUi, passedUi, rejectedUi),
                    )
                }
            }
    }

    private suspend fun resolveCoin(vaultId: String): Coin {
        voterCoin?.let {
            return it
        }
        val vault =
            withContext(ioDispatcher) { vaultRepository.get(vaultId) } ?: error("Vault not found")
        val coin =
            vault.coins.firstOrNull { it.chain == Chain.Qbtc && it.isNativeToken }
                ?: error("QBTC coin not enabled for this vault")
        voterCoin = coin
        return coin
    }

    fun onTabSelected(tab: ProposalStatus) {
        userPickedTab = true
        _state.update { it.copy(selectedTab = tab) }
    }

    fun openVoteSheet(proposal: ProposalUi) {
        if (!proposal.isVotable) return
        _state.update { it.copy(voteSheetProposal = proposal) }
    }

    fun dismissVoteSheet() {
        _state.update { it.copy(voteSheetProposal = null) }
    }

    fun castVote(proposalId: String, option: VoteOption) {
        val vaultId = vaultId ?: return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.safeLaunch(
            onError = {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        voteSheetProposal = null,
                        error = R.string.governance_error_submit_vote.asUiText(),
                    )
                }
            }
        ) {
            val coin = resolveCoin(vaultId)
            // QBTC's verified `min_tx_fee` floor. min_gas_price is 0 on the chain, so this flat
            // amount is the entire fee — and `CosmosFeeService` returns 7_500 for QBTC, which
            // overpays this 800 floor ~9x (see the QBTC entry in CosmosStakingConfig).
            val feeAmount = BigInteger.valueOf(CosmosStakingConfig.feeAmountFor(Chain.Qbtc))
            val gasFee = TokenValue(value = feeAmount, token = coin)

            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain = Chain.Qbtc,
                    address = coin.address,
                    token = coin,
                    gasFee = gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                    transactionType = TransactionType.TRANSACTION_TYPE_VOTE,
                )

            val tx =
                DepositTransaction(
                    id = UUID.randomUUID().toString(),
                    vaultId = vaultId,
                    srcToken = coin,
                    srcAddress = coin.address,
                    dstAddress = "",
                    // Memo contract parsed by QBTCTransactionHelper.buildMsgVote.
                    memo = "QBTC_VOTE:${option.wireName}:$proposalId",
                    srcTokenValue = TokenValue(value = BigInteger.ZERO, token = coin),
                    estimatedFees = gasFee,
                    estimateFeesFiat = "",
                    blockChainSpecific = specific.blockChainSpecific,
                )

            depositTransactionRepository.addTransaction(tx)
            _state.update { it.copy(isSubmitting = false, voteSheetProposal = null) }
            navigator.route(Route.VerifyDeposit(vaultId = vaultId, transactionId = tx.id))
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun firstNonEmptyTab(
        active: List<ProposalUi>,
        passed: List<ProposalUi>,
        rejected: List<ProposalUi>,
    ): ProposalStatus =
        when {
            active.isNotEmpty() -> ProposalStatus.Active
            passed.isNotEmpty() -> ProposalStatus.Passed
            rejected.isNotEmpty() -> ProposalStatus.Rejected
            else -> ProposalStatus.Active
        }

    private fun CosmosGovProposal.toUi(
        status: ProposalStatus,
        now: Instant,
        yourVote: VoteOption?,
    ): ProposalUi {
        val end = runCatching { votingEndTime?.let { Instant.parse(it) } }.getOrNull()
        val votingOpen = end?.isAfter(now) ?: false
        return ProposalUi(
            id = id,
            title = title.orEmpty(),
            summary = summary.orEmpty(),
            status = status,
            timeLabel = timeLabel(status, end, now, votingOpen),
            // Votable only while the proposal is in its voting period AND the window is still open,
            // so a status that lags the on-chain clock can't offer a guaranteed-reject vote.
            isVotable = status == ProposalStatus.Active && votingOpen,
            tally = finalTallyResult.toTally(),
            yourVote = yourVote,
        )
    }

    private fun timeLabel(
        status: ProposalStatus,
        end: Instant?,
        now: Instant,
        votingOpen: Boolean,
    ): UiText {
        if (end == null) return UiText.Empty
        if (status != ProposalStatus.Active || !votingOpen) {
            return R.string.governance_ended.asUiText(DATE_FORMAT.format(end))
        }
        val left = Duration.between(now, end)
        return when {
            left.toDays() >= 1 -> R.string.governance_ends_in_days.asUiText(left.toDays())
            left.toHours() >= 1 -> R.string.governance_ends_in_hours.asUiText(left.toHours())
            left.toMinutes() >= 1 -> R.string.governance_ends_in_minutes.asUiText(left.toMinutes())
            else -> R.string.governance_ending_soon.asUiText()
        }
    }

    private fun CosmosGovTallyResult?.toTally(): TallyUi {
        val yes = this?.yesCount?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val no = this?.noCount?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val abstain = this?.abstainCount?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val veto = this?.noWithVetoCount?.toBigIntegerOrNull() ?: BigInteger.ZERO
        val total = yes + no + abstain + veto
        if (total <= BigInteger.ZERO) return TallyUi()

        val totalDecimal = total.toBigDecimal()
        fun fraction(value: BigInteger): Float =
            value.toBigDecimal().divide(totalDecimal, 6, RoundingMode.HALF_UP).toFloat()
        fun percent(fraction: Float): String = "${(fraction * 100).roundToInt()}%"

        val fy = fraction(yes)
        val fn = fraction(no)
        val fa = fraction(abstain)
        val fv = fraction(veto)
        val leading =
            listOf(
                    VoteOption.YES to fy,
                    VoteOption.NO to fn,
                    VoteOption.ABSTAIN to fa,
                    VoteOption.NO_WITH_VETO to fv,
                )
                .maxByOrNull { it.second }
        return TallyUi(
            yesFraction = fy,
            noFraction = fn,
            abstainFraction = fa,
            vetoFraction = fv,
            yesPercent = percent(fy),
            noPercent = percent(fn),
            abstainPercent = percent(fa),
            vetoPercent = percent(fv),
            hasVotes = true,
            leadingOption = leading?.first,
            leadingPercent = leading?.let { percent(it.second) } ?: "",
        )
    }

    private companion object {
        const val STATUS_VOTING_PERIOD = 2
        const val STATUS_PASSED = 3
        const val STATUS_REJECTED = 4

        val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())
    }
}
