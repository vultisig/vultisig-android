package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.data.api.models.thorchain.MergeAccount
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import java.math.BigInteger

/**
 * Bundle of the view-model-owned dependencies shared across the deposit submit strategies.
 *
 * These values close over `DepositFormViewModel` mutable state (providers, field states, helper
 * method references) and therefore cannot be injected by Hilt; the view-model builds one context
 * per instance and hands it to [DepositStrategyFactory.create].
 *
 * @property vaultId provides the current vault id, or null before it is set.
 * @property chain provides the current source chain, or null before it is set.
 * @property state provides the current deposit form state snapshot.
 * @property address provides the current resolved source address, or null.
 * @property lpPoolId provides the liquidity-pool id for LP deposit options, or null.
 * @property fields the editable form field states.
 * @property blockChainSpecificRepository repository for chain-specific transaction data.
 * @property calculateGasFee computes the gas fee for a chain/token/source-address.
 * @property getFeesFiatValue maps a gas fee into its estimated fiat-valued representation.
 * @property selectedToken provides the currently selected token, or null.
 * @property selectedAccount provides the account for the currently selected token, or null.
 * @property requireTokenAmount validates and converts the entered amount to base units.
 * @property resolvePairedAddress resolves the paired-chain address for a symmetric LP add.
 * @property resolveSecuredAssetInboundAddress resolves the THORChain inbound vault for a deposit.
 * @property getBitcoinTransactionPlan builds a Bitcoin transaction plan for secured-asset deposits.
 * @property rujiMergeBalances provides the loaded RUJI merge balances, or null.
 */
internal class DepositStrategyContext(
    val vaultId: () -> String?,
    val chain: () -> Chain?,
    val state: () -> DepositFormUiModel,
    val address: () -> Address?,
    val lpPoolId: () -> String?,
    val fields: DepositFieldStates,
    val blockChainSpecificRepository: BlockChainSpecificRepository,
    val calculateGasFee: suspend (Chain, Coin, String) -> TokenValue,
    val getFeesFiatValue: suspend (BlockChainSpecificAndUtxo, TokenValue, Coin) -> EstimatedGasFee,
    val selectedToken: () -> Coin?,
    val selectedAccount: () -> Account?,
    val requireTokenAmount: (Coin, Account, Address, TokenValue) -> BigInteger,
    val resolvePairedAddress: suspend (Chain, String, String) -> String?,
    val resolveSecuredAssetInboundAddress: suspend (Coin) -> String,
    val getBitcoinTransactionPlan: BitcoinTransactionPlanBuilder,
    val rujiMergeBalances: () -> List<MergeAccount>?,
)
