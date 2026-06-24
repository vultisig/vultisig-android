@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.governance

import com.vultisig.wallet.data.api.CosmosApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovProposal
import com.vultisig.wallet.data.api.models.cosmos.CosmosGovTallyResult
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class GovernanceViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vaultRepository: VaultRepository = mockk()
    private val cosmosApiFactory: CosmosApiFactory = mockk()
    private val cosmosApi: CosmosApi = mockk(relaxed = true)
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk(relaxed = true)
    private val depositTransactionRepository: DepositTransactionRepository = mockk(relaxed = true)
    private val navigator: Navigator<Destination> = mockk(relaxed = true)

    private val coin =
        Coin(
            chain = Chain.Qbtc,
            ticker = "QBTC",
            logo = "",
            address = "qbtc1voter",
            decimal = 8,
            hexPublicKey = "02".repeat(33),
            priceProviderID = "",
            contractAddress = "",
            isNativeToken = true,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { vaultRepository.get(any()) } returns
            Vault(
                id = "v1",
                name = "v",
                pubKeyECDSA = "pk",
                localPartyID = "lp",
                coins = listOf(coin),
            )
        coEvery { cosmosApiFactory.createCosmosApi(Chain.Qbtc) } returns cosmosApi
        coEvery { cosmosApi.getGovProposals(any()) } returns emptyList()
        coEvery { cosmosApi.getGovVote(any(), any()) } returns null
        coEvery { cosmosApi.getGovTally(any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() =
        GovernanceViewModel(
            vaultRepository = vaultRepository,
            cosmosApiFactory = cosmosApiFactory,
            blockChainSpecificRepository = blockChainSpecificRepository,
            depositTransactionRepository = depositTransactionRepository,
            navigator = navigator,
            ioDispatcher = testDispatcher,
        )

    private fun proposal(
        id: String,
        tally: CosmosGovTallyResult? = null,
        endsInDays: Long = 5,
    ): CosmosGovProposal =
        CosmosGovProposal(
            id = id,
            title = "Proposal $id",
            summary = "summary",
            finalTallyResult = tally,
            votingEndTime = Instant.now().plus(endsInDays, ChronoUnit.DAYS).toString(),
        )

    @Test
    fun `setData groups proposals by status`() = runTest {
        coEvery { cosmosApi.getGovProposals(3) } returns listOf(proposal("1"))

        val model = vm()
        model.setData("v1")

        model.state.value.passed.size shouldBe 1
        model.state.value.active.size shouldBe 0
        model.state.value.rejected.size shouldBe 0
    }

    @Test
    fun `an active proposal reads the live tally endpoint, not final_tally_result`() = runTest {
        // final_tally_result stays zero during the voting period, so the active card must read the
        // running counts from /tally instead.
        coEvery { cosmosApi.getGovProposals(2) } returns
            listOf(proposal("9", tally = null, endsInDays = 3))
        coEvery { cosmosApi.getGovTally("9") } returns
            CosmosGovTallyResult(
                yesCount = "1000",
                noCount = "0",
                abstainCount = "0",
                noWithVetoCount = "0",
            )

        val model = vm()
        model.setData("v1")

        val ui = model.state.value.active.first().tally
        ui.hasVotes shouldBe true
        ui.yesPercent shouldBe "100%"
    }

    @Test
    fun `an active proposal with an open voting window is votable`() = runTest {
        coEvery { cosmosApi.getGovProposals(2) } returns listOf(proposal("9", endsInDays = 3))

        val model = vm()
        model.setData("v1")

        model.state.value.active.first().isVotable shouldBe true
    }

    @Test
    fun `an active proposal past its voting window is not votable`() = runTest {
        coEvery { cosmosApi.getGovProposals(2) } returns listOf(proposal("9", endsInDays = -1))

        val model = vm()
        model.setData("v1")

        model.state.value.active.first().isVotable shouldBe false
    }

    @Test
    fun `tally computes fractions, percentages and the leading option`() = runTest {
        val tally =
            CosmosGovTallyResult(
                yesCount = "100",
                noCount = "50",
                abstainCount = "30",
                noWithVetoCount = "20",
            )
        coEvery { cosmosApi.getGovProposals(3) } returns listOf(proposal("1", tally = tally))

        val model = vm()
        model.setData("v1")

        val ui = model.state.value.passed.first().tally
        ui.hasVotes shouldBe true
        ui.yesPercent shouldBe "50%"
        ui.leadingOption shouldBe VoteOption.YES
    }

    @Test
    fun `a proposal with no votes reports an empty tally`() = runTest {
        coEvery { cosmosApi.getGovProposals(3) } returns listOf(proposal("1", tally = null))

        val model = vm()
        model.setData("v1")

        model.state.value.passed.first().tally.hasVotes shouldBe false
    }

    @Test
    fun `castVote stages a QBTC_VOTE deposit with the 800 fee and routes to verify`() = runTest {
        coEvery { cosmosApi.getGovProposals(2) } returns listOf(proposal("42", endsInDays = 3))
        val model = vm()
        model.setData("v1")

        val txSlot = slot<DepositTransaction>()
        val routeSlot = slot<Any>()
        coEvery { depositTransactionRepository.addTransaction(capture(txSlot)) } returns Unit
        coEvery { navigator.route(capture(routeSlot)) } returns Unit

        model.castVote("42", VoteOption.YES)

        txSlot.captured.memo shouldBe "QBTC_VOTE:YES:42"
        txSlot.captured.estimatedFees.value shouldBe BigInteger.valueOf(800)
        txSlot.captured.srcAddress shouldBe "qbtc1voter"
        (routeSlot.captured as Route.VerifyDeposit).transactionId shouldBe txSlot.captured.id
    }

    @Test
    fun `castVote is rejected when the proposal is not an open active one`() = runTest {
        // The window can close while the sheet is open; the late vote must not burn a keysign.
        val model = vm()
        model.setData("v1")

        model.castVote("42", VoteOption.YES)

        coVerify(exactly = 0) { depositTransactionRepository.addTransaction(any()) }
        model.state.value.error shouldNotBe null
    }

    @Test
    fun `fromWire maps the cosmos vote-option strings`() {
        VoteOption.fromWire("VOTE_OPTION_YES") shouldBe VoteOption.YES
        VoteOption.fromWire("VOTE_OPTION_NO_WITH_VETO") shouldBe VoteOption.NO_WITH_VETO
        VoteOption.fromWire("nope") shouldBe null
    }

    @Test
    fun `load surfaces an error when every proposal fetch fails`() = runTest {
        coEvery { cosmosApi.getGovProposals(any()) } throws RuntimeException("offline")

        val model = vm()
        model.setData("v1")

        model.state.value.error shouldNotBe null
    }
}
