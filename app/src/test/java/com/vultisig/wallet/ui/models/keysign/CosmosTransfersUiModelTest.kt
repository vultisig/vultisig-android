package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.ui.models.transaction.CosmosAmountUiModel
import com.vultisig.wallet.ui.models.transaction.CosmosTransfersRow
import com.vultisig.wallet.ui.models.transaction.CosmosTransfersUiModel
import com.vultisig.wallet.ui.models.transaction.rows
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.math.BigInteger
import org.junit.jupiter.api.Test

/**
 * Covers turning a Cosmos sign-data review into the verify screen's transfers block: labelling
 * coins only from known denoms, stripping dApp text of control and bidi characters, and which rows
 * show.
 */
internal class CosmosTransfersUiModelTest {

    @Test
    fun `native and listed denoms are labelled with their ticker and decimals`() {
        val model =
            review(leg(RECIPIENT, "rune", 150_000_000), leg(RECIPIENT, "tcy", 2_500_000_000))
                .toUiModel(Chain.ThorChain, Coins.ThorChain.RUNE)

        model.amounts shouldBe
            listOf(
                CosmosAmountUiModel("1.5 RUNE", isKnownDenom = true),
                CosmosAmountUiModel("25 TCY", isKnownDenom = true),
            )
    }

    @Test
    fun `an unlisted denom named like a known coin is shown raw and flagged unverified`() {
        val model =
            review(leg(RECIPIENT, "RUNE", 1_000_000))
                .toUiModel(Chain.ThorChain, Coins.ThorChain.RUNE)

        model.amounts shouldBe listOf(CosmosAmountUiModel("1000000 RUNE", isKnownDenom = false))
    }

    @Test
    fun `control, bidi and zero-width characters are stripped from dApp text`() {
        val model =
            review(
                    leg("thor1abc\n1 RUNE", "fake‮denom​", 5, memo = "swap\r\n⁦x"),
                    leg("thor1abc‍\n1 RUNE", "rune", 100_000_000),
                )
                .toUiModel(Chain.ThorChain, Coins.ThorChain.RUNE)

        model.recipients shouldBe listOf("thor1abc1 RUNE")
        model.amounts.first() shouldBe CosmosAmountUiModel("5 fakedenom", isKnownDenom = false)
        model.memos shouldBe listOf("swapx")
    }

    @Test
    fun `a full summary shows recipients, amounts and memos without a warning`() {
        val amounts = listOf(CosmosAmountUiModel("1 ATOM", isKnownDenom = true))
        transfers(recipients = listOf(RECIPIENT), amounts = amounts, memos = listOf("m"))
            .rows() shouldBe
            listOf(
                CosmosTransfersRow.Recipients(listOf(RECIPIENT)),
                CosmosTransfersRow.Amounts(amounts),
                CosmosTransfersRow.Memos(listOf("m")),
            )
    }

    @Test
    fun `a partial summary leads with the warning`() {
        val amounts = listOf(CosmosAmountUiModel("1 ATOM", isKnownDenom = true))
        transfers(recipients = listOf(RECIPIENT), amounts = amounts, isPartial = true)
            .rows() shouldBe
            listOf(
                CosmosTransfersRow.PartialWarning,
                CosmosTransfersRow.Recipients(listOf(RECIPIENT)),
                CosmosTransfersRow.Amounts(amounts),
            )
    }

    @Test
    fun `without amounts only the warning can show`() {
        transfers(recipients = listOf(RECIPIENT), memos = listOf("m"), isPartial = true)
            .rows() shouldBe listOf(CosmosTransfersRow.PartialWarning)
        transfers(recipients = listOf(RECIPIENT), memos = listOf("m")).rows().shouldBeEmpty()
    }

    @Test
    fun `amounts without a recipient skip the To row`() {
        val amounts = listOf(CosmosAmountUiModel("1 RUNE", isKnownDenom = true))
        transfers(amounts = amounts).rows() shouldBe listOf(CosmosTransfersRow.Amounts(amounts))
    }

    private fun leg(recipient: String?, denom: String, amount: Long, memo: String? = null) =
        CosmosSignedTransferLeg(
            recipient = recipient,
            coin = CosmosSignedCoin(denom, BigInteger.valueOf(amount)),
            memo = memo,
        )

    private fun review(vararg legs: CosmosSignedTransferLeg) =
        CosmosSignDataReview(transfers = legs.toList(), hasUnsummarizedMessages = false)

    private fun transfers(
        recipients: List<String> = emptyList(),
        amounts: List<CosmosAmountUiModel> = emptyList(),
        memos: List<String> = emptyList(),
        isPartial: Boolean = false,
    ) =
        CosmosTransfersUiModel(
            recipients = recipients,
            amounts = amounts,
            memos = memos,
            isPartial = isPartial,
        )

    private companion object {
        const val RECIPIENT = "thor1recipient00000000000000000000000000"
    }
}
