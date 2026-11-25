package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.MayaBondProvider
import com.vultisig.wallet.data.api.MayaBondProviders
import com.vultisig.wallet.data.api.MayaNodeInfo
import com.vultisig.wallet.data.api.models.thorchain.BondProvider
import com.vultisig.wallet.data.api.models.thorchain.BondProviders
import com.vultisig.wallet.data.api.models.thorchain.ChurnEntry
import com.vultisig.wallet.data.api.models.thorchain.MidgardActionInput
import com.vultisig.wallet.data.api.models.thorchain.MidgardUnbondActionsResponse
import com.vultisig.wallet.data.api.models.thorchain.MidgardBondMetadata
import com.vultisig.wallet.data.api.models.thorchain.MidgardUnbondAction
import com.vultisig.wallet.data.api.models.thorchain.MidgardUnbondMetadata
import com.vultisig.wallet.data.api.models.thorchain.NodeDetailsResponse
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.BondRewardCacheRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.ThorchainBondRepository
import com.vultisig.wallet.data.sources.AppDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.TestTimeSource
import kotlin.time.asClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class BondRewardHistoryUseCaseTest {

    private lateinit var thorchainBondRepository: ThorchainBondRepository
    private lateinit var mayachainBondRepository: MayachainBondRepository
    private lateinit var deviceStore: AppDataStore
    private lateinit var timeSource: TestTimeSource
    private lateinit var clock: Clock
    private lateinit var useCase: BondRewardHistoryUseCaseImpl

    @BeforeEach
    fun setUp() {
        thorchainBondRepository = mockk()
        mayachainBondRepository = mockk()
        deviceStore = FakeAppDataStore()
        timeSource = TestTimeSource()
        clock = timeSource.asClock(origin = Instant.fromEpochMilliseconds(NOW_MILLIS))
        useCase = newUseCase()
    }

    private fun newUseCase() =
        BondRewardHistoryUseCaseImpl(
            thorchainBondRepository,
            mayachainBondRepository,
            BondRewardCacheRepository(deviceStore),
            clock,
        )

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
            assertEquals(
                listOf(BigInteger("500"), BigInteger("200")),
                history.map { it.amount },
            )
        }

    @Test
    fun `the node is only asked about churns from the last 13 days`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns
            listOf(churn(300, daysAgo = 1), churn(200, daysAgo = 12), churn(100, daysAgo = 14))
        stubThorNode(height = 299, award = "1000", myBond = "1", othersBond = "1")
        stubThorNode(height = 199, award = "1000", myBond = "1", othersBond = "1")

        val history = useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

        assertEquals(listOf(300L, 200L), history.map { it.churnHeight })
        coVerify(exactly = 0) { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) }
    }

    @Test
    fun `saved churns older than the window stay in the history, merged with new ones`() =
        runTest {
            coEvery { thorchainBondRepository.getChurns() } returns
                listOf(churn(300), churn(200), churn(100))
            stubThorNode(height = 299, award = "1000", myBond = "1", othersBond = "1")
            stubThorNode(height = 199, award = "1000", myBond = "1", othersBond = "1")
            stubThorNode(height = 99, award = "1000", myBond = "1", othersBond = "1")
            useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

            // Thirty days on, everything above is outside the window; churn 400 is new.
            timeSource += 30.days
            coEvery { thorchainBondRepository.getChurns() } returns
                listOf(churn(400, daysAgo = -29), churn(300), churn(200), churn(100))
            stubThorNode(height = 399, award = "600", myBond = "1", othersBond = "2")
            val history = newUseCase().getRewardHistory(Chain.ThorChain, NODE, ME)

            assertEquals(listOf(400L, 300L, 200L, 100L), history.map { it.churnHeight })
            coVerify(exactly = 1) { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) }
            coVerify(exactly = 0) { thorchainBondRepository.getUnbondActions(any(), any(), any()) }
        }

    @Test
    fun `the device keeps every churn read, not only the latest twenty`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns (1..25).map { churn(it * 100L) }
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, any()) } returns
            thorNode(award = "10", providers = mapOf(ME to "1"))
        useCase.getRewardHistory(Chain.ThorChain, NODE, ME)
        timeSource += 30.days

        val history = newUseCase().getRewardHistory(Chain.ThorChain, NODE, ME)

        assertEquals(25, history.size)
    }

    @Test
    fun `a churn missed while the app was closed does not hide the saved ones`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(200), churn(100))
        stubThorNode(height = 199, award = "1000", myBond = "1", othersBond = "1")
        stubThorNode(height = 99, award = "1000", myBond = "1", othersBond = "1")
        useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

        // Churn 300 happened 14 days ago and the app was not opened while it could be read.
        timeSource += 30.days
        coEvery { thorchainBondRepository.getChurns() } returns
            listOf(churn(300, daysAgo = -16), churn(200), churn(100))
        coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, null) } returns emptyList()
        val history = newUseCase().getRewardHistory(Chain.ThorChain, NODE, ME)

        assertEquals(listOf(200L, 100L), history.map { it.churnHeight })
        coVerify(exactly = 0) { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 299) }
    }

    @Test
    fun `a gap with no unbond from the node is bridged, asking Midgard once`() = runTest {
        coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns emptyList()

        val history = openAfterAGap()

        assertEquals(listOf(400L, 200L, 100L), history.map { it.churnHeight })
        coVerify(exactly = 1) { thorchainBondRepository.getUnbondActions(any(), any(), any()) }
    }

    @Test
    fun `an unbond the bond address sent itself ends the history at the gap`() = runTest {
        coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns
            listOf(unbond(height = 350, memoProvider = null, sender = ME))

        val history = openAfterAGap()

        assertEquals(listOf(400L), history.map { it.churnHeight })
    }

    @Test
    fun `an unbond in the block of the churn below the gap ends the history at the gap`() =
        runTest {
            // Churn 200's reward was read at block 199, so an unbond at 200 came after it.
            coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns
                listOf(unbond(height = 200, memoProvider = null, sender = ME))

            val history = openAfterAGap()

            assertEquals(listOf(400L), history.map { it.churnHeight })
        }

    @Test
    fun `an unbond in the block of the churn above the gap does not end the history`() =
        runTest {
            // Churn 400's reward was read at block 399, so the position held past an unbond at 400.
            coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns
                listOf(unbond(height = 400, memoProvider = null, sender = ME))

            val history = openAfterAGap()

            assertEquals(listOf(400L, 200L, 100L), history.map { it.churnHeight })
        }

    @Test
    fun `a gap above only a zero payout has nothing to show and says so`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(100))
        // Standby: the node earned nothing that churn.
        stubThorNode(height = 99, award = "0", myBond = "1", othersBond = "1")
        useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

        timeSource += 30.days
        coEvery { thorchainBondRepository.getChurns() } returns
            listOf(churn(200, daysAgo = -16), churn(100))
        coEvery { thorchainBondRepository.getUnbondActions(NODE, 100, null) } returns emptyList()

        assertThrows<IllegalStateException> {
            newUseCase().getRewardHistory(Chain.ThorChain, NODE, ME)
        }
    }

    @Test
    fun `an unbond the operator sent naming this provider ends the history at the gap`() =
        runTest {
            coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns
                listOf(unbond(height = 350, memoProvider = ME, sender = OPERATOR))

            val history = openAfterAGap()

            assertEquals(listOf(400L), history.map { it.churnHeight })
            coVerify(exactly = 1) { thorchainBondRepository.getUnbondActions(NODE, 200, 400) }
        }

    @Test
    fun `a provider named in upper case in the memo still ends the history`() = runTest {
        coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns
            listOf(unbond(height = 350, memoProvider = ME.uppercase(), sender = OPERATOR))

        val history = openAfterAGap()

        assertEquals(listOf(400L), history.map { it.churnHeight })
        coVerify(exactly = 1) { thorchainBondRepository.getUnbondActions(NODE, 200, 400) }
    }

    @Test
    fun `an unbond this address sent as operator for someone else does not end the history`() =
        runTest {
            coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns
                listOf(unbond(height = 350, memoProvider = OTHERS, sender = ME))

            val history = openAfterAGap()

            assertEquals(listOf(400L, 200L, 100L), history.map { it.churnHeight })
        }

    @Test
    fun `an unreadable height on any unbond ends the history at the gap`() = runTest {
        coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns
            listOf(unbond(height = 350, memoProvider = OTHERS, sender = OPERATOR).copy(height = "?"))

        val history = openAfterAGap()

        assertEquals(listOf(400L), history.map { it.churnHeight })
    }

    @Test
    fun `a midgard reply without actions is not read as no unbonds`() {
        val json = Json { ignoreUnknownKeys = true }

        assertThrows<SerializationException> {
            json.decodeFromString<MidgardUnbondActionsResponse>("{}")
        }
        assertEquals(
            emptyList<MidgardUnbondAction>(),
            json.decodeFromString<MidgardUnbondActionsResponse>("""{"actions":[]}""").actions,
        )
    }

    @Test
    fun `a maya memo that names only the node leaves the provider to the sender`() {
        // Live MAYAChain 19195939: the 4th field is the node, the sender is the provider.
        val node = "maya1hv34rnqhh9nv0cqpfglaq7j77d6a72jca573sz"
        val sender = "maya13yseu9un5f9gwqgzshjqvsqrxew0hhgm3wjh4l"

        val provider =
            action("UNBOND:BTC.BTC:455300000000000:$node", sender).provider(Chain.MayaChain, node)

        assertEquals(sender, provider)
    }

    @Test
    fun `a maya memo from the operator names the provider after the node`() {
        // Live MAYAChain 16651453: the operator sent it; the provider is the 5th field.
        val node = "maya1dhsycanx5dynhnx773qdx38t8kwr7lvkqzzzfq"
        val provider = "maya19sf28pj3lj2gwt2a9rlatl3qma97cn8tj73ml0"
        val operator = "maya1j8p70vdl8wk9gva6u28ru6c73lacukfny2ea7d"

        assertEquals(
            provider,
            action("UNBOND:::$node:$provider", operator).provider(Chain.MayaChain, node),
        )
    }

    @Test
    fun `an older maya memo names the provider right after the node`() {
        // Live MAYAChain 8506670, a grammar Mayanode still accepts, sent by the node itself.
        val node = "maya1n4ukxgkhv8z05wa0cutr0huygwqm6txdx7axal"
        val provider = "maya1ya67wn0v8jq9dfc6vyjvl6fvzf89u9ew99602r"

        assertEquals(
            provider,
            action("UNBOND:$node:$provider", node).provider(Chain.MayaChain, node),
        )
    }

    @Test
    fun `a thorchain memo without a provider leaves it to the sender`() {
        assertEquals(ME, action("UNBOND:$NODE:10000000000000", ME).provider(Chain.ThorChain, NODE))
    }

    @Test
    fun `an unbond the operator sent for another provider does not end the history`() = runTest {
        coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns
            listOf(unbond(height = 350, memoProvider = OTHERS, sender = OPERATOR))

        val history = openAfterAGap()

        assertEquals(listOf(400L, 200L, 100L), history.map { it.churnHeight })
        coVerify(exactly = 1) { thorchainBondRepository.getUnbondActions(any(), any(), any()) }
    }

    @Test
    fun `when Midgard cannot say, the history ends at the gap without an error`() = runTest {
        coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } throws PRUNED

        val history = openAfterAGap()

        assertEquals(listOf(400L), history.map { it.churnHeight })
    }

    @Test
    fun `a full page of other providers' unbonds still ends the history at the gap`() = runTest {
        // Newest first and capped at a page: an unbond below the oldest one shown could be missing.
        coEvery { thorchainBondRepository.getUnbondActions(NODE, 200, 400) } returns
            (351L..400L).map { unbond(height = it, memoProvider = OTHERS, sender = OPERATOR) }

        val history = openAfterAGap()

        assertEquals(listOf(400L), history.map { it.churnHeight })
    }

    /** Churns 200 and 100 saved; 30 days on, churn 300 was missed and churn 400 is new. */
    private suspend fun openAfterAGap(): List<BondChurnReward> {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(200), churn(100))
        stubThorNode(height = 199, award = "1000", myBond = "1", othersBond = "1")
        stubThorNode(height = 99, award = "1000", myBond = "1", othersBond = "1")
        useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

        timeSource += 30.days
        coEvery { thorchainBondRepository.getChurns() } returns
            listOf(
                churn(400, daysAgo = -29),
                churn(300, daysAgo = -16),
                churn(200),
                churn(100),
            )
        stubThorNode(height = 399, award = "1000", myBond = "1", othersBond = "1")
        return newUseCase().getRewardHistory(Chain.ThorChain, NODE, ME)
    }

    @Test
    fun `nothing to show when the only churn is too old and was never saved`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(100, daysAgo = 14))

        assertThrows<IllegalStateException> { useCase.getRewardHistory(Chain.ThorChain, NODE, ME) }
        assertNull(useCase.getLastReward(Chain.ThorChain, NODE, ME))
        coVerify(exactly = 0) { thorchainBondRepository.getNodeDetailsAtHeight(NODE, any()) }
    }

    @Test
    fun `the last reward comes from the device once churning has halted`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(100))
        stubThorNode(height = 99, award = "1000", myBond = "1", othersBond = "1")
        useCase.getRewardHistory(Chain.ThorChain, NODE, ME)
        timeSource += 20.days

        val reward = newUseCase().getLastReward(Chain.ThorChain, NODE, ME)

        assertEquals(BigInteger("500"), reward?.amount)
        coVerify(exactly = 1) { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) }
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
    fun `a pruned height keeps only the churns newer than it`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns
            listOf(churn(300), churn(200), churn(100))
        stubThorNode(height = 299, award = "1000", myBond = "1", othersBond = "1")
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 199) } throws PRUNED
        // Readable but older than the unread churn, which may be where this position began.
        stubThorNode(height = 99, award = "1000", myBond = "1", othersBond = "1")

        val history = useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

        assertEquals(listOf(300L), history.map { it.churnHeight })
    }

    @Test
    fun `a history with no churn read at all throws`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(100))
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) } throws PRUNED

        assertThrows<IllegalStateException> { useCase.getRewardHistory(Chain.ThorChain, NODE, ME) }
    }

    @Test
    fun `churns read once are served from the device after the node prunes them`() = runTest {
        coEvery { thorchainBondRepository.getChurns() } returns listOf(churn(200), churn(100))
        // Standby: a zero payout is kept too, or its pruning would end the walk before 100.
        stubThorNode(height = 199, award = "0", myBond = "1", othersBond = "1")
        stubThorNode(height = 99, award = "1000", myBond = "1", othersBond = "1")
        useCase.getRewardHistory(Chain.ThorChain, NODE, ME)
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, any()) } throws PRUNED

        // A new process: nothing in memory, only what the device kept.
        val history = newUseCase().getRewardHistory(Chain.ThorChain, NODE, ME)

        assertEquals(listOf(100L), history.map { it.churnHeight })
        coVerify(exactly = 1) { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 199) }
        coVerify(exactly = 1) { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) }
    }

    @Test
    fun `a failed read older than the end of the walk does not fail the history`() = runTest {
        // One batch: 199 ends the walk, so 99's failure belongs to a position the vault has left.
        coEvery { thorchainBondRepository.getChurns() } returns
            listOf(churn(300), churn(200), churn(100))
        stubThorNode(height = 299, award = "1000", myBond = "1", othersBond = "1")
        stubThorNode(height = 199, award = "1000", myBond = null, othersBond = "1")
        coEvery { thorchainBondRepository.getNodeDetailsAtHeight(NODE, 99) } throws
            IllegalStateException("No archive nodes configured for this chain")

        val history = useCase.getRewardHistory(Chain.ThorChain, NODE, ME)

        assertEquals(listOf(300L), history.map { it.churnHeight })
    }

    @Test
    fun `maya takes the provider's own reward row, not an LP-unit share of the node award`() =
        runTest {
            coEvery { mayachainBondRepository.getChurns() } returns
                listOf(churn(height = 17_904_023))
            // Live MAYANode reading one block before churn 17904023. LP units in different pools
            // are not proportional to `bond`, so weighing by `pools` would pay 5948869444332.
            coEvery { mayachainBondRepository.getNodeDetailsAtHeight(NODE, 17_904_022) } returns
                MayaNodeInfo(
                    nodeAddress = NODE,
                    status = "Active",
                    reward = "7000694276124",
                    bondProviders =
                        MayaBondProviders(
                            nodeOperatorFee = "1500",
                            providers =
                                listOf(
                                    MayaBondProvider(
                                        bondAddress = OTHERS,
                                        reward = "1050300962182",
                                        pools = mapOf("THOR.RUNE" to "100999092998"),
                                    ),
                                    MayaBondProvider(
                                        bondAddress = ME,
                                        reward = "5950393313942",
                                        pools = mapOf("ARB.USDT" to "349179856971119"),
                                    ),
                                ),
                        ),
                )

            val reward = useCase.getLastReward(Chain.MayaChain, NODE, ME)

            assertEquals(BigInteger("5950393313942"), reward?.amount)
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

    /** A THORChain unbond from [NODE], naming [memoProvider] in its memo when not null. */
    private fun unbond(height: Long, memoProvider: String?, sender: String) =
        action(
            memo = listOfNotNull("UNBOND", NODE, "100", memoProvider).joinToString(":"),
            sender = sender,
            height = height,
        )

    private fun action(memo: String, sender: String, height: Long = 1) =
        MidgardUnbondAction(
            height = height.toString(),
            inputs = listOf(MidgardActionInput(address = sender)),
            metadata = MidgardUnbondMetadata(MidgardBondMetadata(memo = memo)),
        )

    private fun churn(height: Long, daysAgo: Int = 1) =
        churn(height, dateMillis = NOW_MILLIS - daysAgo.days.inWholeMilliseconds)

    private fun churn(height: Long, dateMillis: Long) =
        ChurnEntry(date = (dateMillis * 1_000_000L).toString(), height = height.toString())

    private class FakeAppDataStore : AppDataStore {
        private val prefs = MutableStateFlow<Preferences>(emptyPreferences())

        override suspend fun editData(
            transform: suspend (MutablePreferences) -> Unit
        ): Preferences {
            val mutable = prefs.value.toMutablePreferences()
            transform(mutable)
            return mutable.toPreferences().also { prefs.value = it }
        }

        override fun <T> readData(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
            prefs.map { it[key] ?: defaultValue }

        override suspend fun <T> set(key: Preferences.Key<T>, value: T) {
            editData { it[key] = value }
        }

        override fun <T> readData(key: Preferences.Key<T>): Flow<T?> = prefs.map { it[key] }
    }

    private companion object {
        // 2026-09-21: a day after the live churn the first test reads.
        const val NOW_MILLIS = 1_790_000_000_000L
        val PRUNED =
            IllegalStateException("failed to load state at height 99; no commit info found")
        const val NODE = "thor10czf2s89h79fsjmqqck85cdqeq536hw5ngz4lt"
        // Live: the operator of thor1kchgh8…ms65a, which unbonds for its providers.
        const val OPERATOR = "thor1y0tksvjjwhvc7ltzce8k80t7sj8pt2zhpx2ku2"
        const val ME = "thor18zg6y8ylus8n3tpzu5xxge3yyquj03vylstrl3"
        const val OTHERS = "thor1hxrydan0eypp5eutah2dt63swnqwzuvc0elgmd"
    }
}
