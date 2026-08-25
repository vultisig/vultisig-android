package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.blockchain.model.StakingDetails
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.ActiveBondedNodeRepository
import com.vultisig.wallet.data.repositories.StakingDetailsRepository
import com.vultisig.wallet.data.usecases.ThorchainBondUseCase
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The DeFi tab totals what these balances report. The receipt has to travel both paths — the cached
 * one the tab renders first and the network one that replaces it — or a bonded position reads as
 * zero on one of them.
 */
internal class ThorchainDeFiBalanceServiceYbRuneTest {

    private val rujiStakingService: RujiStakingService = mockk(relaxed = true)
    private val tcyStakingService: TCYStakingService = mockk(relaxed = true)
    private val defaultStakingPositionService: DefaultStakingPositionService = mockk(relaxed = true)
    private val bondUseCase: ThorchainBondUseCase = mockk(relaxed = true)
    private val stakingDetailsRepository: StakingDetailsRepository = mockk(relaxed = true)
    private val activeBondedNodeRepository: ActiveBondedNodeRepository = mockk(relaxed = true)

    private val service =
        ThorchainDeFiBalanceService(
            rujiStakingService = rujiStakingService,
            tcyStakingService = tcyStakingService,
            defaultStakingPositionService = defaultStakingPositionService,
            bondUseCase = bondUseCase,
            stakingDetailsRepository = stakingDetailsRepository,
            activeBondedNodeRepository = activeBondedNodeRepository,
        )

    @Test
    fun `a fetched ybRUNE position reaches the DeFi balances`() = runTest {
        coEvery { defaultStakingPositionService.getStakingDetails(ADDRESS, VAULT_ID) } returns
            flowOf(listOf(details(Coins.ThorChain.ybRUNE, BigInteger("523400000000"))))

        val balances = service.getRemoteDeFiBalance(ADDRESS, VAULT_ID)

        assertEquals(BigInteger("523400000000"), balances.amountFor(Coins.ThorChain.ybRUNE.id))
    }

    @Test
    fun `a cached ybRUNE position reaches the DeFi balances`() = runTest {
        coEvery { stakingDetailsRepository.getStakingDetails(VAULT_ID) } returns
            listOf(details(Coins.ThorChain.ybRUNE, BigInteger("77000000")))

        val balances = service.getCacheDeFiBalance(ADDRESS, VAULT_ID)

        assertEquals(BigInteger("77000000"), balances.amountFor(Coins.ThorChain.ybRUNE.id))
    }

    private fun List<com.vultisig.wallet.data.blockchain.model.DeFiBalance>.amountFor(
        coinId: String
    ): BigInteger =
        flatMap { it.balances }.single { it.coin.id.equals(coinId, ignoreCase = true) }.amount

    private fun details(coin: com.vultisig.wallet.data.models.Coin, amount: BigInteger) =
        StakingDetails(
            id = coin.id,
            coin = coin,
            stakeAmount = amount,
            apr = null,
            estimatedRewards = null,
            nextPayoutDate = null,
            rewards = null,
            rewardsCoin = null,
        )

    private companion object {
        const val ADDRESS = "thor1mtqtupwgjwn397w3dx9fqmqgzrjcal5yxz8q7v"
        const val VAULT_ID = "vault-1"
    }
}
