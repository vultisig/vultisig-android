package com.vultisig.wallet.ui.screens.governance

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiAlertDialog
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.models.governance.GovernanceUiState
import com.vultisig.wallet.ui.models.governance.GovernanceViewModel
import com.vultisig.wallet.ui.models.governance.ProposalStatus
import com.vultisig.wallet.ui.models.governance.ProposalUi
import com.vultisig.wallet.ui.models.governance.TallyUi
import com.vultisig.wallet.ui.models.governance.VoteOption
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

/**
 * QBTC governance proposals, hosted inside `ChainDashboardScreen`'s content slot (so it owns its
 * own header). Only open Active proposals expose a vote CTA, which stages a transaction into the
 * shared Verify-Deposit → keysign flow.
 */
@Composable
internal fun GovernanceScreen(vaultId: String, viewModel: GovernanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(vaultId) { viewModel.setData(vaultId = vaultId) }

    GovernanceContent(
        state = state,
        onTabSelected = viewModel::onTabSelected,
        onRefresh = viewModel::refresh,
        onVoteClick = viewModel::openVoteSheet,
    )

    val sheetProposal = state.voteSheetProposal
    if (sheetProposal != null) {
        VoteBottomSheet(
            proposal = sheetProposal,
            isSubmitting = state.isSubmitting,
            onDismiss = viewModel::dismissVoteSheet,
            onConfirm = { option -> viewModel.castVote(sheetProposal.id, option) },
        )
    }

    val error = state.error
    if (error != null) {
        UiAlertDialog(
            title = stringResource(R.string.governance_error_title),
            text = error.asString(),
            onDismiss = viewModel::dismissError,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GovernanceContent(
    state: GovernanceUiState,
    onTabSelected: (ProposalStatus) -> Unit,
    onRefresh: () -> Unit,
    onVoteClick: (ProposalUi) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Theme.v2.colors.backgrounds.primary)) {
            GovernanceHeader(
                activeCount = state.active.size,
                passedCount = state.passed.size,
                rejectedCount = state.rejected.size,
            )

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                VsTabGroup(index = state.selectedTab.ordinal) {
                    ProposalStatus.entries.forEach { status ->
                        tab {
                            VsTab(
                                label = stringResource(status.labelRes),
                                onClick = { onTabSelected(status) },
                            )
                        }
                    }
                }
            }

            val proposals = state.proposalsFor(state.selectedTab)
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                when {
                    state.isLoading && proposals.isEmpty() -> GovernanceSkeletonList()
                    proposals.isEmpty() -> GovernanceEmptyState()
                    else ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(proposals, key = { it.id }) { proposal ->
                                ProposalCard(
                                    proposal = proposal,
                                    onVoteClick = { onVoteClick(proposal) },
                                )
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun GovernanceHeader(activeCount: Int, passedCount: Int, rejectedCount: Int) {
    val teal = Theme.v2.colors.buttons.primary
    val subtitle =
        listOfNotNull(
                activeCount
                    .takeIf { it > 0 }
                    ?.let { stringResource(R.string.governance_count_active, it) },
                passedCount
                    .takeIf { it > 0 }
                    ?.let { stringResource(R.string.governance_count_passed, it) },
                rejectedCount
                    .takeIf { it > 0 }
                    ?.let { stringResource(R.string.governance_count_rejected, it) },
            )
            .joinToString(" · ")
            .ifEmpty { stringResource(R.string.governance_subtitle_default) }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(listOf(teal.copy(alpha = 0.14f), Color.Transparent))
                )
                .border(1.dp, teal.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(Theme.v2.colors.gradients.primary),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(
                drawableResId = R.drawable.ic_megaphone,
                size = 22.dp,
                tint = Theme.v2.colors.text.button.dark,
            )
        }
        UiSpacer(size = 12.dp)
        Column {
            Text(
                text = stringResource(R.string.governance),
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = subtitle,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
            )
        }
    }
}

@Composable
private fun ProposalCard(proposal: ProposalUi, onVoteClick: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Theme.v2.colors.backgrounds.surface1)
                .border(1.dp, Theme.v2.colors.border.normal, RoundedCornerShape(20.dp))
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        proposal.title.ifBlank {
                            stringResource(R.string.governance_proposal_number, proposal.id)
                        },
                    style = Theme.brockmann.body.l.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                UiSpacer(size = 4.dp)
                val timeText = proposal.timeLabel.asString()
                Text(
                    text = "#${proposal.id}" + if (timeText.isNotEmpty()) "  ·  $timeText" else "",
                    style = Theme.brockmann.supplementary.captionSmall,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
            UiSpacer(size = 8.dp)
            StatusPill(proposal.status)
        }

        if (proposal.summary.isNotBlank()) {
            Text(
                text = proposal.summary,
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultHeadline(proposal.tally)
            TallyBar(tally = proposal.tally, modifier = Modifier.fillMaxWidth())
            TallyLegend(proposal.tally)
        }

        proposal.yourVote?.let { YourVoteBadge(it) }

        if (proposal.isVotable) {
            VsButton(
                label =
                    stringResource(
                        if (proposal.yourVote != null) R.string.governance_change_vote
                        else R.string.governance_vote
                    ),
                variant = VsButtonVariant.Primary,
                size = VsButtonSize.Medium,
                onClick = onVoteClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResultHeadline(tally: TallyUi) {
    val option = tally.leadingOption ?: return
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = tally.leadingPercent,
            style = Theme.brockmann.headings.title2,
            color = option.voteColor(),
        )
        UiSpacer(size = 6.dp)
        Text(
            text = stringResource(option.labelRes),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun StatusPill(status: ProposalStatus) {
    val color = status.statusColor()
    Row(
        modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        UiSpacer(size = 6.dp)
        Text(
            text = stringResource(status.labelRes),
            style = Theme.brockmann.supplementary.captionSmall,
            color = color,
        )
    }
}

@Composable
private fun TallyBar(tally: TallyUi, modifier: Modifier = Modifier) {
    if (!tally.hasVotes) {
        Box(
            modifier =
                modifier
                    .height(12.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Theme.v2.colors.backgrounds.surface3)
        )
        return
    }
    Row(modifier = modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        TallySegment(tally.yesFraction, Theme.v2.colors.alerts.success)
        TallySegment(tally.noFraction, Theme.v2.colors.alerts.error)
        TallySegment(tally.abstainFraction, Theme.v2.colors.text.tertiary)
        TallySegment(tally.vetoFraction, Theme.v2.colors.alerts.warning)
    }
}

@Composable
private fun RowScope.TallySegment(fraction: Float, color: Color) {
    if (fraction <= 0f) return
    Box(
        modifier =
            Modifier.fillMaxHeight()
                .weight(fraction)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
    )
}

@Composable
private fun TallyLegend(tally: TallyUi) {
    if (!tally.hasVotes) {
        Text(
            text = stringResource(R.string.governance_no_votes),
            style = Theme.brockmann.supplementary.caption,
            color = Theme.v2.colors.text.tertiary,
        )
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendItem(
            Modifier.weight(1f),
            Theme.v2.colors.alerts.success,
            stringResource(R.string.governance_vote_yes),
            tally.yesPercent,
        )
        LegendItem(
            Modifier.weight(1f),
            Theme.v2.colors.alerts.error,
            stringResource(R.string.governance_vote_no),
            tally.noPercent,
        )
        LegendItem(
            Modifier.weight(1f),
            Theme.v2.colors.text.tertiary,
            stringResource(R.string.governance_vote_abstain),
            tally.abstainPercent,
        )
        LegendItem(
            Modifier.weight(1f),
            Theme.v2.colors.alerts.warning,
            stringResource(R.string.governance_tally_veto),
            tally.vetoPercent,
        )
    }
}

@Composable
private fun LegendItem(modifier: Modifier, color: Color, label: String, percent: String) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            UiSpacer(size = 4.dp)
            Text(
                text = label,
                style = Theme.brockmann.supplementary.captionSmall,
                color = Theme.v2.colors.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = percent,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
        )
    }
}

@Composable
private fun YourVoteBadge(option: VoteOption) {
    val color = option.voteColor()
    Row(
        modifier =
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiIcon(drawableResId = R.drawable.ic_check, size = 14.dp, tint = color)
        UiSpacer(size = 6.dp)
        Text(
            text = stringResource(R.string.governance_you_voted, stringResource(option.labelRes)),
            style = Theme.brockmann.supplementary.caption,
            color = color,
        )
    }
}

@Composable
private fun VoteBottomSheet(
    proposal: ProposalUi,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (VoteOption) -> Unit,
) {
    var selected by remember(proposal.id) { mutableStateOf(proposal.yourVote ?: VoteOption.YES) }

    V2BottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.governance_cast_vote),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val title =
                proposal.title.ifBlank {
                    stringResource(R.string.governance_proposal_number, proposal.id)
                }
            Text(
                text = title,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            UiSpacer(size = 4.dp)
            VoteOption.entries.forEach { option ->
                VoteOptionRow(
                    option = option,
                    isSelected = selected == option,
                    enabled = !isSubmitting,
                    onClick = { selected = option },
                )
            }
            UiSpacer(size = 8.dp)
            VsButton(
                label = stringResource(R.string.governance_confirm_vote),
                variant = VsButtonVariant.Primary,
                size = VsButtonSize.Medium,
                state = if (isSubmitting) VsButtonState.Disabled else VsButtonState.Enabled,
                onClick = { onConfirm(selected) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VoteOptionRow(
    option: VoteOption,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = option.voteColor()
    val borderColor = if (isSelected) color else Theme.v2.colors.border.normal
    val background = if (isSelected) color.copy(alpha = 0.08f) else Color.Transparent
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(background)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            UiSpacer(size = 10.dp)
            Text(
                text = stringResource(option.labelRes),
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
        }
        if (isSelected) {
            UiIcon(drawableResId = R.drawable.ic_check, size = 18.dp, tint = color)
        }
    }
}

@Composable
private fun GovernanceSkeletonList() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) { ProposalSkeletonCard() }
    }
}

@Composable
private fun ProposalSkeletonCard() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Theme.v2.colors.backgrounds.surface1)
                .border(1.dp, Theme.v2.colors.border.normal, RoundedCornerShape(20.dp))
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UiPlaceholderLoader(modifier = Modifier.fillMaxWidth(0.65f).height(18.dp))
        UiPlaceholderLoader(modifier = Modifier.fillMaxWidth(0.9f).height(12.dp))
        UiPlaceholderLoader(modifier = Modifier.fillMaxWidth().height(10.dp))
    }
}

