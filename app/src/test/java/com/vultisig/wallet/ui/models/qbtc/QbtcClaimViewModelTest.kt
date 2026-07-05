@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.qbtc

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.ClaimProofResponse
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.ClaimableUtxo
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.LoadClaimableQbtcUtxosUseCase
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBlockedReason
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimBtcRoundResult
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimError
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcClaimLoadResult
import com.vultisig.wallet.data.blockchain.cosmos.qbtc.claim.QbtcProofService
import com.vultisig.wallet.data.mappers.PayloadToProtoMapper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.qbtc.QbtcClaimFastVaultRoundRunner
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.ui.models.keysign.MissingQbtcClaimAccountException
import com.vultisig.wallet.ui.models.keysign.QbtcClaimCoins
import com.vultisig.wallet.ui.models.keysign.ResolveQbtcClaimCoinsUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class QbtcClaimViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var navigator: Navigator<Destination>
    private lateinit var vaultRepository: VaultRepository
    private lateinit var resolveQbtcClaimCoins: ResolveQbtcClaimCoinsUseCase
    private lateinit var loadClaimableUtxos: LoadClaimableQbtcUtxosUseCase
    private lateinit var proofService: QbtcProofService
    private lateinit var roundRunner: QbtcClaimFastVaultRoundRunner
    private lateinit var explorerLinkRepository: ExplorerLinkRepository
    private lateinit var sessionApi: SessionApi
    private lateinit var discoverParticipants: DiscoverParticipantsUseCase
    private lateinit var payloadToProtoMapper: PayloadToProtoMapper
    private lateinit var compressQr: CompressQrUseCase
    private lateinit var generateQrBitmap: GenerateQrBitmap
    private lateinit var generateServiceName: GenerateServiceName
    private lateinit var encryption: Encryption
    private lateinit var protoBuf: ProtoBuf
    private lateinit var json: Json

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        every { any<SavedStateHandle>().toRoute<Route.QbtcClaim>() } returns
            Route.QbtcClaim(vaultId = VAULT_ID)
        navigator = mockk(relaxed = true)
        vaultRepository = mockk(relaxed = true)
        resolveQbtcClaimCoins = mockk()
        loadClaimableUtxos = mockk(relaxed = true)
        proofService = mockk(relaxed = true)
        roundRunner = mockk(relaxed = true)
        explorerLinkRepository = mockk(relaxed = true)
        sessionApi = mockk(relaxed = true)
        discoverParticipants = mockk(relaxed = true)
        payloadToProtoMapper = mockk(relaxed = true)
        compressQr = mockk(relaxed = true)
        generateQrBitmap = mockk(relaxed = true)
        generateServiceName = mockk(relaxed = true)
        encryption = mockk(relaxed = true)
        protoBuf = mockk(relaxed = true)
        json = mockk(relaxed = true)
        coEvery { vaultRepository.get(VAULT_ID) } returns fastVault()
        coEvery { resolveQbtcClaimCoins(any()) } returns
            QbtcClaimCoins(btc = btcCoin(), qbtc = qbtcCoin())
        every { explorerLinkRepository.getTransactionLink(Chain.Qbtc, any()) } returns ""
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        QbtcClaimViewModel(
            savedStateHandle = SavedStateHandle(),
            navigator = navigator,
            vaultRepository = vaultRepository,
            resolveQbtcClaimCoins = resolveQbtcClaimCoins,
            loadClaimableUtxos = loadClaimableUtxos,
            proofService = proofService,
            fastVaultRoundRunner = roundRunner,
            explorerLinkRepository = explorerLinkRepository,
            sessionApi = sessionApi,
            discoverParticipants = discoverParticipants,
            payloadToProtoMapper = payloadToProtoMapper,
            compressQr = compressQr,
            generateQrBitmap = generateQrBitmap,
            generateServiceName = generateServiceName,
            encryption = encryption,
            protoBuf = protoBuf,
            json = json,
        )

    @Test
    fun `load preselects every claimable UTXO when at or below the cap`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Available(utxos(3))

            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.uiState.value as QbtcClaimUiState.Selecting
            assertEquals(3, state.utxos.size)
            assertEquals(3, state.selectedKeys.size)
            assertTrue(state.canConfirm)
            assertTrue(state.isAllSelected)
        }

    @Test
    fun `load caps the initial selection at the claim max so Confirm stays enabled`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Available(utxos(60))

            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.uiState.value as QbtcClaimUiState.Selecting
            assertEquals(60, state.utxos.size)
            assertEquals(MAX_CLAIM_UTXOS, state.selectedKeys.size)
            assertTrue(state.canConfirm)
            assertTrue(state.isAllSelected)
        }

    @Test
    fun `load maps a blocked reason to the blocked state`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Blocked(QbtcClaimBlockedReason.NoUtxos)

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(QbtcClaimUiState.Blocked(QbtcClaimBlockedReason.NoUtxos), vm.uiState.value)
        }

    @Test
    fun `load maps a maturing result to the maturing state with confirmation and block counts`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Maturing(
                    listOf(
                        ClaimableUtxo(
                            txid = "ab".repeat(32),
                            vout = 0,
                            amount = 75_000_000,
                            confirmations = 112,
                        ),
                        // Unconfirmed (mempool) UTXO → shown as 0 confirmations.
                        ClaimableUtxo(
                            txid = "cd".repeat(32),
                            vout = 2,
                            amount = 25_000_000,
                            confirmations = null,
                        ),
                    )
                )

            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.uiState.value as QbtcClaimUiState.Maturing
            assertEquals(2, state.utxos.size)
            assertEquals("112/144", state.utxos[0].confirmationsCount)
            assertEquals(32L, state.utxos[0].remainingBlocks)
            assertEquals("0/144", state.utxos[1].confirmationsCount)
            assertEquals(144L, state.utxos[1].remainingBlocks)
        }

    @Test
    fun `load blocks when the claim accounts cannot be resolved`() =
        runTest(testDispatcher) {
            // Neither chain is enabled and derivation can't produce the account → resolver throws.
            coEvery { vaultRepository.get(VAULT_ID) } returns fastVault().copy(coins = emptyList())
            coEvery { resolveQbtcClaimCoins(any()) } throws
                MissingQbtcClaimAccountException(Chain.Qbtc)

            val vm = viewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is QbtcClaimUiState.Blocked)
        }

    @Test
    fun `toggle deselects and reselects without exceeding the cap`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Available(utxos(60))
            val vm = viewModel()
            advanceUntilIdle()

            val firstSelected =
                (vm.uiState.value as QbtcClaimUiState.Selecting).selectedKeys.first()
            vm.toggle(firstSelected)
            var state = vm.uiState.value as QbtcClaimUiState.Selecting
            assertEquals(MAX_CLAIM_UTXOS - 1, state.selectedKeys.size)
            assertFalse(firstSelected in state.selectedKeys)

            // A 60th key was never selected (cap was 50); selecting one is allowed again now.
            val unselected =
                utxos(60).map { "${it.txid}:${it.vout}" }.first { it !in state.selectedKeys }
            vm.toggle(unselected)
            state = vm.uiState.value as QbtcClaimUiState.Selecting
            assertEquals(MAX_CLAIM_UTXOS, state.selectedKeys.size)
        }

    @Test
    fun `confirm runs the BTC round then maps a proof mismatch to a failure`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Available(utxos(2))
            coEvery { roundRunner.run(any()) } returns
                QbtcClaimBtcRoundResult(rHex = "01".repeat(24), sHex = "02".repeat(32))
            // Hashes that won't match the locally-computed ones → orchestrator fails the echo
            // check.
            coEvery { proofService.generateProof(any()) } returns
                ClaimProofResponse(
                    proof = "ff00",
                    messageHash = "bb".repeat(32),
                    addressHash = "cc".repeat(20),
                    qbtcAddressHash = "dd".repeat(32),
                    txHash = "ab".repeat(32),
                )

            val vm = viewModel()
            advanceUntilIdle()
            vm.confirm(fastVaultPassword = "pw")
            advanceUntilIdle()

            coVerify(exactly = 1) { roundRunner.run(any()) }
            assertEquals(
                QbtcClaimUiState.Failed(QbtcClaimError.PROOF_HASH_MISMATCH),
                vm.uiState.value,
            )
        }

    @Test
    fun `confirm surfaces a generic failure when the BTC round throws`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Available(utxos(2))
            coEvery { roundRunner.run(any()) } throws IllegalStateException("relay down")

            val vm = viewModel()
            advanceUntilIdle()
            vm.confirm(fastVaultPassword = "pw")
            advanceUntilIdle()

            assertEquals(QbtcClaimUiState.Failed(QbtcClaimError.GENERIC), vm.uiState.value)
            coVerify(exactly = 0) { proofService.generateProof(any()) }
        }

    @Test
    fun `confirm does nothing when nothing is selected`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Available(utxos(2))
            val vm = viewModel()
            advanceUntilIdle()
            (vm.uiState.value as QbtcClaimUiState.Selecting).selectedKeys.toList().forEach {
                vm.toggle(it)
            }

            vm.confirm(fastVaultPassword = "pw")
            advanceUntilIdle()

            coVerify(exactly = 0) { roundRunner.run(any()) }
            assertTrue(vm.uiState.value is QbtcClaimUiState.Selecting)
        }

    @Test
    fun `confirm sets a validation error and never calls the orchestrator when the password is empty`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Available(utxos(2))
            val vm = viewModel()
            advanceUntilIdle()

            vm.confirm(fastVaultPassword = "")
            advanceUntilIdle()

            coVerify(exactly = 0) { roundRunner.run(any()) }
            val state = vm.uiState.value as QbtcClaimUiState.Selecting
            assertEquals(
                UiText.StringResource(R.string.password_should_not_be_empty),
                state.passwordError,
            )
        }

    @Test
    fun `retry reloads after a block`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Blocked(QbtcClaimBlockedReason.NoUtxos)
            val vm = viewModel()
            advanceUntilIdle()
            assertTrue(vm.uiState.value is QbtcClaimUiState.Blocked)

            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Available(utxos(1))
            vm.retry()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is QbtcClaimUiState.Selecting)
        }

    @Test
    fun `isFastVault reflects the loaded vault`() =
        runTest(testDispatcher) {
            coEvery { loadClaimableUtxos(BTC_ADDRESS) } returns
                QbtcClaimLoadResult.Available(utxos(1))
            val vm = viewModel()
            advanceUntilIdle()
            assertTrue(vm.isFastVault())
        }

    private fun btcCoin() =
        Coin.EMPTY.copy(chain = Chain.Bitcoin, address = BTC_ADDRESS, hexPublicKey = BTC_PUBKEY)

    private fun qbtcCoin() = Coin.EMPTY.copy(chain = Chain.Qbtc, address = QBTC_ADDRESS)

    private fun fastVault() =
        Vault(
            id = VAULT_ID,
            name = "Test",
            pubKeyMLDSA = "ab".repeat(32),
            localPartyID = "iPhone-1",
            signers = listOf("Server-1", "iPhone-1"),
            coins = listOf(btcCoin(), qbtcCoin()),
        )

    private fun utxos(n: Int): List<ClaimableUtxo> =
        List(n) { ClaimableUtxo(txid = "%064x".format(it), vout = 0, amount = 1_000L) }

    private companion object {
        const val VAULT_ID = "vault-1"
        const val BTC_ADDRESS = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
        const val BTC_PUBKEY = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        const val QBTC_ADDRESS = "qbtc1abc"
        const val MAX_CLAIM_UTXOS = 50
    }
}
