@file:OptIn(ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.OPERATION_BOND_YIELD
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositGasFeeHelper
import java.math.BigInteger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Builds the THORChain Rewards Payout [DepositTransaction]: a 0 RUNE `MsgDeposit` whose BONDYIELD
 * memo pays [payoutPercentage]% of [nodeAddress]'s future rewards to the vault's own address.
 */
internal suspend fun buildBondYieldTransaction(
    vaultId: String,
    rune: Coin,
    nodeAddress: String,
    payoutPercentage: Int,
    blockChainSpecificRepository: BlockChainSpecificRepository,
    depositGasFeeHelper: DepositGasFeeHelper,
): DepositTransaction {
    val chain = Chain.ThorChain
    val memo = DepositMemo.BondYield(nodeAddress, payoutPercentage, payoutAddress = rune.address)
    val gasFee = depositGasFeeHelper.calculateGasFee(vaultId, chain, rune, rune.address)
    val specific =
        blockChainSpecificRepository.getSpecific(
            chain,
            rune.address,
            rune,
            gasFee,
            isSwap = false,
            isMaxAmountEnabled = false,
            isDeposit = true,
        )
    val gasFeeFiat = depositGasFeeHelper.getFeesFiatValue(chain, specific, gasFee, rune)

    return DepositTransaction(
        id = Uuid.random().toString(),
        vaultId = vaultId,
        srcToken = rune,
        srcAddress = rune.address,
        dstAddress = nodeAddress,
        memo = memo.toString(),
        srcTokenValue = TokenValue(value = BigInteger.ZERO, token = rune),
        estimatedFees = gasFee,
        blockChainSpecific = specific.blockChainSpecific,
        estimateFeesFiat = gasFeeFiat.formattedFiatValue,
        operation = OPERATION_BOND_YIELD,
        nodeAddress = nodeAddress,
    )
}
