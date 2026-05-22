package com.vultisig.wallet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.ParsedSolanaTransaction
import com.vultisig.wallet.data.chains.helpers.SolanaTransactionParser
import com.vultisig.wallet.ui.theme.Theme
import timber.log.Timber
import vultisig.keysign.v1.SignSolana

@Composable
fun SignSolanaDisplayView(signSolana: SignSolana, modifier: Modifier = Modifier) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val rawTransactions = signSolana.rawTransactions

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.solana_raw_transaction),
                style = Theme.brockmann.button.medium.regular,
                color = Theme.v2.colors.text.tertiary,
            )

            IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(10.dp)) {
                UiIcon(
                    drawableResId = R.drawable.chevron,
                    tint = Theme.v2.colors.neutrals.n100,
                    size = 8.dp,
                    modifier = Modifier.graphicsLayer(rotationZ = if (isExpanded) 180f else 0f),
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            // SolanaTransactionParser.parse is wallet-core JNI work; deferring it until first
            // expansion means non-expanding users never pay the parse cost on the composition
            // thread.
            val instructions =
                remember(rawTransactions) {
                    rawTransactions.flatMap { tx ->
                        try {
                            SolanaTransactionParser.parse(tx).instructions
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse Solana transaction")
                            emptyList()
                        }
                    }
                }

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (instructions.isNotEmpty()) {
                    InstructionsSummarySection(instructions)
                }
                RawTransactionsSection(rawTransactions)
            }
        }
    }
}

@Composable
private fun InstructionsSummarySection(
    instructions: List<ParsedSolanaTransaction.ParsedInstruction>
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = Theme.v2.colors.variables.bordersLight,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.solana_instructions_summary),
            style = Theme.brockmann.button.medium.regular,
            color = Theme.v2.colors.text.primary,
            fontSize = 13.sp,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            instructions.forEachIndexed { index, instruction ->
                InstructionRow(instruction = instruction, index = index)
            }
        }
    }
}

@Composable
private fun InstructionRow(instruction: ParsedSolanaTransaction.ParsedInstruction, index: Int) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Header "Instruction N: <type>" mirrors the browser extension; programName is rendered
        // separately below so a long name can't truncate the instruction type.
        val headerSuffix = instruction.instructionType?.let { ": $it" }.orEmpty()
        Text(
            text = stringResource(R.string.solana_instruction_number, index + 1) + headerSuffix,
            style = Theme.brockmann.button.medium.medium,
            color = Theme.v2.colors.text.primary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = stringResource(R.string.solana_program_id, instruction.programId),
            color = Theme.v2.colors.neutrals.n100,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text =
                pluralStringResource(
                    R.plurals.solana_accounts_data,
                    instruction.accountsCount,
                    instruction.accountsCount,
                    instruction.dataLength,
                ),
            color = Theme.v2.colors.neutrals.n100,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun RawTransactionsSection(rawTransactions: List<String>) {
    VerifyCardJsonDetails(
        title = stringResource(R.string.raw_transaction_data),
        subtitle = rawTransactions.joinToString(separator = "\n"),
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = Theme.v2.colors.variables.bordersLight,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp),
    )
}

private val PREVIEW_INSTRUCTIONS =
    listOf(
        ParsedSolanaTransaction.ParsedInstruction(
            programId = "11111111111111111111111111111111",
            programName = "System Program",
            instructionType = "Transfer",
            accountsCount = 2,
            dataLength = 12,
        )
    )

private val PREVIEW_RAW_TRANSACTIONS =
    listOf(
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    )

@Preview
@Composable
private fun PreviewSignSolanaInstructionsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InstructionsSummarySection(PREVIEW_INSTRUCTIONS)
        RawTransactionsSection(PREVIEW_RAW_TRANSACTIONS)
    }
}

@Preview
@Composable
private fun PreviewSignSolanaDisplayView() {
    SignSolanaDisplayView(signSolana = SignSolana(rawTransactions = PREVIEW_RAW_TRANSACTIONS))
}