@Composable
private fun GovernanceEmptyState() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            UiIcon(
                drawableResId = R.drawable.ic_megaphone,
                size = 28.dp,
                tint = Theme.v2.colors.text.tertiary,
            )
            UiSpacer(size = 8.dp)
            Text(
                text = stringResource(R.string.governance_empty),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
            )
        }
    }
}

@get:StringRes
private val ProposalStatus.labelRes: Int
    get() =
        when (this) {
            ProposalStatus.Active -> R.string.governance_status_active
            ProposalStatus.Passed -> R.string.governance_status_passed
            ProposalStatus.Rejected -> R.string.governance_status_rejected
        }

@Composable
private fun ProposalStatus.statusColor(): Color =
    when (this) {
        ProposalStatus.Active -> Theme.v2.colors.buttons.primary
        ProposalStatus.Passed -> Theme.v2.colors.alerts.success
        ProposalStatus.Rejected -> Theme.v2.colors.alerts.error
    }

@Composable
private fun VoteOption.voteColor(): Color =
    when (this) {
        VoteOption.YES -> Theme.v2.colors.alerts.success
        VoteOption.NO -> Theme.v2.colors.alerts.error
        VoteOption.ABSTAIN -> Theme.v2.colors.text.tertiary
        VoteOption.NO_WITH_VETO -> Theme.v2.colors.alerts.warning
    }

