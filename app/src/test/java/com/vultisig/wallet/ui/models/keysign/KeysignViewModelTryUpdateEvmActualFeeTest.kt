@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.EvmRpcResponseJson
import com.vultisig.wallet.data.api.models.EvmTxStatusJson
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
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
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
import kotlin.test.assertFalse
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

internal class KeysignViewModelTryUpdateEvmActualFeeTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var evmApiFactory: EvmApiFactory
    private lateinit var evmApi: EvmApi
    private lateinit var gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase
    private lateinit var fiatValueToString: FiatValueToStringMapper
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    private val vault = Vault(id = "v1", name = "Test Vault")
    private val ethCoin =
        Coin(
            chain = Chain.Ethereum,
            ticker = "ETH",
            logo = "eth",
            address = "0xsender",
            decimal = 18,
            hexPublicKey = "",
            priceProviderID = "ethereum",
            contractAddress = "",
            isNativeToken = true,
        )
    private val ethKeysignPayload =
        KeysignPayload(
            coin = ethCoin,
            toAddress = "0xdest",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.Ethereum(
                    maxFeePerGasWei = BigInteger.ZERO,
                    priorityFeeWei = BigInteger.ZERO,
                    nonce = BigInteger.ZERO,
                    gasLimit = BigInteger.valueOf(21000),
                ),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )
    private val btcCoin =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "btc",
            address = "bc1qsender",
            decimal = 8,
            hexPublicKey = "",
            priceProviderID = "bitcoin",
            contractAddress = "",
            isNativeToken = true,
        )
    private val btcKeysignPayload =
        KeysignPayload(
            coin = btcCoin,
            toAddress = "bc1qdest",
            toAmount = BigInteger.ZERO,
            blockChainSpecific =
                BlockChainSpecific.UTXO(byteFee = BigInteger.ONE, sendMaxAmount = false),
            vaultPublicKeyECDSA = "",
            vaultLocalPartyID = "",
            libType = null,
            wasmExecuteContractPayload = null,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        evmApiFactory = mockk(relaxed = true)
        evmApi = mockk(relaxed = true)
        gasFeeToEstimatedFee = mockk(relaxed = true)
        fiatValueToString = mockk()
        transactionHistoryRepository = mockk(relaxed = true)
        every { evmApiFactory.createEvmApi(Chain.Ethereum) } returns evmApi
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
            evmApiFactory = evmApiFactory,
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
            transactionHistoryRepository = transactionHistoryRepository,
            balanceRepository = mockk(relaxed = true),
            inAppReviewRepository = mockk(relaxed = true),
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            fiatValueToString = fiatValueToString,
            pendingLimitOrderRepository = mockk(relaxed = true),
            utxoInFlightRepository = mockk(relaxed = true),
            doneTransactionPresentation = mockk(relaxed = true),
            ioDispatcher = testDispatcher,
            awaitApprovalConfirmation = mockk(relaxed = true),
        )

    @Test
    fun `EVM receipt with both fee fields updates resolvedTransactionUiModel`() =
        runTest(testDispatcher) {
            val vm = createViewModel(ethKeysignPayload)
            vm.updateUiStateForTesting {
                it.copy(
                    transactionUiModel =
                        TransactionTypeUiModel.Send(
                            TransactionDetailsUiModel(
                                networkFeeTokenValue = "0.001 ETH",
                                networkFeeFiatValue = "~$3",
                            )
                        )
                )
            }
            coEvery { evmApi.getTxStatus("0xabc") } returns
                EvmRpcResponseJson(
                    id = 1,
                    result =
                        EvmTxStatusJson(
                            status = "0x1",
                            gasUsed = "0x5208",
                            effectiveGasPrice = "0x3b9aca00",
                        ),
                )
            coEvery { gasFeeToEstimatedFee(any()) } returns
                EstimatedGasFee(
                    formattedTokenValue = "0.00042 ETH",
                    formattedFiatValue = "$1.50",
                    tokenValue = TokenValue(value = BigInteger.ONE, unit = "ETH", decimals = 18),
                    fiatValue = FiatValue(value = BigDecimal("1.50"), currency = "USD"),
                )

            vm.tryUpdateEvmActualFee("0xabc", Chain.Ethereum)
            advanceUntilIdle()

            val model = vm.state.value.transactionUiModel as TransactionTypeUiModel.Send
            assertEquals("0.00042 ETH", model.tx.networkFeeTokenValue)
            assertEquals("$1.50", model.tx.networkFeeFiatValue)
        }

    @Test
    fun `swap done screen shows the paid gas and a total rebuilt around it`() =
        runTest(testDispatcher) {
            val vm = createViewModel(ethKeysignPayload)
            vm.updateUiStateForTesting {
                it.copy(
                    transactionUiModel =
                        TransactionTypeUiModel.Swap(
                            SwapTransactionUiModel(
                                networkFee = ValuedToken.Empty.copy(fiatValue = "$6.34"),
                                networkFeeFormatted = "0.00239107 ETH",
                                isNetworkFeeMax = true,
                                totalFee = "$6.34",
                                totalFeeExcludingNetwork = usd("0"),
                            )
                        )
                )
            }
            receipt(gasUsed = "0xfc2f6", effectiveGasPrice = "0x342edf18")
            coEvery { gasFeeToEstimatedFee(any()) } returns paidSwapFee
            coEvery { fiatValueToString(usd("2.42"), true, false) } returns "$2.42"

            vm.tryUpdateEvmActualFee("0xabc", Chain.Ethereum)
            advanceUntilIdle()

            val model =
                (vm.state.value.transactionUiModel as TransactionTypeUiModel.Swap)
                    .swapTransactionUiModel
            assertEquals("0.00090433 ETH", model.networkFeeFormatted)
            assertEquals("$2.42", model.networkFee.fiatValue)
            assertEquals("$2.42", model.totalFee)
            assertFalse(model.isNetworkFeeMax)
        }

    @Test
    fun `paid gas is written to the history row`() =
        runTest(testDispatcher) {
            val vm = createViewModel(ethKeysignPayload)
            receipt(gasUsed = "0xfc2f6", effectiveGasPrice = "0x342edf18")
            coEvery { gasFeeToEstimatedFee(any()) } returns paidSwapFee

            vm.tryUpdateEvmActualFee("0xabc", Chain.Ethereum)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                transactionHistoryRepository.recordPaidNetworkFee(
                    "Ethereum",
                    "0xabc",
                    BigInteger("904334296650000"),
                )
            }
        }

    /**
     * Gas is paid in ETH even when the transaction moved an ERC-20: valuing the wei at USDC's six
     * decimals would put the fee twelve orders of magnitude too high.
     */
    @Test
    fun `an ERC-20 transaction's gas is valued in the native coin`() =
        runTest(testDispatcher) {
            val usdc = ethCoin.copy(ticker = "USDC", decimal = 6, isNativeToken = false)
            val vm = createViewModel(ethKeysignPayload.copy(coin = usdc))
            receipt(gasUsed = "0x5208", effectiveGasPrice = "0x3b9aca00")
            val params = slot<GasFeeParams>()
            coEvery { gasFeeToEstimatedFee(capture(params)) } returns paidSwapFee

            vm.tryUpdateEvmActualFee("0xabc", Chain.Ethereum)
            advanceUntilIdle()

            assertEquals(18, params.captured.gasFee.decimals)
            assertEquals("ETH", params.captured.gasFee.unit)
            assertEquals("ETH", params.captured.selectedToken.ticker)
        }

    @Test
    fun `non-EVM chain skips RPC call entirely`() =
        runTest(testDispatcher) {
            val vm = createViewModel(btcKeysignPayload)
            vm.updateUiStateForTesting {
                it.copy(
                    transactionUiModel =
                        TransactionTypeUiModel.Send(
                            TransactionDetailsUiModel(networkFeeTokenValue = "0.0001 BTC")
                        )
                )
            }

            vm.tryUpdateEvmActualFee("0xabc", Chain.Bitcoin)
            advanceUntilIdle()

            coVerify(exactly = 0) { evmApiFactory.createEvmApi(any()) }
            val model = vm.state.value.transactionUiModel as TransactionTypeUiModel.Send
            assertEquals("0.0001 BTC", model.tx.networkFeeTokenValue)
        }

    @Test
    fun `receipt missing effectiveGasPrice leaves model unchanged`() =
        runTest(testDispatcher) {
            val vm = createViewModel(ethKeysignPayload)
            vm.updateUiStateForTesting {
                it.copy(
                    transactionUiModel =
                        TransactionTypeUiModel.Send(
                            TransactionDetailsUiModel(
                                networkFeeTokenValue = "0.001 ETH",
                                networkFeeFiatValue = "~$3",
                            )
                        )
                )
            }
            coEvery { evmApi.getTxStatus("0xabc") } returns
                EvmRpcResponseJson(
                    id = 1,
                    result =
                        EvmTxStatusJson(
                            status = "0x1",
                            gasUsed = "0x5208",
                            effectiveGasPrice = null,
                        ),
                )

            vm.tryUpdateEvmActualFee("0xabc", Chain.Ethereum)
            advanceUntilIdle()

            val model = vm.state.value.transactionUiModel as TransactionTypeUiModel.Send
            assertEquals("0.001 ETH", model.tx.networkFeeTokenValue)
            assertEquals("~$3", model.tx.networkFeeFiatValue)
        }

    private fun receipt(gasUsed: String, effectiveGasPrice: String) {
        coEvery { evmApi.getTxStatus("0xabc") } returns
            EvmRpcResponseJson(
                id = 1,
                result =
                    EvmTxStatusJson(
                        status = "0x1",
                        gasUsed = gasUsed,
                        effectiveGasPrice = effectiveGasPrice,
                    ),
            )
    }

    private fun usd(value: String) = FiatValue(value = BigDecimal(value), currency = "USD")

    private val paidSwapFee =
        EstimatedGasFee(
            formattedTokenValue = "0.00090433 ETH",
            formattedFiatValue = "$2.42",
            tokenValue =
                TokenValue(value = BigInteger("904334296650000"), unit = "ETH", decimals = 18),
            fiatValue = usd("2.42"),
        )
}
