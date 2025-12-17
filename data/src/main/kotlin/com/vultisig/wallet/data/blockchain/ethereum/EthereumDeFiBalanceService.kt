package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.blockchain.model.StakingDetails.Companion.generateId
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigInteger

class EthereumDeFiBalanceService(
    private val stakingDetailsRepository: StakingDetailsRepository,
    private val scaCircleAccountRepository: ScaCircleAccountRepository,
    private val evmApi: EvmApiFactory,
) : DeFiService {

    override suspend fun getCacheDeFiBalance(
        address: String,
        vaultId: String
    ): List<DeFiBalance> {
        Timber.d("EthereumDeFiBalanceService: Fetching DeFi balances for address: $address")

        val scaAccount = scaCircleAccountRepository.getAccount(vaultId) ?: return zeroDeFiBalance()
        val id = Coins.Ethereum.USDC.generateId(scaAccount)

        val cachedDetails =
            stakingDetailsRepository.getStakingDetailsById(vaultId, id) ?: return zeroDeFiBalance()

        return listOf(
            DeFiBalance(
                chain = Chain.Ethereum,
                balances = listOf(
                    DeFiBalance.Balance(
                        coin = cachedDetails.coin,
                        amount = cachedDetails.stakeAmount
                    )
                )
            )
        )
    }

    override suspend fun getRemoteDeFiBalance(
        address: String,
        vaultId: String
    ): List<DeFiBalance> {
        val scaAccount = scaCircleAccountRepository.getAccount(vaultId) ?: return zeroDeFiBalance()

        val api = evmApi.createEvmApi(Chain.Ethereum)
        val usdc = Coins.Ethereum.USDC.copy(address = scaAccount)
        val usdcDepositedBalance = withContext(Dispatchers.IO) {
            api.getBalance(usdc)
        }

        val usdcCircleStakingDetails = StakingDetails(
            id = usdc.generateId(scaAccount),
            coin = usdc,
            stakeAmount = usdcDepositedBalance,
            apr = null,
            estimatedRewards = null,
            nextPayoutDate = null,
            rewards = null,
            rewardsCoin = usdc,
        )

        // Save position in  cache
        withContext(Dispatchers.IO) {
            stakingDetailsRepository.saveStakingDetails(vaultId, usdcCircleStakingDetails)
        }

        return listOf(
            DeFiBalance(
                chain = Chain.Ethereum,
                balances = listOf(
                    DeFiBalance.Balance(
                        coin = usdc,
                        amount = usdcDepositedBalance
                    )
                )
            )
        )
    }

    private fun zeroDeFiBalance(): List<DeFiBalance> {
        return listOf(
            DeFiBalance(
                chain = Chain.Ethereum,
                balances = listOf(
                    DeFiBalance.Balance(
                        coin = Coins.Ethereum.USDC,
                        amount = BigInteger.ZERO
                    )
                )
            )
        )
    }
}