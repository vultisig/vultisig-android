@file:OptIn(ExperimentalCoroutinesApi::class)

package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins how a plain send resolves its coin.
 *
 * The DeFi-only receipts are deliberately kept out of token discovery, so they are never vault
 * coins — yet they are ordinary bank denoms the position card offers a Transfer action for. Looking
 * the coin up in the vault alone left that send with nothing to sign.
 */
internal class KeysignShareViewModelReceiptSendTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val vaultRepository: VaultRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper =
        mockk(relaxed = true)
    private val swapTransactionRepository: SwapTransactionRepository = mockk(relaxed = true)
    private val depositTransaction: DepositTransactionRepository = mockk(relaxed = true)
    private val customMessagePayloadRepo: CustomMessagePayloadRepo = mockk(relaxed = true)
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat = mockk(relaxed = true)
    private val generateQrBitmap: GenerateQrBitmap = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mapTokenValueToStringWithUnit(any()) } returns "0"
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a ybRUNE transfer signs the receipt the form staged, not a vault coin`() = runTest {
        val receipt =
            Coins.ThorChain.ybRUNE.copy(address = THOR_ADDRESS, hexPublicKey = THOR_PUBLIC_KEY)
        coEvery { transactionRepository.getTransaction(TX_ID) } returns transaction(receipt)
        coEvery { vaultRepository.get(VAULT_ID) } returns
            Vault(id = VAULT_ID, name = "Test", coins = listOf(rune()))

        val vm = viewModel()
        vm.loadTransaction(TX_ID)

        val coin = requireNotNull(vm.keysignPayload?.coin)
        assertEquals(Coins.ThorChain.ybRUNE.id, coin.id)
        // The denom that funds the send, plus the key material the chain's own account supplied.
        assertEquals(Coins.ThorChain.ybRUNE.contractAddress, coin.contractAddress)
        assertEquals(THOR_ADDRESS, coin.address)
        assertEquals(THOR_PUBLIC_KEY, coin.hexPublicKey)
    }

    @Test
    fun `a wallet token is still resolved from the vault`() = runTest {
        // The vault's own copy stays authoritative: the staged token is only a fallback for the
        // receipts the vault is never allowed to carry.
        val vaultRune = rune()
        coEvery { transactionRepository.getTransaction(TX_ID) } returns
            transaction(vaultRune.copy(address = "thor1stale"))
        coEvery { vaultRepository.get(VAULT_ID) } returns
            Vault(id = VAULT_ID, name = "Test", coins = listOf(vaultRune))

        val vm = viewModel()
        vm.loadTransaction(TX_ID)

        assertEquals(THOR_ADDRESS, vm.keysignPayload?.coin?.address)
    }

    @Test
    fun `a token the vault does not hold at all still fails loudly`() = runTest {
        coEvery { transactionRepository.getTransaction(TX_ID) } returns
            transaction(Coins.ThorChain.TCY.copy(address = THOR_ADDRESS))
        coEvery { vaultRepository.get(VAULT_ID) } returns
            Vault(id = VAULT_ID, name = "Test", coins = listOf(rune()))

        val vm = viewModel()
        val failure = runCatching { vm.loadTransaction(TX_ID) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
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

    private fun rune(): Coin =
        Coins.ThorChain.RUNE.copy(address = THOR_ADDRESS, hexPublicKey = THOR_PUBLIC_KEY)

    private fun transaction(token: Coin): Transaction =
        Transaction(
            id = TX_ID,
            vaultId = VAULT_ID,
            chainId = Chain.ThorChain.id,
            token = token,
            srcAddress = THOR_ADDRESS,
            dstAddress = "thor1dst",
            tokenValue = TokenValue(value = BigInteger("100000000"), token = token),
            fiatValue = FiatValue(BigDecimal.ZERO, "USD"),
            gasFee = TokenValue(value = BigInteger("2000000"), token = token),
            totalGas = "2000000",
            memo = null,
            estimatedFee = "0",
            blockChainSpecific = mockk<BlockChainSpecific>(relaxed = true),
        )

    private companion object {
        const val TX_ID = "tx-1"
        const val VAULT_ID = "vault-1"
        const val THOR_ADDRESS = "thor1owner"
        const val THOR_PUBLIC_KEY =
            "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    }
}
