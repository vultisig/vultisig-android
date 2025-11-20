package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.ActiveBondedNodeRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import java.math.BigInteger

class ThorchainDeFiBalanceService(
    private val rujiStakingService: RujiStakingService,
    private val tcyStakingService: TCYStakingService,
    private val defaultStakingPositionService: DefaultStakingPositionService,
    private val bondUseCase: ThorchainBondUseCase,
    private val stakingDetailsRepository: StakingDetailsRepository,
    private val activeBondedNodeRepository: ActiveBondedNodeRepository,
): DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String, vaultId: String): List<DeFiBalance> = supervisorScope {
        Timber.d("ThorchainDeFiBalanceService: Fetching DeFi balances for address: $address")
        
        val rujiDeFiBalance = async { getRemoteRujiDeFiBalance(address, vaultId) }
        val tcyStakingBalance = async { getRemoteTcyDeFiBalance(address, vaultId) }
        val defaultStakingPositionsBalance = async { getRemoteDefaultStakingPositionsDeFiBalance(address, vaultId) }
        val bondStakingBalance = async { getRemoteBondStakingPositionsDeFiBalance(address, vaultId) }

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
    ): List<DeFiBalance> = supervisorScope {
        val rujiDetailsDeferred =
            async { stakingDetailsRepository.getStakingDetails(vaultId, Coins.ThorChain.RUJI.id) }
        val tcyDetailsDeferred =
            async { stakingDetailsRepository.getStakingDetails(vaultId, Coins.ThorChain.TCY.id) }
        val defaultDetailsDeferred =
            async { stakingDetailsRepository.getStakingDetails(vaultId) }
        val bonDetailsDeferred =
            async { activeBondedNodeRepository.getBondedNodes(vaultId) }

        val rujiDetails = rujiDetailsDeferred.await()
        val tcyDetails = tcyDetailsDeferred.await()
        val defaultDetails = defaultDetailsDeferred.await()
        val bonDetails = bonDetailsDeferred.await()
        
        val defiBalances = mutableListOf<DeFiBalance>()
        
        // Add RUJI balance if exists
        rujiDetails?.let {
            defiBalances.add(
                DeFiBalance(
                    chain = Chain.ThorChain,
                    balances = listOf(
                        DeFiBalance.Balance(
                            coin = it.coin,
                            amount = it.stakeAmount
                        )
                    )
                )
            )
        }
        
        // Add TCY balance if exists
        tcyDetails?.let {
            defiBalances.add(
                DeFiBalance(
                    chain = Chain.ThorChain,
                    balances = listOf(
                        DeFiBalance.Balance(
                            coin = it.coin,
                            amount = it.stakeAmount
                        )
                    )
                )
            )
        }
        
        // Add default staking positions if any
        if (defaultDetails.isNotEmpty()) {
            defiBalances.add(
                DeFiBalance(
                    chain = Chain.ThorChain,
                    balances = defaultDetails.map { detail ->
                        DeFiBalance.Balance(
                            coin = detail.coin,
                            amount = detail.stakeAmount
                        )
                    }
                )
            )
        }
        
        // Add bonded nodes balance if any
        if (bonDetails.isNotEmpty()) {
            val totalBondedAmount = bonDetails.sumOf { it.amount }
            defiBalances.add(
                DeFiBalance(
                    chain = Chain.ThorChain,
                    balances = listOf(
                        DeFiBalance.Balance(
                            coin = Coins.ThorChain.RUNE,
                            amount = totalBondedAmount
                        )
                    )
                )
            )
        }
        
        defiBalances
    }

    private suspend fun getRemoteRujiDeFiBalance(address: String, vaultId: String): DeFiBalance {
        val amount = runCatching {
            rujiStakingService.getStakingDetails(address, vaultId).last()?.stakeAmount ?: BigInteger.ZERO
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

    private suspend fun getRemoteTcyDeFiBalance(address: String, vaultId: String): DeFiBalance {
        val amount = runCatching {
            tcyStakingService.getStakingDetails(address, vaultId).last()?.stakeAmount ?: BigInteger.ZERO
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

    private suspend fun getRemoteBondStakingPositionsDeFiBalance(
        address: String,
        vaultId: String,
    ): DeFiBalance {
        val bondedNodes = runCatching {
            bondUseCase.getActiveNodes(address, vaultId).last()
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

    private suspend fun getRemoteDefaultStakingPositionsDeFiBalance(
        address: String,
        vaultId: String,
    ): DeFiBalance {
        val defiBalances = runCatching {
            val stakingDetails = defaultStakingPositionService.getStakingDetails(address, vaultId).last()
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
