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
import com.vultisig.wallet.data.api.ZcashApi
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
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_LIMIT
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_PRICE
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.utils.increaseByPercent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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
    fun `Zcash UTXO specific carries the live branch id fetched from ZcashApi`() = runTest {
        val coin = zcashCoin()
        val zcashApi = mockk<ZcashApi> { coEvery { getConsensusBranchIdHex() } returns "30f33754" }
        val result =
            repository(zcashApi = zcashApi)
                .getSpecific(
                    chain = Chain.Zcash,
                    address = SOURCE_ADDRESS,
                    token = coin,
                    gasFee = TokenValue(BigInteger.ONE, coin),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                )

        val specific = result.blockChainSpecific
        assertTrue(specific is BlockChainSpecific.UTXO)
        assertEquals("30f33754", (specific as BlockChainSpecific.UTXO).zcashBranchId)
    }

    @Test
    fun `Solana specific carries the per-CU median price as priorityFee, not the total gas fee`() =
        runTest {
            val coin = solanaCoin()
            val solanaApi =
                mockk<SolanaApi> {
                    coEvery { getRecentBlockHash() } returns "SolanaBlockHash1111"
                    coEvery { getTokenAssociatedAccountByOwner(any(), any()) } returns
                        (null to false)
                    coEvery { getMedianPriorityFee(any()) } returns BigInteger("50000")
                }

            val result =
                repository(solanaApi = solanaApi)
                    .getSpecific(
                        chain = Chain.Solana,
                        address = SOURCE_ADDRESS,
                        token = coin,
                        // Total fee (base + priority + rent) in lamports — must NOT leak into
                        // priorityFee, which is a per-compute-unit price.
                        gasFee = TokenValue(BigInteger("105000"), coin),
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                        dstAddress = "SolRecipient1111",
                    )

            val specific = result.blockChainSpecific
            assertTrue(specific is BlockChainSpecific.Solana)
            assertEquals(BigInteger("50000"), (specific as BlockChainSpecific.Solana).priorityFee)
            assertEquals(SOLANA_PRIORITY_FEE_LIMIT.toBigInteger(), specific.priorityLimit)
        }

    @Test
    fun `Solana swap skips the priority-fee RPC (aggregator tx carries its own compute budget)`() =
        runTest {
            val coin = solanaCoin()
            val solanaApi =
                mockk<SolanaApi> {
                    coEvery { getRecentBlockHash() } returns "SolanaBlockHash1111"
                    coEvery { getTokenAssociatedAccountByOwner(any(), any()) } returns
                        (null to false)
                }

            val result =
                repository(solanaApi = solanaApi)
                    .getSpecific(
                        chain = Chain.Solana,
                        address = SOURCE_ADDRESS,
                        token = coin,
                        gasFee = TokenValue(BigInteger("105000"), coin),
                        isSwap = true,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                        dstAddress = "SolRecipient1111",
                    )

            val specific = result.blockChainSpecific
            assertTrue(specific is BlockChainSpecific.Solana)
            // No median fetched — swap signers ignore priorityFee; it falls back to the floor
            // price.
            coVerify(exactly = 0) { solanaApi.getMedianPriorityFee(any()) }
            assertEquals(
                SOLANA_PRIORITY_FEE_PRICE.toBigInteger(),
                (specific as BlockChainSpecific.Solana).priorityFee,
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
    fun `ERC20 router deposit flag applies hardcoded gas limit`() = runTest {
        val router = "0xD37BbE5744D730a1d98d8DC97c42F0Ca46aD7146"
        val coin =
            evmCoin(chain = Chain.Ethereum, isNativeToken = false, contractAddress = "0xusdt")
        val result =
            repository(
                    evmApi = evmApi(erc20GasByRecipient = mapOf(router to BigInteger("50000"))),
                    evmFeeService =
                        evmFeeService(
                            feesByRecipient =
                                mapOf(router to (BigInteger("111") to BigInteger("22")))
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
                    dstAddress = router,
                    tokenAmountValue = BigInteger("300000"),
                    memo = "+:ETH.USDT-0XDAC17F958D2EE523A2206206994597C13D831EC7:thor1abc",
                    isThorchainRouterDeposit = true,
                )

        // eth_estimateGas reverts without prior router approval, so we hardcode 200k and use
        // it verbatim — the bare ERC-20 default floor (210k) must not bump it up via max().
        assertEthereumSpecific(
            result = result,
            gasLimit = BigInteger("200000"),
            maxFeePerGas = BigInteger("111"),
            priorityFee = BigInteger("22"),
        )
    }

    @Test
    fun `ERC20 with router-like memo but no flag does not apply router deposit floor`() = runTest {
        // Regression guard for the false-positive scenario: a regular USDT send to a non-router
        // recipient where the user-typed memo happens to begin with `+:` should not push the limit
        // to the 200k router-deposit floor — it should land at the standard ERC-20 transfer path.
        val destination = "0xnotrouter"
        val coin =
            evmCoin(chain = Chain.Ethereum, isNativeToken = false, contractAddress = "0xusdt")
        val result =
            repository(
                    evmApi =
                        evmApi(erc20GasByRecipient = mapOf(destination to BigInteger("50000"))),
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
                    tokenAmountValue = BigInteger("300000"),
                    memo = "+:something-the-user-typed",
                )

        // ERC-20 path: max(210k DEFAULT_TOKEN_TRANSFER_LIMIT_WITH_MARGIN, 50k*1.5=75k) = 210k.
        // The floor mirrors EthereumFeeService so the signed gasLimit equals the displayed
        // fee bond (issue #4857).
        assertEthereumSpecific(
            result = result,
            gasLimit = BigInteger("210000"),
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

    private fun zcashCoin() =
        Coin(
            chain = Chain.Zcash,
            ticker = "ZEC",
            logo = "",
            address = SOURCE_ADDRESS,
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "zcash",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun solanaCoin() =
        Coin(
            chain = Chain.Solana,
            ticker = "SOL",
            logo = "",
            address = SOURCE_ADDRESS,
            decimal = 9,
            hexPublicKey = "pub",
            priceProviderID = "solana",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun repository(
        evmApi: EvmApi = mockk<EvmApi>(relaxed = true),
        evmFeeService: FeeService = NoOpFeeService,
        suiApi: SuiApi = mockk<SuiApi>(relaxed = true),
        suiFeeService: FeeService = NoOpFeeService,
        blockChairApi: BlockChairApi = mockk<BlockChairApi>(relaxed = true),
        zcashApi: ZcashApi = mockk<ZcashApi>(relaxed = true),
        solanaApi: SolanaApi = mockk<SolanaApi>(relaxed = true),
    ): BlockChainSpecificRepositoryImpl {
        val evmApiFactory =
            object : EvmApiFactory {
                override fun createEvmApi(chain: Chain): EvmApi = evmApi
            }

        return BlockChainSpecificRepositoryImpl(
            thorChainApi = mockk<ThorChainApi>(relaxed = true),
            mayaChainApi = mockk<MayaChainApi>(relaxed = true),
            evmApiFactory = evmApiFactory,
            solanaApi = solanaApi,
            cosmosApiFactory = mockk<CosmosApiFactory>(relaxed = true),
            blockChairApi = blockChairApi,
            dashApi = mockk<DashApi>(relaxed = true),
            zcashApi = zcashApi,
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
