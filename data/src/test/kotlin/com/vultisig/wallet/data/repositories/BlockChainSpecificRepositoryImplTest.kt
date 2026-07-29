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
import com.vultisig.wallet.data.api.models.BlockChairAddress
import com.vultisig.wallet.data.api.models.BlockChairInfo
import com.vultisig.wallet.data.api.models.BlockChairUtxoInfo
import com.vultisig.wallet.data.api.models.ZkGasFee
import com.vultisig.wallet.data.blockchain.FeeService
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.ethereum.ZkFeeService
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.blockchain.model.BlockchainTransaction
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.blockchain.model.Fee
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.blockchain.sui.SuiFeeService.Companion.SUI_DEFAULT_GAS_BUDGET
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_LIMIT
import com.vultisig.wallet.data.chains.helpers.SOLANA_PRIORITY_FEE_PRICE
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
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
import vultisig.keysign.v1.SuiCoin

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
    fun `Bitcoin UTXO selection excludes dust, unconfirmed, non-spendable, and negative-index entries`() =
        runTest {
            val coin = bitcoinCoin()
            val blockChairApi =
                mockk<BlockChairApi> {
                    coEvery { getAllUtxos(Chain.Bitcoin, SOURCE_ADDRESS) } returns
                        blockChairInfo(rawUtxoFixture())
                }

            val result =
                repository(blockChairApi = blockChairApi)
                    .getSpecific(
                        chain = Chain.Bitcoin,
                        address = SOURCE_ADDRESS,
                        token = coin,
                        gasFee = TokenValue(BigInteger.ONE, coin),
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                    )

            assertEquals(
                listOf(
                    UtxoInfo(hash = "tx-confirmed-small", amount = 10_000, index = 0u),
                    UtxoInfo(hash = "tx-confirmed-large", amount = 50_000, index = 0u),
                ),
                result.utxos,
            )
        }

    @Test
    fun `Dash-Blockchair-fallback and the generic UTXO branch apply the identical spendable filter`() =
        runTest {
            val rawUtxos = rawUtxoFixture()
            val blockChairApi =
                mockk<BlockChairApi> {
                    coEvery { getAllUtxos(any(), SOURCE_ADDRESS) } returns blockChairInfo(rawUtxos)
                }
            val failingDashApi =
                mockk<DashApi> {
                    coEvery { getAddressUtxos(any()) } throws java.io.IOException("dash rpc down")
                }

            val dashResult =
                repository(blockChairApi = blockChairApi, dashApi = failingDashApi)
                    .getSpecific(
                        chain = Chain.Dash,
                        address = SOURCE_ADDRESS,
                        token = dashCoin(),
                        gasFee = TokenValue(BigInteger.ONE, dashCoin()),
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                    )
            val bitcoinResult =
                repository(blockChairApi = blockChairApi)
                    .getSpecific(
                        chain = Chain.Bitcoin,
                        address = SOURCE_ADDRESS,
                        token = bitcoinCoin(),
                        gasFee = TokenValue(BigInteger.ONE, bitcoinCoin()),
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                    )

            assertEquals(bitcoinResult.utxos, dashResult.utxos)
        }

    @Test
    fun `Dash RPC path excludes dust the same way the Blockchair-fallback path does`() = runTest {
        val dashApi =
            mockk<DashApi> {
                coEvery { getAddressUtxos(SOURCE_ADDRESS) } returns
                    listOf(
                        UtxoInfo(hash = "tx-dust", amount = 500, index = 0u),
                        UtxoInfo(hash = "tx-confirmed-large", amount = 50_000, index = 0u),
                        UtxoInfo(hash = "tx-confirmed-small", amount = 10_000, index = 0u),
                    )
            }

        val result =
            repository(dashApi = dashApi)
                .getSpecific(
                    chain = Chain.Dash,
                    address = SOURCE_ADDRESS,
                    token = dashCoin(),
                    gasFee = TokenValue(BigInteger.ONE, dashCoin()),
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = false,
                )

        assertEquals(
            listOf(
                UtxoInfo(hash = "tx-confirmed-small", amount = 10_000, index = 0u),
                UtxoInfo(hash = "tx-confirmed-large", amount = 50_000, index = 0u),
            ),
            result.utxos,
        )
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
    fun `zkSync signing path estimates with the same calldata as the fee preview`() = runTest {
        val destination = "0xzkrecipient"
        val coin = evmCoin(chain = Chain.ZkSync, isNativeToken = true)
        val evmApi = evmApi()
        val evmApiFactory =
            object : EvmApiFactory {
                override fun createEvmApi(chain: Chain): EvmApi = evmApi
            }

        repository(evmApi = evmApi, evmFeeService = NoOpFeeService)
            .getSpecific(
                chain = Chain.ZkSync,
                address = SOURCE_ADDRESS,
                token = coin,
                gasFee = TokenValue(BigInteger.ONE, coin),
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = false,
                dstAddress = destination,
                memo = MEMO,
            )

        // The signing path must have priced the memo it is about to sign, not a fixed stand-in.
        coVerify(exactly = 1) { evmApi.zkEstimateFee(SOURCE_ADDRESS, destination, MEMO_CALL_DATA) }

        ZkFeeService(evmApiFactory)
            .calculateFees(
                Transfer(
                    coin = coin,
                    vault = VaultData("", ""),
                    amount = BigInteger.ZERO,
                    to = destination,
                    memo = MEMO,
                )
            )

        // Both entry points must send byte-identical calldata: zkSync prices gas by payload size.
        coVerify(exactly = 2) { evmApi.zkEstimateFee(SOURCE_ADDRESS, destination, MEMO_CALL_DATA) }
    }

    @Test
    fun `zkSync specific clamps a priority fee that exceeds the max fee`() = runTest {
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
                                            maxPriorityFeePerGas = BigInteger("99"),
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
            priorityFee = BigInteger("77"),
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
    fun `SUI embedded coins cover the amount plus the refined gas budget they are signed with`() =
        runTest {
            // Refined budget above SUI_DEFAULT_GAS_BUDGET: selecting against the default alone
            // would stop at amount + 3_000_000 and leave the signing-time coverage check —
            // amount + gasBudget, the balance Sui requires a PaySui input set to hold — short on a
            // fragmented but fully funded wallet.
            val refinedBudget = BigInteger("4200000")
            val amount = BigInteger("1000000")
            val walletCoins = fragmentedNativeCoins(count = 12, balance = "1000000")
            val result =
                repository(
                        suiApi = suiApi(referenceGasPrice = BigInteger("1"), coins = walletCoins),
                        suiFeeService = suiFeeService(limit = refinedBudget),
                    )
                    .getSpecific(
                        chain = Chain.Sui,
                        address = SOURCE_ADDRESS,
                        token = suiCoin(),
                        gasFee = TokenValue(BigInteger.ONE, suiCoin()),
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                        tokenAmountValue = amount,
                    )

            val specific = result.blockChainSpecific as BlockChainSpecific.Sui
            assertEquals(refinedBudget, specific.gasBudget)
            assertTrue(specific.coins.totalBalance() >= amount + refinedBudget)
            // Still bounded — only the objects the send needs, not every owned object.
            assertTrue(specific.coins.size < walletCoins.size)
        }

    @Test
    fun `SUI embedded coins cover the padded default budget used when fee estimation fails`() =
        runTest {
            // The fallback budget is SUI_DEFAULT_GAS_BUDGET + 15%, so it too exceeds the budget the
            // dry-run priced against and must drive the embedded selection.
            val paddedDefault = SUI_DEFAULT_GAS_BUDGET.increaseByPercent(15)
            val amount = BigInteger("1000000")
            val result =
                repository(
                        suiApi =
                            suiApi(
                                referenceGasPrice = BigInteger("500"),
                                coins = fragmentedNativeCoins(count = 12, balance = "1000000"),
                            ),
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
                        tokenAmountValue = amount,
                    )

            val specific = result.blockChainSpecific as BlockChainSpecific.Sui
            assertEquals(paddedDefault, specific.gasBudget)
            assertTrue(specific.coins.totalBalance() >= amount + paddedDefault)
        }

    @Test
    fun `SUI embedded coins stay the dry-run priced set when the refined budget is under default`() =
        runTest {
            // Refined budget below SUI_DEFAULT_GAS_BUDGET: the selection must stay at the default,
            // the budget SuiFeeService dry-run priced, so the broadcast transaction carries no
            // input objects the simulation never measured.
            val refinedBudget = BigInteger("2000000")
            val amount = BigInteger("1000000")
            val walletCoins = fragmentedNativeCoins(count = 12, balance = "1000000")
            val result =
                repository(
                        suiApi = suiApi(referenceGasPrice = BigInteger("1"), coins = walletCoins),
                        suiFeeService = suiFeeService(limit = refinedBudget),
                    )
                    .getSpecific(
                        chain = Chain.Sui,
                        address = SOURCE_ADDRESS,
                        token = suiCoin(),
                        gasFee = TokenValue(BigInteger.ONE, suiCoin()),
                        isSwap = false,
                        isMaxAmountEnabled = false,
                        isDeposit = false,
                        tokenAmountValue = amount,
                    )

            val specific = result.blockChainSpecific as BlockChainSpecific.Sui
            val pricedSet =
                SuiHelper.selectPayloadCoins(
                    walletCoins,
                    isNativeToken = true,
                    contractAddress = "",
                    amount = amount,
                    gasBudget = SUI_DEFAULT_GAS_BUDGET,
                )
            assertEquals(pricedSet.map { it.coinObjectId }, specific.coins.map { it.coinObjectId })
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

    private fun suiApi(referenceGasPrice: BigInteger, coins: List<SuiCoin> = emptyList()): SuiApi =
        mockk {
            coEvery { getReferenceGasPrice() } returns referenceGasPrice
            coEvery { getAllCoins(any()) } returns coins
        }

    /** Native SUI objects of [balance] MIST each, so a send has to accumulate several of them. */
    private fun fragmentedNativeCoins(count: Int, balance: String): List<SuiCoin> =
        (1..count).map { index ->
            SuiCoin(
                coinType = "0x2::sui::SUI",
                coinObjectId = "0x%064x".format(index),
                version = index.toString(),
                digest = "digest-$index",
                balance = balance,
                previousTransaction = "",
            )
        }

    private fun List<SuiCoin>.totalBalance(): BigInteger =
        fold(BigInteger.ZERO) { acc, coin -> acc + coin.balance.toBigInteger() }

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

    private fun bitcoinCoin() =
        Coin(
            chain = Chain.Bitcoin,
            ticker = "BTC",
            logo = "",
            address = SOURCE_ADDRESS,
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "bitcoin",
            contractAddress = "",
            isNativeToken = true,
        )

    private fun dashCoin() =
        Coin(
            chain = Chain.Dash,
            ticker = "DASH",
            logo = "",
            address = SOURCE_ADDRESS,
            decimal = 8,
            hexPublicKey = "pub",
            priceProviderID = "dash",
            contractAddress = "",
            isNativeToken = true,
        )

    /**
     * A dust entry, an unconfirmed entry, and an explicitly non-spendable entry — all excluded by
     * [toSpendableUtxos] — alongside two valid entries whose values (10_000 / 50_000 sats) clear
     * every UTXO chain's dust threshold, so the fixture is safe to reuse across chains.
     */
    private fun rawUtxoFixture(): List<BlockChairUtxoInfo> =
        listOf(
            BlockChairUtxoInfo(
                transactionHash = "tx-dust",
                index = 0,
                value = 500,
                blockId = 800_000,
            ),
            BlockChairUtxoInfo(
                transactionHash = "tx-unconfirmed",
                index = 0,
                value = 50_000,
                blockId = 0,
            ),
            BlockChairUtxoInfo(
                transactionHash = "tx-not-spendable",
                index = 0,
                value = 20_000,
                blockId = 800_000,
                isSpendable = false,
            ),
            BlockChairUtxoInfo(
                transactionHash = "tx-negative-index",
                index = -1,
                value = 30_000,
                blockId = 800_000,
            ),
            BlockChairUtxoInfo(
                transactionHash = "tx-confirmed-large",
                index = 0,
                value = 50_000,
                blockId = 800_000,
            ),
            BlockChairUtxoInfo(
                transactionHash = "tx-confirmed-small",
                index = 0,
                value = 10_000,
                blockId = 800_000,
            ),
        )

    private fun blockChairInfo(utxos: List<BlockChairUtxoInfo>): BlockChairInfo =
        BlockChairInfo(
            address = BlockChairAddress(balance = 0, unspentOutputCount = utxos.size),
            utxos = utxos,
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
        dashApi: DashApi = mockk<DashApi>(relaxed = true),
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
            dashApi = dashApi,
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
                    zkFeeService = ZkFeeService(evmApiFactory),
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
        const val MEMO = "hi there"
        // "hi there" UTF-8 encoded — the payload the signed transaction carries.
        const val MEMO_CALL_DATA = "0x6869207468657265"
    }
}

private object NoOpFeeService : FeeService {
    override suspend fun calculateFees(transaction: BlockchainTransaction): Fee =
        BasicFee(BigInteger.ZERO)

    override suspend fun calculateDefaultFees(transaction: BlockchainTransaction): Fee =
        BasicFee(BigInteger.ZERO)
}
