package com.vultisig.wallet.data.blockchain.ethereum

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.blockchain.DeFiService
import com.vultisig.wallet.data.blockchain.model.BondedNodePosition.Companion.generateId
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import timber.log.Timber
import java.math.BigInteger

class EthereumDeFiBalanceService(
    private val stakingDetailsRepository: StakingDetailsRepository,
    private val scaCircleAccountRepository: ScaCircleAccountRepository,
    private val evmApi: EvmApi,
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