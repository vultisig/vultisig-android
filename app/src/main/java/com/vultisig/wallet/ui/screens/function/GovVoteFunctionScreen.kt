package com.vultisig.wallet.ui.screens.function

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.gov.CosmosVoteOption
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.library.form.FormEntry
import com.vultisig.wallet.ui.components.library.form.FormTextFieldCard
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

/**
 * Governance vote form: the ballot as an inline list — four fixed options need no picker — then the
 * proposal id. Nothing is preselected, so the ballot signed is always one the user chose.
 */
@Composable
internal fun GovVoteFunctionScreen(
    selectedOption: CosmosVoteOption?,
    onSelectOption: (CosmosVoteOption) -> Unit,
    optionError: UiText?,
    proposalIdFieldState: TextFieldState,
    onProposalIdLostFocus: () -> Unit,
    proposalIdError: UiText?,
) {
    FormEntry(title = stringResource(R.string.governance_cast_vote)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            CosmosVoteOption.entries.forEach { option ->
                BallotRow(
                    label = stringResource(option.labelRes),
                    isSelected = option == selectedOption,
                    onClick = { onSelectOption(option) },
                )
            }
            optionError?.let { error ->
                Text(
                    text = error.asString(),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.alerts.error,
                )
            }
        }
    }

    FormTextFieldCard(
        title = stringResource(R.string.deposit_form_proposal_id_title),
        hint = stringResource(R.string.deposit_form_proposal_id_title),
        keyboardType = KeyboardType.Number,
        textFieldState = proposalIdFieldState,
        onLostFocus = onProposalIdLostFocus,
        error = proposalIdError,
    )
}

@get:StringRes
internal val CosmosVoteOption.labelRes: Int
    get() =
        when (this) {
            CosmosVoteOption.YES -> R.string.governance_vote_yes
            CosmosVoteOption.ABSTAIN -> R.string.governance_vote_abstain
            CosmosVoteOption.NO -> R.string.governance_vote_no
            CosmosVoteOption.NO_WITH_VETO -> R.string.governance_vote_no_with_veto
        }

@Composable
private fun BallotRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .clip(Theme.v2.radius.md)
                .background(Theme.v2.colors.backgrounds.surface1)
                .border(
                    width = 1.dp,
                    color =
                        if (isSelected) Theme.v2.colors.primary.accent4
                        else Theme.v2.colors.border.normal,
                    shape = Theme.v2.radius.md,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.primary,
        )
        if (isSelected) {
            UiIcon(
                drawableResId = R.drawable.ic_check,
                size = 16.dp,
                tint = Theme.v2.colors.primary.accent4,
            )
        }
    }
}
