package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.math.BigInteger

class ThorchainDeFiBalanceService(
    private val rujiStakingService: RujiStakingService,
    private val tcyStakingService: TCYStakingService,
    private val defaultStakingPositionService: DefaultStakingPositionService,
    private val bondUseCase: ThorchainBondUseCase,
    private val stakingDetailsRepository: StakingDetailsRepository,
): DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String): List<DeFiBalance> = supervisorScope {
        Timber.d("ThorchainDeFiBalanceService: Fetching DeFi balances for address: $address")
        
        val rujiDeFiBalance = async { getRujiDeFiBalance(address) }
        val tcyStakingBalance = async { getTcyDeFiBalance(address) }
        val defaultStakingPositionsBalance = async { getDefaultStakingPositionsDeFiBalance(address) }
        val bondStakingBalance = async { getBondStakingPositionsDeFiBalance(address) }

        val results = listOf(
            rujiDeFiBalance.await(),
            tcyStakingBalance.await(),
            defaultStakingPositionsBalance.await(),
            bondStakingBalance.await(),
        )
        
        results.forEach { defiBalance ->
            defiBalance.balances.forEach { balance ->
                if (balance.amount > BigInteger.ZERO) {
                    Timber.d("ThorchainDeFiBalanceService: ${balance.coin.ticker} balance: ${balance.amount} (raw units)")
                }
            }
        }
        
        val totalPositions = results.flatMap { it.balances }.count { it.amount > BigInteger.ZERO }
        Timber.d("ThorchainDeFiBalanceService: Total DeFi positions found: $totalPositions")
        
        return@supervisorScope results
    }

    override suspend fun getCacheDeFiBalance(
        address: String,
        vaultId: String
    ): List<DeFiBalance> = supervisorScope{
        val rujiDetails =
            async { stakingDetailsRepository.getStakingDetails(vaultId, Coins.ThorChain.RUJI.id) }
        val tcyDetails =
            async { stakingDetailsRepository.getStakingDetails(vaultId, Coins.ThorChain.TCY.id) }
        val defaultDetails = async {
            stakingDetailsRepository.getStakingDetails(vaultId)
        }
        //val bonDetails = async {
        //    bondUseCase.
        //}


        error("")
    }

    private suspend fun getRujiDeFiBalance(address: String): DeFiBalance {
        val amount = runCatching {
            rujiStakingService.getStakingDetailsFromNetwork(address).stakeAmount
        }.getOrElse { exception ->
            Timber.e(exception, "ThorchainDeFiBalanceService: Failed to fetch RUJI balance")
            BigInteger.ZERO
        }

        Timber.d("ThorchainDeFiBalanceService: RUJI staking amount for $address: $amount")

        return DeFiBalance(
            chain = Chain.ThorChain,
            balances = listOf(
                DeFiBalance.Balance(
                    coin = Coins.ThorChain.RUJI,
                    amount = amount
                )
            )
        )
    }

    private suspend fun getTcyDeFiBalance(address: String): DeFiBalance {
        val amount = runCatching {
            tcyStakingService.getStakingDetailsFromNetwork(address).stakeAmount
        }.getOrElse { exception ->
            Timber.e(exception, "ThorchainDeFiBalanceService: Failed to fetch TCY balance")
            BigInteger.ZERO
        }

        Timber.d("ThorchainDeFiBalanceService: TCY staking amount for $address: $amount")

        return DeFiBalance(
            chain = Chain.ThorChain,
            balances = listOf(
                DeFiBalance.Balance(
                    coin = Coins.ThorChain.TCY,
                    amount = amount
                )
            )
        )
    }

    private suspend fun getBondStakingPositionsDeFiBalance(
        address: String
    ): DeFiBalance {
        val bondedNodes = runCatching {
            bondUseCase.getActiveNodesRemote(address)
        }.getOrElse { exception ->
            Timber.e(exception, "ThorchainDeFiBalanceService: Failed to fetch bonded nodes")
            emptyList()
        }
        
        val amount = bondedNodes.sumOf { it.amount }
        
        Timber.d("ThorchainDeFiBalanceService: Found ${bondedNodes.size} bonded nodes for $address")

        bondedNodes.forEach { node ->
            Timber.d("ThorchainDeFiBalanceService: Bonded node ${node.node.address}: amount=${node.amount}, apy=${node.apy}%")
        }

        Timber.d("ThorchainDeFiBalanceService: Total bonded amount: $amount")

        return DeFiBalance(
            chain = Chain.ThorChain,
            balances = listOf(
                DeFiBalance.Balance(
                    coin = Coins.ThorChain.RUNE,
                    amount = amount
                )
            )
        )
    }

    private suspend fun getDefaultStakingPositionsDeFiBalance(address: String): DeFiBalance {
        val defiBalances = runCatching {
            val stakingDetails = defaultStakingPositionService.getStakingDetailsFromNetwork(address)
            Timber.d("ThorchainDeFiBalanceService: Found ${stakingDetails.size} default staking positions")
            
            stakingDetails.map { detail ->
                Timber.d("ThorchainDeFiBalanceService: ${detail.coin.ticker} staking: amount=${detail.stakeAmount}")
                DeFiBalance.Balance(
                    coin = detail.coin,
                    amount = detail.stakeAmount
                )
            }
        }.getOrElse { exception ->
            Timber.e(exception, "ThorchainDeFiBalanceService: Failed to fetch default staking positions, using zero balances")
            defaultStakingPositionService.supportedStakingCoins.map {
                DeFiBalance.Balance(
                    coin = it,
                    amount = BigInteger.ZERO
                )
            }
        }
        
        val totalAmount = defiBalances.sumOf { it.amount }
        Timber.d("ThorchainDeFiBalanceService: Total default staking amount: $totalAmount across ${defiBalances.size} positions")

        return DeFiBalance(
            chain = Chain.ThorChain,
            balances = defiBalances,
        )
    }
}
