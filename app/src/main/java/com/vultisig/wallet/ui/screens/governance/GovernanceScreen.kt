package com.vultisig.wallet.ui.screens.governance

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.models.governance.GovernanceUiState
import com.vultisig.wallet.ui.models.governance.ProposalStatus
import com.vultisig.wallet.ui.models.governance.ProposalUi
import com.vultisig.wallet.ui.models.governance.TallyUi
import com.vultisig.wallet.ui.models.governance.VoteOption
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.asString

/**
 * Renders the QBTC governance proposals as `LazyColumn` items so they can sit inside the DeFi
 * (staking-positions) screen under a "Governance" tab next to "Staked". Proposals are grouped into
 * Active / Passed / Rejected sections; only open Active proposals expose a vote CTA.
 */
internal fun LazyListScope.governanceProposalItems(
    state: GovernanceUiState,
    onVoteClick: (ProposalUi) -> Unit,
) {
    when {
        state.isLoading && state.isEmpty ->
            items(count = 2, key = { "gov-skeleton-$it" }) { ProposalSkeletonCard() }
        state.isEmpty -> item(key = "gov-empty") { GovernanceEmptyState() }
        else -> {
            proposalSection("active", state.active, onVoteClick)
            proposalSection("passed", state.passed, onVoteClick)
            proposalSection("rejected", state.rejected, onVoteClick)
        }
    }
}

private fun LazyListScope.proposalSection(
    key: String,
    proposals: List<ProposalUi>,
    onVoteClick: (ProposalUi) -> Unit,
) {
    if (proposals.isEmpty()) return
    item(key = "gov-header-$key") {
        Text(
            text = stringResource(proposals.first().status.labelRes),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
    }
    items(proposals, key = { "gov-$key-${it.id}" }) { proposal ->
        ProposalCard(proposal = proposal, onVoteClick = { onVoteClick(proposal) })
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
internal fun GovernanceVoteSheet(
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

@get:StringRes
internal val ProposalStatus.labelRes: Int
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
