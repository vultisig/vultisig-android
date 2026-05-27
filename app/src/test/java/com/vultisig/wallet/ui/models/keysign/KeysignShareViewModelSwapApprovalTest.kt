@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.api.models.quotes.EVMSwapQuoteJson
import com.vultisig.wallet.data.api.models.quotes.OneInchSwapTxJson
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EVMSwapPayloadJson
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapTransaction.RegularSwapTransaction
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.CustomMessagePayloadRepo
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.MakeQrCodeBitmapShareFormat
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the security-critical approval hop for swaps: the spender that ends up in the signed
 * [com.vultisig.wallet.data.models.payload.ERC20ApprovePayload] must be the transaction's
 * [RegularSwapTransaction.approveSpender] (SwapKit's token-transfer proxy), NOT the swap `to` (the
 * SKWrapGeneric entry contract). A regression that reverted the spender back to `dstAddress` would
 * resurrect the `ERC20InsufficientAllowance` revert — this test would catch it.
 */
internal class KeysignShareViewModelSwapApprovalTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val swapTransactionRepository: SwapTransactionRepository = mockk()
    private val vaultRepository: VaultRepository = mockk()
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper =
        mockk(relaxed = true)
    private val transactionRepository: TransactionRepository = mockk(relaxed = true)
    private val depositTransaction: DepositTransactionRepository = mockk(relaxed = true)
    private val customMessagePayloadRepo: CustomMessagePayloadRepo = mockk(relaxed = true)
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat = mockk(relaxed = true)
    private val generateQrBitmap: GenerateQrBitmap = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // MapperFunc<TokenValue, String> erases to a generic mock under relaxed mode; stub it so
        // the String result assigned to the amount StateFlow doesn't ClassCastException.
        every { mapTokenValueToStringWithUnit(any()) } returns "0"
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        KeysignShareViewModel(
            mapTokenValueToStringWithUnit = mapTokenValueToStringWithUnit,
            vaultRepository = vaultRepository,
            transactionRepository = transactionRepository,
            swapTransactionRepository = swapTransactionRepository,
            depositTransaction = depositTransaction,
            customMessagePayloadRepo = customMessagePayloadRepo,
            makeQrCodeBitmapShareFormat = makeQrCodeBitmapShareFormat,
            generateQrBitmap = generateQrBitmap,
        )

    @Test
    fun `swap approve payload uses the allowance target as spender, not the swap to`() = runTest {
        val swapEntry = "0x9025b8ff35ca44f7018c3a37fe0f69e63dbb0743" // SKWrapGeneric (tx.to)
        val transferProxy = "0x6c0ad82f9721a6dc986381d19338601a2e6370e5" // allowance target
        val amount = BigInteger("5728996") // 5.728996 USDT (6dp)

        coEvery { swapTransactionRepository.getTransaction("tx-1") } returns
            swapTransaction(
                amount = amount,
                dstAddress = swapEntry,
                approveSpender = transferProxy,
                isApprovalRequired = true,
            )
        coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")

        val vm = viewModel()
        vm.loadSwapTransaction("tx-1")

        val approve =
            requireNotNull(vm.keysignPayload?.approvePayload) {
                "approvePayload must be present when approval is required"
            }
        // The spender is the token-transfer proxy, never the swap entry contract.
        assertEquals(transferProxy, approve.spender)
        // And the approve amount matches the swap input (what the security scanner must preview).
        assertEquals(amount, approve.amount)
        // The swap itself is still sent to the entry contract.
        assertEquals(swapEntry, vm.keysignPayload?.toAddress)
    }

    @Test
    fun `no approve payload when approval is not required`() = runTest {
        coEvery { swapTransactionRepository.getTransaction("tx-2") } returns
            swapTransaction(
                amount = BigInteger("5728996"),
                dstAddress = "0x9025b8ff35ca44f7018c3a37fe0f69e63dbb0743",
                approveSpender = "0x6c0ad82f9721a6dc986381d19338601a2e6370e5",
                isApprovalRequired = false,
            )
        coEvery { vaultRepository.get("vault-1") } returns Vault(id = "vault-1", name = "Test")

        val vm = viewModel()
        vm.loadSwapTransaction("tx-2")

        assertNull(vm.keysignPayload?.approvePayload)
    }

    private fun swapTransaction(
        amount: BigInteger,
        dstAddress: String,
        approveSpender: String,
        isApprovalRequired: Boolean,
    ): RegularSwapTransaction {
        val usdt =
            Coin(
                chain = Chain.Ethereum,
                ticker = "USDT",
                logo = "usdt",
                address = "0xowner",
                decimal = 6,
                hexPublicKey = "hex",
                priceProviderID = "tether",
                contractAddress = "0xdac17f958d2ee523a2206206994597c13d831ec7",
                isNativeToken = false,
            )
        val eth =
            Coin(
                chain = Chain.Ethereum,
                ticker = "ETH",
                logo = "eth",
                address = "0xowner",
                decimal = 18,
                hexPublicKey = "hex",
                priceProviderID = "ethereum",
                contractAddress = "",
                isNativeToken = true,
            )
        val srcValue = TokenValue(value = amount, token = usdt)
        val quote =
            EVMSwapQuoteJson(
                dstAmount = "2690000000000000",
                tx =
                    OneInchSwapTxJson(
                        from = "0xowner",
                        to = dstAddress,
                        allowanceTarget = approveSpender,
                        gas = 600000,
                        data = "0xda5d4170",
                        value = "0",
                        gasPrice = "1000000000",
                    ),
            )
        return RegularSwapTransaction(
            id = "tx-1",
            vaultId = "vault-1",
            srcToken = usdt,
            srcTokenValue = srcValue,
            dstToken = eth,
            dstAddress = dstAddress,
            approveSpender = approveSpender,
            expectedDstTokenValue = TokenValue(value = BigInteger("2690000000000000"), token = eth),
            blockChainSpecific = mockk<BlockChainSpecificAndUtxo>(relaxed = true),
            estimatedFees = srcValue,
            gasFees = TokenValue(value = BigInteger("100000000000000"), token = eth),
            memo = null,
            payload =
                SwapPayload.EVM(
                    EVMSwapPayloadJson(
                        fromCoin = usdt,
                        toCoin = eth,
                        fromAmount = amount,
                        toAmountDecimal = BigDecimal("0.00269"),
                        quote = quote,
                        provider = "swapkit",
                    )
                ),
            isApprovalRequired = isApprovalRequired,
            gasFeeFiatValue = FiatValue(BigDecimal.ZERO, "USD"),
        )
    }
}
