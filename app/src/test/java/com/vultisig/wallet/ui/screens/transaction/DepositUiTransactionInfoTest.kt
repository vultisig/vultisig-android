package com.vultisig.wallet.ui.screens.transaction

import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.keysign.TransactionTypeUiModel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The Deposit branch of [toUiTransactionInfo] must surface the resolved From/To labels so a Cosmos
 * staking deposit renders the same name resolution as a send on the Transaction-complete screen
 * (issue #4939).
 */
internal class DepositUiTransactionInfoTest {

    @Test
    fun `deposit carries resolved from and to labels`() {
        val model =
            TransactionTypeUiModel.Deposit(
                DepositTransactionUiModel(
                    srcAddress = "terra1pxpx9",
                    dstAddress = "terra1validator",
                    srcVaultName = "Main Vault",
                    dstVaultName = "Savings Vault",
                )
            )

        val info = model.toUiTransactionInfo()

        info.type shouldBe UiTransactionInfoType.Deposit
        info.from shouldBe "terra1pxpx9"
        info.fromLabel shouldBe "Main Vault"
        info.to shouldBe "terra1validator"
        info.toLabel shouldBe "Savings Vault"
    }

    @Test
    fun `deposit to-label falls back to address-book title when no vault name`() {
        val model =
            TransactionTypeUiModel.Deposit(
                DepositTransactionUiModel(
                    dstAddress = "terra1validator",
                    dstVaultName = null,
                    dstAddressBookTitle = "My Validator",
                )
            )

        model.toUiTransactionInfo().toLabel shouldBe "My Validator"
    }

    @Test
    fun `deposit without resolved labels leaves them null`() {
        val model =
            TransactionTypeUiModel.Deposit(
                DepositTransactionUiModel(
                    srcAddress = "terra1pxpx9",
                    dstAddress = "terra1validator",
                )
            )

        val info = model.toUiTransactionInfo()

        info.fromLabel shouldBe null
        info.toLabel shouldBe null
    }
}
