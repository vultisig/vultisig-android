package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.blockchain.ton.TonNominatorPool
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.flow.first

/** Whether a TON nominator-pool transaction deposits into or withdraws from the pool. */
internal enum class TonStakingAction {
    DEPOSIT,
    WITHDRAW,
}

/** Builds a Stake (deposit) [DepositTransaction] for a TON nominator pool. */
internal class StakeStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val stateProvider: () -> DepositFormUiModel,
    private val nodeAddressFieldState: TextFieldState,
    private val tokenAmountFieldState: TextFieldState,
    private val accountsRepository: AccountsRepository,
    private val tonStakingApi: TonStakingApi,
    private val toBounceableAddress: (String) -> String,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    private val getFeesFiatValue:
        suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
) : DepositSubmitStrategy {

    override suspend fun build(): DepositTransaction =
        buildTonStakingTransaction(
            action = TonStakingAction.DEPOSIT,
            vaultIdProvider = vaultIdProvider,
            chainProvider = chainProvider,
            stateProvider = stateProvider,
            nodeAddressFieldState = nodeAddressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            accountsRepository = accountsRepository,
            tonStakingApi = tonStakingApi,
            toBounceableAddress = toBounceableAddress,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = calculateGasFee,
            getFeesFiatValue = getFeesFiatValue,
        )
}

/**
 * Builds a TON nominator-pool [DepositTransaction] for [action] (deposit or withdraw).
 *
 * Shared by the Stake and Unstake strategies. The destination pool's `implementation` (resolved
 * from tonapi) drives the text comment — `whales` → `Deposit`/`Withdraw`, `tf` → `d`/`w` — and an
 * unknown implementation blocks the action rather than guessing. The pool address is converted to
 * the bounceable user-friendly `EQ…` form so a rejected message bounces back instead of being
 * absorbed (lost). A deposit must clear `min_stake` + a ~1 TON commission; a withdraw carries only
 * the fixed 0.2 TON signal fee (the pool returns the full staked balance).
 */
internal suspend fun buildTonStakingTransaction(
    action: TonStakingAction,
    vaultIdProvider: () -> String?,
    chainProvider: () -> Chain?,
    stateProvider: () -> DepositFormUiModel,
    nodeAddressFieldState: TextFieldState,
    tokenAmountFieldState: TextFieldState,
    accountsRepository: AccountsRepository,
    tonStakingApi: TonStakingApi,
    toBounceableAddress: (String) -> String,
    blockChainSpecificRepository: BlockChainSpecificRepository,
    calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    getFeesFiatValue: suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
): DepositTransaction {
    val vaultId =
        requireNotNull(vaultIdProvider()) {
            "vaultId must be initialized before creating transaction"
        }
    val chain =
        chainProvider()
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

    if (stateProvider().depositChain != Chain.Ton) {
        throw InvalidTransactionDataException(UiText.StringResource(R.string.error_invalid_chain))
    }

    val poolAddressInput = nodeAddressFieldState.text.toString().trim()
    if (poolAddressInput.isBlank()) {
        throw InvalidTransactionDataException(UiText.StringResource(R.string.send_error_no_address))
    }

    // Resolve the pool: its implementation drives the comment word and gates the action; min_stake
    // is needed to validate a deposit. A pool unknown to tonapi (or an unsupported implementation)
    // is blocked rather than guessed.
    val poolInfo =
        tonStakingApi.getStakingPool(poolAddressInput)
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.ton_stake_error_unsupported_pool)
            )

    val comment =
        when (action) {
            TonStakingAction.DEPOSIT -> TonNominatorPool.depositComment(poolInfo.implementation)
            TonStakingAction.WITHDRAW -> TonNominatorPool.withdrawComment(poolInfo.implementation)
        }
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.ton_stake_error_unsupported_pool)
            )

    // Pool addresses arrive raw `0:…`; convert to the bounceable `EQ…` form so a rejected deposit
    // bounces back. An address that can't be converted is invalid.
    val dstAddress =
        runCatching { toBounceableAddress(poolAddressInput) }
            .getOrElse {
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_address)
                )
            }

    val address = accountsRepository.loadAddress(vaultId, chain).first()
    val selectedToken =
        address.accounts.firstOrNull { it.token.isNativeToken }?.token
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

    val amountNano =
        when (action) {
            TonStakingAction.DEPOSIT -> {
                // A deposit needs a real minimum to enforce; a missing/zero min_stake means drifted
                // pool metadata, so block rather than silently allow a near-zero deposit.
                val minStakeNano =
                    poolInfo.minStake?.takeIf { it > 0 }
                        ?: throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.ton_stake_error_unsupported_pool)
                        )
                resolveDepositAmount(tokenAmountFieldState, selectedToken, minStakeNano)
            }
            // The withdraw message carries only the fixed signal fee; the entered amount is
            // ignored.
            TonStakingAction.WITHDRAW -> TonNominatorPool.WITHDRAW_FEE
        }

    val srcAddress = selectedToken.address

    val gasFee = calculateGasFee(chain, selectedToken, srcAddress)

    // Pass the bounceable destination so the spec resolves bounceable = true; without a dstAddress
    // the flag defaults to false and a rejected deposit would be absorbed instead of bounced back.
    val specific =
        blockChainSpecificRepository.getSpecific(
            chain,
            srcAddress,
            selectedToken,
            gasFee,
            isSwap = false,
            isMaxAmountEnabled = false,
            isDeposit = true,
            dstAddress = dstAddress,
        )

    val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

    return DepositTransaction(
        id = UUID.randomUUID().toString(),
        vaultId = vaultId,
        srcToken = selectedToken,
        srcAddress = srcAddress,
        dstAddress = dstAddress,
        memo = comment,
        srcTokenValue = TokenValue(value = amountNano, token = selectedToken),
        estimatedFees = gasFee,
        estimateFeesFiat = gasFeeFiat.formattedFiatValue,
        blockChainSpecific = specific.blockChainSpecific,
    )
}

/**
 * Parses and validates the deposit amount entered for [token], enforcing the pool minimum
 * (`min_stake` + ~1 TON commission). [minStakeNano] is the pool's `min_stake` in nanotons.
 */
private fun resolveDepositAmount(
    tokenAmountFieldState: TextFieldState,
    token: Coin,
    minStakeNano: Long,
): BigInteger {
    val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()
    if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
        throw InvalidTransactionDataException(UiText.StringResource(R.string.send_error_no_amount))
    }

    val amountNano = tokenAmount.movePointRight(token.decimal).toBigInteger()

    val minDeposit = TonNominatorPool.minimumDeposit(BigInteger.valueOf(minStakeNano))
    if (amountNano < minDeposit) {
        val minDepositTon = BigDecimal(minDeposit).movePointLeft(token.decimal).stripTrailingZeros()
        throw InvalidTransactionDataException(
            UiText.FormattedText(
                R.string.ton_stake_error_min_amount,
                listOf(minDepositTon.toPlainString()),
            )
        )
    }

    return amountNano
}
