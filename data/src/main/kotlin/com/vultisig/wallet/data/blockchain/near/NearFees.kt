package com.vultisig.wallet.data.blockchain.near

import java.math.BigInteger

/**
 * NEAR fee arithmetic, transcribed from nearcore protocol 86 (release 2.13.4):
 * `runtime/runtime/src/config.rs` `calculate_tx_cost`, `core/parameters/src/cost.rs` (an implicit
 * receiver reserves `create_account` + `add_full_access_key` gas whether or not it already exists)
 * and `runtime/runtime/src/verifier.rs` `check_storage_stake` (NEP-448 exempts `storage_usage <=
 * 770`).
 *
 * `account_creation_charge` is absent on purpose: nearcore collects it at execution time out of the
 * receipt's gas refund, so it is neither upfront nor part of the balance requirement.
 */
object NearFees {

    /** NEP-448 zero-balance accounts reach this storage usage; `verifier.rs:40`. */
    val ZERO_BALANCE_STORAGE_LIMIT: BigInteger = BigInteger.valueOf(770)

    /** `ParameterCost` as exposed by `transaction_costs` in the runtime config. */
    data class ParameterCost(
        val sendSir: BigInteger,
        val sendNotSir: BigInteger,
        val execution: BigInteger,
    )

    data class FeeConfig(
        val actionReceiptCreation: ParameterCost,
        val transfer: ParameterCost,
        val createAccount: ParameterCost,
        val addFullAccessKey: ParameterCost,
        /** Floor price for the gas attached to the receipt (`min_gas_purchase_price`). */
        val minGasPurchasePrice: BigInteger,
        val storageAmountPerByte: BigInteger,
    )

    /**
     * YoctoNEAR the sender must hold on top of the transfer amount. [senderIsReceiver] is
     * nearcore's `sender_is_receiver`: a self-send pays the cheaper `send_sir` variants.
     */
    fun gasReservation(
        config: FeeConfig,
        gasPrice: BigInteger,
        senderIsReceiver: Boolean,
        receiverIsImplicit: Boolean,
    ): BigInteger {
        fun ParameterCost.send() = if (senderIsReceiver) sendSir else sendNotSir

        val creationSendGas =
            if (receiverIsImplicit) config.createAccount.send() + config.addFullAccessKey.send()
            else BigInteger.ZERO
        val creationExecGas =
            if (receiverIsImplicit)
                config.createAccount.execution + config.addFullAccessKey.execution
            else BigInteger.ZERO

        val burntGas =
            config.actionReceiptCreation.send() + config.transfer.send() + creationSendGas
        val remainingGas =
            config.actionReceiptCreation.execution + config.transfer.execution + creationExecGas

        // Conversion gas is burnt at the block price; the receipt's gas is purchased at a price
        // floored by `min_gas_purchase_price`, a factor of ten apart on mainnet.
        val receiptPrice = gasPrice.max(config.minGasPurchasePrice)

        return burntGas * gasPrice + remainingGas * receiptPrice
    }

    /**
     * Balance that must stay behind to back the account's own storage. `locked` (staking) only
     * relaxes this requirement — it never becomes spendable.
     */
    fun storageReserve(
        storageUsage: BigInteger,
        locked: BigInteger,
        storageAmountPerByte: BigInteger,
    ): BigInteger {
        if (storageUsage <= ZERO_BALANCE_STORAGE_LIMIT) return BigInteger.ZERO
        return (storageAmountPerByte * storageUsage - locked).max(BigInteger.ZERO)
    }

    fun maxSendable(
        balance: BigInteger,
        gasReservation: BigInteger,
        storageReserve: BigInteger,
    ): BigInteger = (balance - gasReservation - storageReserve).max(BigInteger.ZERO)
}
