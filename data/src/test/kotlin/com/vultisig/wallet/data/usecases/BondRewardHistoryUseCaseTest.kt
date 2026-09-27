package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaBondProvider
import com.vultisig.wallet.data.api.MayaBondProviders
import com.vultisig.wallet.data.api.MayaNodeInfo
import com.vultisig.wallet.data.api.models.thorchain.BondProvider
import com.vultisig.wallet.data.api.models.thorchain.BondProviders
import com.vultisig.wallet.data.api.models.thorchain.ChurnEntry
import com.vultisig.wallet.data.api.models.thorchain.NodeDetailsResponse
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.ThorchainBondRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class BondRewardHistoryUseCaseTest {

    private lateinit var thorchainBondRepository: ThorchainBondRepository
    private lateinit var mayachainBondRepository: MayachainBondRepository
    private lateinit var useCase: BondRewardHistoryUseCaseImpl

    @BeforeEach
    fun setUp() {
        thorchainBondRepository = mockk()
        mayachainBondRepository = mockk()
        useCase = BondRewardHistoryUseCaseImpl(thorchainBondRepository, mayachainBondRepository)
    }

    @Test
    fun `the last churn pays the award after the operator fee, split by bond share`() = runTest {
        // Live THORNode reading one block before churn 27914370 (2026-09-20): a 1033.42 RUNE
        // award, a 2000 bps operator fee, and a provider holding ~26.3% of the node's bond.
        coEvery { thorchainBondRepository.getChurns() } returns
            listOf(churn(height = 27_914_370, dateMillis = 1_789_925_117_543L))
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 27_914_369) } returns
            thorNode(
                award = "103341961766",
                operatorFeeBps = "2000",
                providers = mapOf(ME to "29511174914053", OTHERS to "82796569644030"),
            )

        val reward = useCase.getLastReward(Chain.ThorChain, NODE, ME)

        requireNotNull(reward)
        assertEquals(BigInteger("21724184536"), reward.amount) // 217.24184536 RUNE
        assertEquals(27_914_370L, reward.churnHeight)
        assertEquals(Date(1_789_925_117_543L), reward.date)
    }

    @Test
    fun `no last reward when the vault was not a bond provider at the last churn`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(height = 100))
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) } returns
            thorNode(award = "1000", providers = mapOf(OTHERS to "10"))

        assertNull(useCase.getLastReward(Chain.ThorChain, NODE, ME))
    }

    @Test
    fun `no last reward when the chain has never churned`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns emptyList()

        assertNull(useCase.getLastReward(Chain.ThorChain, NODE, ME))
    }

    @Test
    fun `history is newest first, skips zero payouts and stops where the vault was not bonded`() =
        runTest {
            // Out of order on purpose: the walk must not trust the endpoint's ordering.
            coEvery { thorchainBondRepository.getChurns() } returns
                listOf(churn(300), churn(500), churn(100), churn(400), churn(200))
            stubThorNode(height = 499, award = "1000", myBond = "1", othersBond = "1")
            // Standby: the node earned nothing that churn.
            stubThorNode(height = 399, award = "0", myBond = "1", othersBond = "1")
            stubThorNode(height = 299, award = "600", myBond = "1", othersBond = "2")
            stubThorNode(height = 199, award = "1000", myBond = null, othersBond = "1")
            // Older than the gap: it belongs to an earlier position the vault has since left.
            stubThorNode(height = 99, award = "1000", myBond = "1", othersBond = "1")

            val history = useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

            assertEquals(listOf(500L, 300L), history.map { it.churnHeight })
            assertEquals(listOf(BigInteger("500"), BigInteger("200")), history.map { it.amount })
        }

    @Test
    fun `history reads at most the last twenty churns`() = runTest {
        val churns = (1..25).map { churn(height = it * 100L) }
        coEvery { thorchainBondRepository.getChurns() } returns churns
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, any()) } returns
            thorNode(award = "10", providers = mapOf(ME to "1"))

        val history = useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

        assertEquals(BondRewardHistoryUseCase.MAX_CHURNS, history.size)
        assertEquals(2_500L, history.first().churnHeight)
        assertEquals(600L, history.last().churnHeight)
        coVerify(exactly = 0) { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 499) }
    }

    @Test
    fun `a node at a past height is read once and reused`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(height = 100))
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) } returns
            thorNode(award = "1000", providers = mapOf(ME to "1"))

        useCase.getLastReward(Chain.ThorChain, NODE, ME)
        useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

        coVerify(exactly = 1) { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) }
    }

    @Test
    fun `a failed height read fails the whole history rather than understate it`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(200), churn(100))
        stubThorNode(height = 199, award = "1000", myBond = "1", othersBond = "1")
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) } throws
            IllegalStateException("No archive nodes configured for this chain")

        assertThrows<IllegalStateException> { useCase.getRewardHistory(Chain.ThorChain, NODE, ME) }
    }

    @Test
    fun `maya reads the node's reward field and weighs providers by bonded LP units`() = runTest {
        coEvery { mayachainBondRepository.getChurns() } returns listOf(churn(height = 17_904_023))
        // Live MAYANode reading one block before churn 17904023: a 5000 bps operator fee, and the
        // vault bonding BTC LP units against a provider holding 3 RUNE LP units.
        coEvery { mayachainBondRepository.getNodeDetailsAtHeight(NODE, 17_904_022) } returns
            MayaNodeInfo(
                nodeAddress = NODE,
                status = "Active",
                reward = "7021264386631",
                bondProviders =
                    MayaBondProviders(
                        nodeOperatorFee = "5000",
                        providers =
                            listOf(
                                MayaBondProvider(
                                    bondAddress = OTHERS,
                                    pools = mapOf("THOR.RUNE" to "3"),
                                ),
                                MayaBondProvider(
                                    bondAddress = ME,
                                    pools = mapOf("BTC.BTC" to "3634299242418724"),
                                ),
                            ),
                    ),
            )

        val reward = useCase.getLastReward(Chain.MayaChain, NODE, ME)

        // The same figure MAYANode reports as this provider's own `reward` at that height.
        assertEquals(BigInteger("3510632193315"), reward?.amount)
    }

    @Test
    fun `a zero-bond node pays nothing instead of dividing by zero`() {
        val snapshot =
            NodeRewardSnapshot(
                award = BigInteger("1000"),
                operatorFeeBps = BigInteger.ZERO,
                providerBonds = mapOf(ME to BigInteger.ZERO),
            )

        assertEquals(BigInteger.ZERO, snapshot.providerShare(ME))
        assertNull(snapshot.providerShare(OTHERS))
    }

    @Test
    fun `an operator fee above 100 percent leaves providers nothing, never a negative share`() {
        val snapshot =
            NodeRewardSnapshot(
                award = BigInteger("1000"),
                operatorFeeBps = BigInteger("12000"),
                providerBonds = mapOf(ME to BigInteger.ONE),
            )

        assertEquals(BigInteger.ZERO, snapshot.providerShare(ME))
    }

    private fun stubThorNode(height: Long, award: String, myBond: String?, othersBond: String) {
        val providers = buildMap {
            if (myBond != null) put(ME, myBond)
            put(OTHERS, othersBond)
        }
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, height) } returns
            thorNode(award = award, providers = providers)
    }

    private fun thorNode(
        award: String,
        operatorFeeBps: String = "0",
        providers: Map<String, String>,
    ) =
        NodeDetailsResponse(
            nodeAddress = NODE,
            status = "Active",
            currentAward = award,
            bondProviders =
                BondProviders(
                    nodeOperatorFee = operatorFeeBps,
                    providers = providers.map { (address, bond) -> BondProvider(address, bond) },
                ),
        )

    private fun churn(height: Long, dateMillis: Long = height * 1_000L) =
        ChurnEntry(date = (dateMillis * 1_000_000L).toString(), height = height.toString())

    private companion object {
        const val NODE = "thor10czf2s89h79fsjmqqck85cdqeq536hw5ngz4lt"
        const val ME = "thor18zg6y8ylus8n3tpzu5xxge3yyquj03vylstrl3"
        const val OTHERS = "thor1hxrydan0eypp5eutah2dt63swnqwzuvc0elgmd"
    }
}
