package com.vultisig.wallet.ui.models.deposit.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel

/** Builds an Unstake [DepositTransaction] for the TON chain. */
internal class UnstakeStrategy(
    private val vaultIdProvider: () -> String?,
    private val chainProvider: () -> Chain?,
    private val stateProvider: () -> DepositFormUiModel,
    private val nodeAddressFieldState: TextFieldState,
    private val tokenAmountFieldState: TextFieldState,
    private val accountsRepository: AccountsRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    private val getFeesFiatValue:
        suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
) : DepositSubmitStrategy {

    override suspend fun build(): DepositTransaction =
        buildTonDepositTransaction(
            memo = DepositMemo.Unstake,
            vaultIdProvider = vaultIdProvider,
            chainProvider = chainProvider,
            stateProvider = stateProvider,
            nodeAddressFieldState = nodeAddressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            accountsRepository = accountsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = calculateGasFee,
            getFeesFiatValue = getFeesFiatValue,
        )
}