@Preview
@Composable
private fun GovernanceContentPreview() {
    val tally =
        TallyUi(
            yesFraction = 0.72f,
            noFraction = 0.18f,
            abstainFraction = 0.06f,
            vetoFraction = 0.04f,
            yesPercent = "72%",
            noPercent = "18%",
            abstainPercent = "6%",
            vetoPercent = "4%",
            hasVotes = true,
            leadingOption = VoteOption.YES,
            leadingPercent = "72%",
        )
    GovernanceContent(
        state =
            GovernanceUiState(
                selectedTab = ProposalStatus.Active,
                active =
                    listOf(
                        ProposalUi(
                            id = "12",
                            title = "Increase the block reward to incentivise validators",
                            summary =
                                "Raise the per-block reward from 1.0 to 1.5 QBTC over 30 days.",
                            status = ProposalStatus.Active,
                            timeLabel = UiText.DynamicString("Ends in 2d"),
                            isVotable = true,
                            tally = tally,
                            yourVote = VoteOption.YES,
                        )
                    ),
                passed =
                    listOf(
                        ProposalUi(
                            id = "1",
                            title = "Claim UTXO to reserve",
                            summary = "Claim more UTXO to reserve",
                            status = ProposalStatus.Passed,
                            timeLabel = UiText.DynamicString("Ended 22 Jun 2026"),
                            isVotable = false,
                            tally = tally,
                            yourVote = null,
                        )
                    ),
            ),
        onTabSelected = {},
        onRefresh = {},
        onVoteClick = {},
    )
}
