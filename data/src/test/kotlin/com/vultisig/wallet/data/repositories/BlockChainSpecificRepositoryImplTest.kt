package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.DashApi
import com.vultisig.wallet.data.api.EvmApi
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.models.ZkGasFee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.sui.SuiFeeService.Companion.SUI_DEFAULT_GAS_BUDGET
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.utils.increaseByPercent
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class BlockChainSpecificRepositoryImplTest {

    @Test
    fun `native EVM estimation uses destination address in returned dto`() = runTest {
        val destination = "0xdestination"
        val coin = evmCoin(chain = Chain.Ethereum, isNativeToken = true)
        val result =
            repository(
                    evmApi =
                        evmApi(nativeGasByRecipient = mapOf(destination to BigInteger("40000"))),
                    evmFeeService =
                        evmFeeService(
                            feesByRecipient =
                                mapOf(destination to (BigInteger("111") to BigInteger("22")))
                        ),
                )
                .getSpecific(
                    chain = Chain.Ethereum,
                    address = SOURCE_ADDRESS,
                    token = coin,
                    gasFee = TokenValue(BigInteger.ONE, coin),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                    dstAddress = destination,
                    tokenAmountValue = BigInteger.TEN,
                    memo = "memo",
                )

        assertEthereumSpecific(
            result = result,
            gasLimit = BigInteger("40000"),
            maxFeePerGas = BigInteger("111"),
            priorityFee = BigInteger("22"),
        )
    }

    @Test
    fun `ERC20 estimation uses destination address in returned dto`() = runTest {
        val destination = "0xrecipient"
        val coin =
            evmCoin(chain = Chain.Ethereum, isNativeToken = false, contractAddress = "0xcontract")
        val result =
            repository(
                    evmApi =
                        evmApi(erc20GasByRecipient = mapOf(destination to BigInteger("200000"))),
                    evmFeeService =
                        evmFeeService(
                            feesByRecipient =
                                mapOf(destination to (BigInteger("111") to BigInteger("22")))
                        ),
                )
                .getSpecific(
                    chain = Chain.Ethereum,
                    address = SOURCE_ADDRESS,
                    token = coin,
                    gasFee = TokenValue(BigInteger.ONE, coin),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                    dstAddress = destination,
                    tokenAmountValue = BigInteger("42"),
                )

        assertEthereumSpecific(
            result = result,
            gasLimit = BigInteger("300000"),
            maxFeePerGas = BigInteger("111"),
            priorityFee = BigInteger("22"),
        )
    }

    @Test
    fun `zkSync estimation uses destination address in returned dto`() = runTest {
        val destination = "0xzkrecipient"
        val coin = evmCoin(chain = Chain.ZkSync, isNativeToken = true)
        val result =
            repository(
                    evmApi =
                        evmApi(
                            zkFeesByRecipient =
                                mapOf(
                                    destination to
                                        ZkGasFee(
                                            gasLimit = BigInteger("25000"),
                                            gasPerPubdataLimit = BigInteger.ONE,
                                            maxFeePerGas = BigInteger("77"),
                                            maxPriorityFeePerGas = BigInteger("33"),
                                        )
                                )
                        ),
                    evmFeeService = NoOpFeeService,
                )
                .getSpecific(
                    chain = Chain.ZkSync,
                    address = SOURCE_ADDRESS,
                    token = coin,
                    gasFee = TokenValue(BigInteger.ONE, coin),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                    dstAddress = destination,
                )

        assertEthereumSpecific(
            result = result,
            gasLimit = BigInteger("25000"),
            maxFeePerGas = BigInteger("77"),
            priorityFee = BigInteger("33"),
        )
    }

    @Test
    fun `SUI specific uses GasFees limit and price from fee service`() = runTest {
        val simulatedBudget = BigInteger("4200000")
        val simulatedPrice = BigInteger("750")
        val result =
            repository(
                    suiApi = suiApi(referenceGasPrice = BigInteger("1")),
                    suiFeeService = suiFeeService(limit = simulatedBudget, price = simulatedPrice),
                )
                .getSpecific(
                    chain = Chain.Sui,
                    address = SOURCE_ADDRESS,
                    token = suiCoin(),
                    gasFee = TokenValue(BigInteger.ONE, suiCoin()),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                )

        val specific = result.blockChainSpecific as BlockChainSpecific.Sui
        assertEquals(simulatedBudget, specific.gasBudget)
        assertEquals(simulatedPrice, specific.referenceGasPrice)
    }

    @Test
    fun `SUI specific falls back to padded default budget when fee service throws`() = runTest {
        val fallbackPrice = BigInteger("500")
        val result =
            repository(
                    suiApi = suiApi(referenceGasPrice = fallbackPrice),
                    suiFeeService = failingFeeService(),
                )
                .getSpecific(
                    chain = Chain.Sui,
                    address = SOURCE_ADDRESS,
                    token = suiCoin(),
                    gasFee = TokenValue(BigInteger.ONE, suiCoin()),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                )

        val specific = result.blockChainSpecific as BlockChainSpecific.Sui
        assertEquals(SUI_DEFAULT_GAS_BUDGET.increaseByPercent(15), specific.gasBudget)
        assertEquals(fallbackPrice, specific.referenceGasPrice)
    }

    @Test
    fun `SUI specific uses default fees when primary throws but default succeeds`() = runTest {
        val defaultBudget = BigInteger("3450000")
        val defaultPrice = BigInteger("620")
        val result =
            repository(
                    suiApi = suiApi(referenceGasPrice = BigInteger("1")),
                    suiFeeService =
                        suiFeeService(
                            limit = BigInteger.ZERO,
                            primaryThrows = true,
                            defaultLimit = defaultBudget,
                            defaultPrice = defaultPrice,
                        ),
                )
                .getSpecific(
                    chain = Chain.Sui,
                    address = SOURCE_ADDRESS,
                    token = suiCoin(),
                    gasFee = TokenValue(BigInteger.ONE, suiCoin()),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                )

        val specific = result.blockChainSpecific as BlockChainSpecific.Sui
        assertEquals(defaultBudget, specific.gasBudget)
        assertEquals(defaultPrice, specific.referenceGasPrice)
    }

    @Test
    fun `SUI specific falls back to padded default when fee service returns unexpected type`() =
        runTest {
            val fallbackPrice = BigInteger("500")
            val result =
                repository(
                        suiApi = suiApi(referenceGasPrice = fallbackPrice),
                        suiFeeService = basicFeeService(),
                    )
                    .getSpecific(
                        chain = Chain.Sui,
                        address = SOURCE_ADDRESS,
                        token = suiCoin(),
                        gasFee = TokenValue(BigInteger.ONE, suiCoin()),
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                    )

            val specific = result.blockChainSpecific as BlockChainSpecific.Sui
            assertEquals(SUI_DEFAULT_GAS_BUDGET.increaseByPercent(15), specific.gasBudget)
            assertEquals(fallbackPrice, specific.referenceGasPrice)
        }

    @Test
    fun `SUI specific rethrows CancellationException without falling back`() = runTest {
        assertFailsWith<CancellationException> {
            repository(
                    suiApi = suiApi(referenceGasPrice = BigInteger("500")),
                    suiFeeService = cancellingFeeService(),
                )
                .getSpecific(
                    chain = Chain.Sui,
                    address = SOURCE_ADDRESS,
                    token = suiCoin(),
                    gasFee = TokenValue(BigInteger.ONE, suiCoin()),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                )
        }
    }

    private fun suiApi(referenceGasPrice: BigInteger): SuiApi = mockk {
        coEvery { getReferenceGasPrice() } returns referenceGasPrice
        coEvery { getAllCoins(any()) } returns emptyList()
    }

    private fun suiFeeService(
        limit: BigInteger,
        price: BigInteger = BigInteger.ZERO,
        defaultPrice: BigInteger = price,
        primaryThrows: Boolean = false,
        defaultLimit: BigInteger = BigInteger.ZERO,
    ): FeeService =
        object : FeeService {
            override suspend fun calculateFees(transaction: BlockchainTransaction): Fee {
                if (primaryThrows) throw java.io.IOException("sui rpc down")
                return GasFees(price = price, limit = limit, amount = limit)
            }

            override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee =
                GasFees(price = defaultPrice, limit = defaultLimit, amount = defaultLimit)
        }

    private fun cancellingFeeService(): FeeService =
        object : FeeService {
            override suspend fun calculateFees(transaction: BlockchainTransaction): Fee =
                throw CancellationException("sui fee calculation cancelled")

            override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee =
                throw CancellationException("sui default fee calculation cancelled")
        }

    private fun failingFeeService(): FeeService =
        object : FeeService {
            override suspend fun calculateFees(transaction: BlockchainTransaction): Fee =
                throw java.io.IOException("primary rpc down")

            override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee =
                throw java.io.IOException("default rpc down")
        }

    private fun basicFeeService(): FeeService =
        object : FeeService {
            override suspend fun calculateFees(transaction: BlockchainTransaction): Fee =
                BasicFee(BigInteger.ZERO)

            override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee =
                BasicFee(BigInteger.ZERO)
        }

    private fun suiCoin() =
        Coin(
            chain = Chain.Sui,
            ticker = "SUI",
            logo = "",
            address = SOURCE_ADDRESS,
            decimal = 9,
            hexPublicKey = "pub",
            priceProviderID = "sui",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun repository(
        evmApi: EvmApi = mockk<EvmApi>(relaxed = true),
        evmFeeService: FeeService = NoOpFeeService,
        suiApi: SuiApi = mockk<SuiApi>(relaxed = true),
        suiFeeService: FeeService = NoOpFeeService,
    ): BlockChainSpecificRepositoryImpl {
        val evmApiFactory =
            object : EvmApiFactory {
                override fun createEvmApi(chain: Chain): EvmApi = evmApi
            }

        return BlockChainSpecificRepositoryImpl(
            thorChainApi = mockk<ThorChainApi>(relaxed = true),
            mayaChainApi = mockk<MayaChainApi>(relaxed = true),
            evmApiFactory = evmApiFactory,
            solanaApi = mockk<SolanaApi>(relaxed = true),
            cosmosApiFactory = mockk<CosmosApiFactory>(relaxed = true),
            blockChairApi = mockk<BlockChairApi>(relaxed = true),
            dashApi = mockk<DashApi>(relaxed = true),
            polkadotApi = mockk<PolkadotApi>(relaxed = true),
            bittensorApi = mockk<BittensorApi>(relaxed = true),
            suiApi = suiApi,
            tonApi = mockk<TonApi>(relaxed = true),
            rippleApi = mockk<RippleApi>(relaxed = true),
            tronApi = mockk<TronApi>(relaxed = true),
            cardanoApi = mockk<CardanoApi>(relaxed = true),
            feeServiceComposite =
                FeeServiceComposite(
                    ethereumFeeService = evmFeeService,
                    zkFeeService = NoOpFeeService,
                    polkadotFeeService = NoOpFeeService,
                    bittensorFeeService = NoOpFeeService,
                    rippleFeeService = NoOpFeeService,
                    suiFeeService = suiFeeService,
                    tonFeeService = NoOpFeeService,
                    tronFeeService = NoOpFeeService,
                    solanaFeeService = NoOpFeeService,
                    thorchainFeeService = NoOpFeeService,
                    cosmosFeeService = NoOpFeeService,
                    utxoFeeService = NoOpFeeService,
                ),
        )
    }

    private fun evmApi(
        nativeGasByRecipient: Map<String, BigInteger> = emptyMap(),
        erc20GasByRecipient: Map<String, BigInteger> = emptyMap(),
        zkFeesByRecipient: Map<String, ZkGasFee> = emptyMap(),
    ): EvmApi = mockk {
        coEvery { getNonce(any()) } returns NONCE

        coEvery { estimateGasForEthTransaction(any(), any(), any(), any()) } answers
            {
                val recipient = invocation.args[1] as String
                nativeGasByRecipient[recipient] ?: BigInteger("1000")
            }

        coEvery { estimateGasForERC20Transfer(any(), any(), any(), any()) } answers
            {
                val recipient = invocation.args[2] as String
                erc20GasByRecipient[recipient] ?: BigInteger("50000")
            }

        coEvery { zkEstimateFee(any(), any(), any()) } answers
            {
                val recipient = invocation.args[1] as String
                zkFeesByRecipient[recipient]
                    ?: ZkGasFee(
                        gasLimit = BigInteger("999"),
                        gasPerPubdataLimit = BigInteger.ONE,
                        maxFeePerGas = BigInteger("5"),
                        maxPriorityFeePerGas = BigInteger.ONE,
                    )
            }
    }

    private fun evmFeeService(
        feesByRecipient: Map<String, Pair<BigInteger, BigInteger>>
    ): FeeService = mockk {
        coEvery { calculateFees(any()) } answers
            {
                val transfer = invocation.args.first() as Transfer
                val (maxFee, priorityFee) =
                    feesByRecipient[transfer.to] ?: (BigInteger("999") to BigInteger("888"))
                Eip1559(
                    limit = BigInteger.ONE,
                    networkPrice = BigInteger.ZERO,
                    maxFeePerGas = maxFee,
                    maxPriorityFeePerGas = priorityFee,
                    amount = maxFee,
                )
            }

        coEvery { calculateDefaultFees(any()) } returns BasicFee(BigInteger.ZERO)
    }

    private fun assertEthereumSpecific(
        result: BlockChainSpecificAndUtxo,
        gasLimit: BigInteger,
        maxFeePerGas: BigInteger,
        priorityFee: BigInteger,
    ) {
        val specific = result.blockChainSpecific as BlockChainSpecific.Ethereum
        assertEquals(gasLimit, specific.gasLimit)
        assertEquals(maxFeePerGas, specific.maxFeePerGasWei)
        assertEquals(priorityFee, specific.priorityFeeWei)
        assertEquals(NONCE, specific.nonce)
    }

    private fun evmCoin(chain: Chain, isNativeToken: Boolean, contractAddress: String = "") =
        Coin(
            chain = chain,
            ticker = "ETH",
            logo = "",
            address = SOURCE_ADDRESS,
            decimal = 18,
            hexPublicKey = "pub",
            priceProviderID = "eth",
            contractAddress = contractAddress,
            isNativeToken = isNativeToken,
        )

    private companion object {
        val NONCE: BigInteger = BigInteger("7")
        const val SOURCE_ADDRESS = "0xsource"
    }
}

private object NoOpFeeService : FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee =
        BasicFee(BigInteger.ZERO)

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee =
        BasicFee(BigInteger.ZERO)
}
