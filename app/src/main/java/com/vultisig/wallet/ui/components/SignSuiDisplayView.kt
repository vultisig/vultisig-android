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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.chains.helpers.ParsedSuiTransaction
import com.vultisig.wallet.data.chains.helpers.SuiArgument
import com.vultisig.wallet.data.chains.helpers.SuiCommand
import com.vultisig.wallet.data.chains.helpers.SuiPtbInput
import com.vultisig.wallet.data.chains.helpers.SuiPtbParser
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigInteger
import timber.log.Timber

/**
 * Verify-screen card for a dApp-supplied Sui Programmable Transaction Block (SignSui). Decodes the
 * PTB's BCS `TransactionData` bytes and renders its sender, gas budget/price, commands (MoveCall,
 * TransferObjects, SplitCoins, …) and inputs, so a co-signer can review what it actually signs
 * instead of an opaque blob. Falls back to the raw base64 bytes — mirroring [SignSolanaDisplayView]
 * — only when the PTB fails to decode.
 *
 * @param sender the vault address signing the PTB, shown only in the raw-bytes fallback.
 * @param unsignedTxMsg base64-encoded `TransactionData` BCS bytes.
 */
@Composable
fun SignSuiDisplayView(
    sender: String,
    unsignedTxMsg: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var isExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

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
                text = stringResource(R.string.sui_raw_transaction),
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
            // SuiPtbParser is pure Kotlin BCS decoding, not JNI, but still non-trivial work;
            // deferring it until first expansion mirrors SignSolanaDisplayView so non-expanding
            // users never pay the parse cost on the composition thread.
            val parsedTransaction =
                remember(unsignedTxMsg) {
                    try {
                        SuiPtbParser.parse(unsignedTxMsg)
                    } catch (e: Exception) {
                        when (e) {
                            is IllegalArgumentException,
                            is IllegalStateException,
                            is IndexOutOfBoundsException -> {
                                Timber.w(e, "Failed to parse Sui PTB")
                                null
                            }
                            else -> throw e
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
                if (parsedTransaction != null) {
                    DecodedPtbSections(parsedTransaction)
                } else {
                    RawBytesFallback(sender, unsignedTxMsg)
                }
            }
        }
    }
}

@Composable
private fun DecodedPtbSections(transaction: ParsedSuiTransaction) {
    if (transaction.sender.isNotBlank()) {
        VerifyCardJsonDetails(
            title = stringResource(R.string.sui_sender),
            subtitle = transaction.sender,
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        color = Theme.v2.colors.variables.bordersLight,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 12.dp),
        )
    }
    GasSummarySection(transaction)
    if (transaction.commands.isNotEmpty()) {
        CommandsSummarySection(transaction.commands)
    }
    if (transaction.inputs.isNotEmpty()) {
        InputsSummarySection(transaction.inputs)
    }
}

@Composable
private fun GasSummarySection(transaction: ParsedSuiTransaction) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = Theme.v2.colors.variables.bordersLight,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.sui_gas_budget, formatSuiAmount(transaction.gasBudget)),
            color = Theme.v2.colors.neutrals.n100,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
        )
        Text(
            text = stringResource(R.string.sui_gas_price, transaction.gasPrice.toString()),
            color = Theme.v2.colors.neutrals.n100,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun RawBytesFallback(sender: String, unsignedTxMsg: String) {
    if (sender.isNotBlank()) {
        VerifyCardJsonDetails(
            title = stringResource(R.string.sui_sender),
            subtitle = sender,
            modifier =
                Modifier.fillMaxWidth()
                    .background(
                        color = Theme.v2.colors.variables.bordersLight,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 12.dp),
        )
    }
    VerifyCardJsonDetails(
        title = stringResource(R.string.raw_transaction_data),
        subtitle = unsignedTxMsg,
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = Theme.v2.colors.variables.bordersLight,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp),
    )
}

@Composable
private fun CommandsSummarySection(commands: List<SuiCommand>) {
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
            text = stringResource(R.string.sui_commands_summary),
            style = Theme.brockmann.button.medium.regular,
            color = Theme.v2.colors.text.primary,
            fontSize = 13.sp,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            commands.forEachIndexed { index, command -> CommandRow(command, index) }
        }
    }
}

