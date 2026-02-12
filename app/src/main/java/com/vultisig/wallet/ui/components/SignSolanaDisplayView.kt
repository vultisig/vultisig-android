package com.vultisig.wallet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.ParsedSolanaTransaction
import com.vultisig.wallet.data.chains.helpers.SolanaTransactionParser
import com.vultisig.wallet.ui.theme.Theme
import vultisig.keysign.v1.SignSolana
import kotlin.collections.flatMap

@Composable
fun SignSolanaDisplayView(
    signSolana: SignSolana,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = 12.dp,
            )
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.solana_raw_transaction),
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
                maxLines = 1,
            )

            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(10.dp)
            ) {
                UiIcon(
                    drawableResId = R.drawable.chevron,
                    tint = Theme.v2.colors.neutrals.n100,
                    size = 8.dp,
                    modifier = Modifier
                        .graphicsLayer(rotationZ = if (isExpanded) 180f else 0f)
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .background(
                        color = Theme.v2.colors.variables.bordersLight,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InstructionsSummarySection(signSolana)

                RawTransactionsSection(signSolana)
            }
        }
    }
}

@Composable
private fun InstructionsSummarySection(signSolana: SignSolana) {
    val allInstructions = remember(signSolana) {
        signSolana.rawTransactions.flatMap { tx ->
            try {
                val parsed = SolanaTransactionParser.parse(tx)
                parsed.instructions
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    if (allInstructions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Transaction Instructions Summary",
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.text.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allInstructions.forEachIndexed { index, instruction ->
                    InstructionRow(
                        instruction = instruction,
                        index = index
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionRow(
    instruction: ParsedSolanaTransaction.ParsedInstruction,
    index: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                shape = RoundedCornerShape(8.dp),
                color = Theme.v2.colors.backgrounds.surface3
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Instruction ${index + 1}",
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.text.primary
            )

            instruction.instructionType?.let { type ->
                Text(
                    text = ": $type",
                    style = Theme.brockmann.button.medium.medium,
                    color = Theme.v2.colors.text.primary
                )
            }
        }

        instruction.programName?.let { name ->
            Text(
                text = "Program: $name",
                fontSize = 12.sp,
                color = Theme.v2.colors.neutrals.n100
            )
        }

        Text(
            text = "Program ID: ${instruction.programId}",
            fontSize = 12.sp,
            color = Theme.v2.colors.neutrals.n100,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = "Accounts: ${instruction.accountsCount} | Data length: ${instruction.dataLength} bytes",
            fontSize = 12.sp,
            color = Theme.v2.colors.neutrals.n100
        )
    }
}

@Composable
private fun RawTransactionsSection(signSolana: SignSolana) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(R.string.raw_transaction_data),
            style = Theme.brockmann.button.medium.medium,
            color = Theme.v2.colors.text.primary
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Theme.v2.colors.backgrounds.darkBackground,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            signSolana.rawTransactions.forEach { tx ->
                Text(
                    text = tx,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Theme.v2.colors.buttons.primary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Preview
@Composable
private fun SignSolanaDisplayPrewview() {
    SignSolanaDisplayView(
        SignSolana(
            rawTransactions = listOf(
                "AgAAAAAABJgYHhQO8v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v1+Vt2u1ZpXn5+2z5s6q9v",
            )
        )
    )

}