@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.CosmosApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.models.cosmos.CosmosTxStatusJson
import com.vultisig.wallet.data.api.models.cosmos.TxResponse
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class KeysignViewModelTryUpdateTerraDisplayFeeTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var cosmosApiFactory: CosmosApiFactory
    private lateinit var cosmosApi: CosmosApi
    private lateinit var gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase

    private val vault = Vault(id = "v1", name = "Test Vault")
    private val lunaCoin =
        Coin(
            chain = Chain.Terra,
            ticker = "LUNA",
            logo = "luna",
            address = "terra1sender",
            decimal = 6,
            hexPublicKey = "",
            priceProviderID = "terra-luna-2",
            contractAddress = "",
            isNativeToken = true,
        )
    private val lunaKeysignPayload =
        KeysignPayload(
            coin = lunaCoin,
            toAddress = "terra1dest",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Cosmos(
                    accountNumber = BigInteger.ZERO,
                    sequence = BigInteger.ZERO,
                    gas = BigInteger.valueOf(7500),
                    ibcDenomTraces = null,
                    transactionType =
                        vultisig.keysign.v1.TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )
    private val gaiaCoin =
        lunaCoin.copy(chain = Chain.GaiaChain, ticker = "ATOM", address = "cosmos1sender")
    private val gaiaKeysignPayload = lunaKeysignPayload.copy(coin = gaiaCoin)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cosmosApiFactory = mockk(relaxed = true)
        cosmosApi = mockk(relaxed = true)
        gasFeeToEstimatedFee = mockk(relaxed = true)
        every { cosmosApiFactory.createCosmosApi(Chain.Terra) } returns cosmosApi
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(keysignPayload: KeysignPayload?) =
        KeysignViewModel(
            vault = vault,
            keysignCommittee = emptyList(),
            serverUrl = "",
            sessionId = "",
            encryptionKeyHex = "",
            messagesToSign = emptyList(),
            keyType = TssKeyType.ECDSA,
            keysignPayload = keysignPayload,
            customMessagePayload = null,
            transactionTypeUiModel = null,
            isInitiatingDevice = false,
            transactionHistoryData = null,
            thorChainApi = mockk(relaxed = true),
            evmApiFactory = mockk(relaxed = true),
            cosmosApiFactory = cosmosApiFactory,
            broadcastTx = mockk(relaxed = true),
            explorerLinkRepository = mockk(relaxed = true),
            navigator = mockk<Navigator<Destination>>(relaxed = true),
            sessionApi = mockk(relaxed = true),
            encryption = mockk(relaxed = true),
            featureFlagApi = mockk(relaxed = true),
            pullTssMessages = mockk(relaxed = true),
            addressBookRepository = mockk(relaxed = true),
            txStatusConfigurationProvider = mockk(relaxed = true),
            txStatusPoller = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            chainAccountAddressRepository = mockk(relaxed = true),
            transactionHistoryRepository = mockk(relaxed = true),
            balanceRepository = mockk(relaxed = true),
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            awaitApprovalConfirmation = mockk(relaxed = true),
        )

    @Test
    fun `Terra tx prices the fee at gas_used times min gas price and updates the model`() =
        runTest(testDispatcher) {
            val vm = createViewModel(lunaKeysignPayload)
            vm.updateUiStateForTesting {
                it.copy(
                    transactionUiModel =
                        TransactionTypeUiModel.Send(
                            TransactionDetailsUiModel(
                                networkFeeTokenValue = "0.0075 LUNA",
                                networkFeeFiatValue = "$0.00035",
                            )
                        )
                )
            }
            // 77779 gas × 0.025 uluna/gas = 1944.475 → floor 1944 uluna (matches the extension).
            coEvery { cosmosApi.getTxStatus("hash") } returns
                CosmosTxStatusJson(
                    txResponse = TxResponse(height = "1", txHash = "hash", gasUsed = "77779")
                )
            val params = slot<GasFeeParams>()
            coEvery { gasFeeToEstimatedFee(capture(params)) } returns
                EstimatedGasFee(
                    formattedTokenValue = "0.001944 LUNA",
                    formattedFiatValue = "$0.00008",
                    tokenValue =
                        TokenValue(value = BigInteger.valueOf(1944), unit = "LUNA", decimals = 6),
                    fiatValue = FiatValue(value = BigDecimal("0.00008"), currency = "USD"),
                )

            vm.tryUpdateTerraDisplayFee("hash", Chain.Terra)
            advanceUntilIdle()

            assertEquals(BigInteger.valueOf(1944), params.captured.gasFee.value)
            val model = vm.state.value.transactionUiModel as TransactionTypeUiModel.Send
            assertEquals("0.001944 LUNA", model.tx.networkFeeTokenValue)
            assertEquals("$0.00008", model.tx.networkFeeFiatValue)
        }

    @Test
    fun `non-Terra Cosmos chain skips the RPC call and leaves the model unchanged`() =
        runTest(testDispatcher) {
            val vm = createViewModel(gaiaKeysignPayload)
            vm.updateUiStateForTesting {
                it.copy(
                    transactionUiModel =
                        TransactionTypeUiModel.Send(
                            TransactionDetailsUiModel(networkFeeTokenValue = "0.0075 ATOM")
                        )
                )
            }

            vm.tryUpdateTerraDisplayFee("hash", Chain.GaiaChain)
            advanceUntilIdle()

            coVerify(exactly = 0) { cosmosApiFactory.createCosmosApi(any()) }
            val model = vm.state.value.transactionUiModel as TransactionTypeUiModel.Send
            assertEquals("0.0075 ATOM", model.tx.networkFeeTokenValue)
        }

    @Test
    fun `tx missing gas_used leaves the declared fee unchanged`() =
        runTest(testDispatcher) {
            val vm = createViewModel(lunaKeysignPayload)
            vm.updateUiStateForTesting {
                it.copy(
                    transactionUiModel =
                        TransactionTypeUiModel.Send(
                            TransactionDetailsUiModel(
                                networkFeeTokenValue = "0.0075 LUNA",
                                networkFeeFiatValue = "$0.00035",
                            )
                        )
                )
            }
            coEvery { cosmosApi.getTxStatus("hash") } returns
                CosmosTxStatusJson(
                    txResponse = TxResponse(height = "1", txHash = "hash", gasUsed = null)
                )

            vm.tryUpdateTerraDisplayFee("hash", Chain.Terra)
            advanceUntilIdle()

            val model = vm.state.value.transactionUiModel as TransactionTypeUiModel.Send
            assertEquals("0.0075 LUNA", model.tx.networkFeeTokenValue)
            assertEquals("$0.00035", model.tx.networkFeeFiatValue)
        }
}