@Composable
private fun CommandRow(command: SuiCommand, index: Int) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text =
                stringResource(R.string.sui_command_number, index + 1) + ": ${command.typeLabel()}",
            style = Theme.brockmann.button.medium.medium,
            color = Theme.v2.colors.text.primary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        command.targetLine()?.let {
            Text(
                text = stringResource(R.string.sui_command_target, it),
                color = Theme.v2.colors.neutrals.n100,
                style = Theme.brockmann.button.medium.medium,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        command.detailsLine()?.let {
            Text(
                text = stringResource(R.string.sui_command_details, it),
                color = Theme.v2.colors.neutrals.n100,
                style = Theme.brockmann.button.medium.medium,
                fontSize = 10.sp,
            )
        }

        command.argumentsLine()?.let {
            Text(
                text = stringResource(R.string.sui_arguments, it),
                color = Theme.v2.colors.neutrals.n100,
                style = Theme.brockmann.button.medium.medium,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun InputsSummarySection(inputs: List<SuiPtbInput>) {
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
            text = stringResource(R.string.sui_inputs_summary),
            style = Theme.brockmann.button.medium.regular,
            color = Theme.v2.colors.text.primary,
            fontSize = 13.sp,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            inputs.forEachIndexed { index, input -> InputRow(input, index) }
        }
    }
}

@Composable
private fun InputRow(input: SuiPtbInput, index: Int) {
    Text(
        text = stringResource(R.string.sui_input_number, index + 1) + ": ${input.render()}",
        color = Theme.v2.colors.neutrals.n100,
        style = Theme.brockmann.button.medium.medium,
        fontSize = 10.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun SuiCommand.typeLabel(): String =
    when (this) {
        is SuiCommand.MoveCall -> "MoveCall"
        is SuiCommand.TransferObjects -> "TransferObjects"
        is SuiCommand.SplitCoins -> "SplitCoins"
        is SuiCommand.MergeCoins -> "MergeCoins"
        is SuiCommand.Publish -> "Publish"
        is SuiCommand.MakeMoveVec -> "MakeMoveVec"
        is SuiCommand.Upgrade -> "Upgrade"
    }

private fun SuiCommand.targetLine(): String? =
    when (this) {
        is SuiCommand.MoveCall -> "$packageId::$module::$function"
        is SuiCommand.TransferObjects -> address.render()
        is SuiCommand.SplitCoins -> coin.render()
        is SuiCommand.MergeCoins -> destination.render()
        is SuiCommand.Upgrade -> packageId
        is SuiCommand.Publish,
        is SuiCommand.MakeMoveVec -> null
    }

private fun SuiCommand.detailsLine(): String? =
    when (this) {
        is SuiCommand.MoveCall -> typeArguments.takeIf { it.isNotEmpty() }?.joinToString(", ")
        is SuiCommand.Publish -> "$moduleCount modules, $dependencyCount dependencies"
        is SuiCommand.MakeMoveVec ->
            listOfNotNull(elementType, "$elementCount elements").joinToString(", ")
        is SuiCommand.Upgrade -> "$moduleCount modules, $dependencyCount dependencies"
        is SuiCommand.TransferObjects,
        is SuiCommand.SplitCoins,
        is SuiCommand.MergeCoins -> null
    }

private fun SuiCommand.argumentsLine(): String? =
    when (this) {
        is SuiCommand.MoveCall -> arguments.renderArgumentsOrNull()
        is SuiCommand.TransferObjects -> objects.renderArgumentsOrNull()
        is SuiCommand.SplitCoins -> amounts.renderArgumentsOrNull()
        is SuiCommand.MergeCoins -> sources.renderArgumentsOrNull()
        is SuiCommand.Upgrade -> ticket.render()
        is SuiCommand.Publish,
        is SuiCommand.MakeMoveVec -> null
    }

private fun List<SuiArgument>.renderArgumentsOrNull(): String? =
    takeIf { it.isNotEmpty() }?.joinToString(", ") { it.render() }

private fun SuiArgument.render(): String =
    when (this) {
        SuiArgument.GasCoin -> "GasCoin"
        is SuiArgument.Input -> "Input($index)"
        is SuiArgument.Result -> "Result($index)"
        is SuiArgument.NestedResult -> "Result($commandIndex[$resultIndex])"
    }

private fun SuiPtbInput.render(): String =
    when (this) {
        is SuiPtbInput.Pure -> "Pure(${value.type}): ${value.display}"
        is SuiPtbInput.Object -> {
            val mutabilitySuffix =
                mutable?.let { if (it) " (mutable)" else " (read-only)" }.orEmpty()
            "${kind.name}$mutabilitySuffix: $objectId"
        }
    }

/** MIST → SUI, e.g. `3000000` → `"0.003"`, trimmed of trailing zeros. */
private fun formatSuiAmount(mist: BigInteger): String =
    mist.toValue(SUI_DECIMALS).stripTrailingZeros().toPlainString()

private const val SUI_DECIMALS = 9

private const val PREVIEW_SUI_PTB =
    "AAACAAhkAAAAAAAAAAAgW4yMD3sdSyqcPk9QYXKDlKW2x9jp8KGyw9Tl9gcYKTACAgABAQAAAQEDAAAAAAEBAFuMjA97" +
        "HUsqnD5PUGFyg5SltsfY6fChssPU5fYHGCkwARERERERERERERERERERERERERERERERERERERERERERAQAA" +
        "AAAAAAAgBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwdbjIwPex1LKpw+T1BhcoOUpbbH2OnwobLD1O" +
        "X2BxgpMOgDAAAAAAAAwMYtAAAAAAAA"

private const val PREVIEW_SUI_RAW_FALLBACK =
    "AAACAAgA4fUFAAAAAAAgWqQ5q8s0e0kq0a7s3w2QxJYwq7XmZ1pL0c1d8s2f3g4FAQEBAQABAAA" +
        "x2QxJYwq7XmZ1pL0c1d8s2f3g4Aq7XmZ1pL0c1d8s2f3g4Fy0kq0a7s3w2QxJYwq7XmZ1AQ=="

@Preview
@Composable
private fun PreviewSignSuiDisplayViewDecoded() {
    SignSuiDisplayView(
        sender = "0x9a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef",
        unsignedTxMsg = PREVIEW_SUI_PTB,
        initiallyExpanded = true,
    )
}

@Preview
@Composable
private fun PreviewSignSuiDisplayViewRawFallback() {
    SignSuiDisplayView(
        sender = "0x9a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef",
        unsignedTxMsg = PREVIEW_SUI_RAW_FALLBACK,
        initiallyExpanded = true,
    )
}
