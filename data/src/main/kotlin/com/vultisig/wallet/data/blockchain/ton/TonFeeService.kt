package com.vultisig.wallet.data.blockchain.ton

import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.crypto.TonHelper.RECOMMENDED_JETTONS_AMOUNT

/**
 * Fee model for The Open Network (TON).
 * - Gas fee: covers computation cost (smart contract execution).
 * - Storage fee: continuous "state rent" proportional to bytes stored on-chain.
 * - Forwarding fee: network bandwidth cost for delivering messages.
 *
 * Unlike Ethereum, TON charges ongoing rent for storage to prevent dead contracts from bloating the
 * blockchain. Gas not used is refunded, but storage is always charged.
 *
 * **IMPORTANT** : TON Center has specific API to calculate fees by passing a serialized transaction
 * Unfortunately, it returns ridiculus low fees. Under investigation it seems that is is due to the
 * way wallet core serialize internal bag cells, for some reason it triggers something wrong on TON
 * center and returning only storage and forward fees, while gas fee is 0.
 *
 *                 Under investigation, and will open/address a potential issue to wallet core
 *                 it seems other users has experienced it.
 *
 * Reference: https://ton.org/docs/learn/overviews/fees
 */
class TonFeeService : FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
        return calculateDefaultFees(transaction)
    }

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee {
        val isTokenTransfer = !transaction.coin.isNativeToken

        val feeAmount =
            if (isTokenTransfer) {
                TON_DEFAULT_FEES + RECOMMENDED_JETTONS_AMOUNT.toBigInteger()
            } else {
                TON_DEFAULT_FEES
            }

        return BasicFee(feeAmount)
    }

    private companion object {
        // 0.05 TON, matching iOS/macOS `TonHelper.defaultFee`. TON refunds unused gas, so this is a
        // reservation ceiling rather than the actual charge; keeping it in sync across platforms
        // gives nominator-pool deposits enough forward gas and a consistent fee display.
        val TON_DEFAULT_FEES = "50000000".toBigInteger()
    }
}
