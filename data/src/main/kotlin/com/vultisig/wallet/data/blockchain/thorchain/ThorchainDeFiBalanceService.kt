package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.math.BigInteger

class ThorchainDeFiBalanceService(
    private val rujiStakingService: RujiStakingService,
    private val tcyStakingService: TCYStakingService,
    private val defaultStakingPositionService: DefaultStakingPositionService,
    private val bondUseCase: ThorchainBondUseCase,
): DeFiService {

    override suspend fun getRemoteDeFiBalance(address: String): List<DeFiBalance> = supervisorScope {
        val rujiDeFiBalance = async { getRujiDeFiBalance(address) }
        val tcyStakingBalance = async { getTcyDeFiBalance(address) }
        val defaultStakingPositionsBalance = async { getDefaultStakingPositionsDeFiBalance(address) }
        val bondStakingBalance = async {getBondStakingPositionsDeFiBalance(address) }

        return@supervisorScope listOf(
            rujiDeFiBalance.await(),
            tcyStakingBalance.await(),
            defaultStakingPositionsBalance.await(),
            bondStakingBalance.await(),
        )
    }

    private suspend fun getRujiDeFiBalance(address: String): DeFiBalance {
        val amount = runCatching {
            rujiStakingService.getStakingDetailsFromNetwork(address).stakeAmount
        }.getOrDefault(BigInteger.ZERO)

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
        }.getOrDefault(BigInteger.ZERO)

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
        val amount = runCatching {
            bondUseCase.getActiveNodesRemote(address).sumOf { it.amount }
        }.getOrDefault(BigInteger.ZERO)

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
            defaultStakingPositionService.getStakingDetailsFromNetwork(address).map {
                DeFiBalance.Balance(
                    coin = it.coin,
                    amount = it.stakeAmount
                )
            }
        }.getOrElse {
            defaultStakingPositionService.supportedStakingCoins.map {
                DeFiBalance.Balance(
                    coin = it,
                    amount = BigInteger.ZERO
                )
            }
        }

        return DeFiBalance(
            chain = Chain.ThorChain,
            balances = defiBalances,
        )
    }
}
