package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel

/** Builds an Unstake (withdraw) [DepositTransaction] for a TON nominator pool. */
internal class UnstakeStrategy(
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
            action = TonStakingAction.WITHDRAW,
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
