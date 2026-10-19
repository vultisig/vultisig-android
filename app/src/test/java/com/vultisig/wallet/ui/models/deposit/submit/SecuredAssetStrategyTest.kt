package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.BasicFee
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.deposit.DepositFieldStates
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

internal class SecuredAssetStrategyTest {

    private val fields = DepositFieldStates()
    private val vaultRepository: VaultRepository = mockk()
    private val feeService: FeeServiceComposite = mockk()
    private val tokenRepository: TokenRepository = mockk()
    private val specificRepo: BlockChainSpecificRepository = mockk()
    private val eth = gateCoin(Chain.Ethereum, "ETH", "0xself", decimal = 18)
    private val btc = gateCoin(Chain.Bitcoin, "BTC", "bc1self")

    @BeforeEach
    fun setUp() {
        fields.thorAddressFieldState.edit { replace(0, length, THOR_ADDRESS) }
        coEvery { vaultRepository.get(GATE_VAULT_ID) } returns Vault(id = GATE_VAULT_ID, name = "v")
        coEvery { feeService.calculateFees(any()) } returns BasicFee(GAS)
        coEvery { tokenRepository.getNativeToken(Chain.Ethereum.id) } returns eth
        coEvery { tokenRepository.getNativeToken(Chain.Bitcoin.id) } returns btc
        coEvery {
            specificRepo.getSpecific(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns BlockChainSpecificAndUtxo(mockk(relaxed = true))
    }

    @Test
    fun `an EVM amount that leaves no room for the fee is refused`() = runTest {
        enterAmount("1")
        val ethAccount = gateAccount(eth, ONE_ETH)

        val error =
            assertFailsWith<InvalidTransactionDataException> {
                build(Chain.Ethereum, ethAccount, gateAddress(ethAccount)).build()
            }

        assertInsufficientBalance(error)
    }

    @Test
    fun `an EVM amount the balance covers with the fee mints to the inbound vault`() = runTest {
        enterAmount("1")
        val ethAccount = gateAccount(eth, ONE_ETH + GAS)

        val tx = build(Chain.Ethereum, ethAccount, gateAddress(ethAccount)).build()

        assertEquals(ONE_ETH, tx.srcTokenValue.value)
        assertEquals(INBOUND, tx.dstAddress)
        assertEquals("SECURE+:$THOR_ADDRESS", tx.memo)
    }

    @Test
    fun `a UTXO deposit is left to the planner and never consults the form balance`() = runTest {
        enterAmount("0.01")

        val tx = build(Chain.Bitcoin, gateAccount(btc, balance = null), formAddress = null).build()

        assertEquals(BigInteger.valueOf(1_000_000), tx.srcTokenValue.value)
        assertEquals(INBOUND, tx.dstAddress)
    }

    private fun enterAmount(amount: String) {
        fields.tokenAmountFieldState.edit { replace(0, length, amount) }
    }

    private fun build(chain: Chain, selectedAccount: Account, formAddress: Address?) =
        SecuredAssetStrategy(
            vaultIdProvider = { GATE_VAULT_ID },
            chainProvider = { chain },
            thorAddressFieldState = fields.thorAddressFieldState,
            tokenAmountFieldState = fields.tokenAmountFieldState,
            selectedAccountProvider = { selectedAccount },
            addressProvider = { formAddress },
            resolveInboundAddress = { INBOUND },
            requireTokenAmount = amountGate(chain, fields)::requireTokenAmount,
            vaultRepository = vaultRepository,
            feeServiceComposite = feeService,
            tokenRepository = tokenRepository,
            blockChainSpecificRepository = specificRepo,
            gasFeeToEstimate = { params -> gateEstimatedFee(params.gasFee) },
            getBitcoinTransactionPlan = { _, _, _, _, _, _ ->
                Bitcoin.TransactionPlan.newBuilder().setError(SigningError.OK).build()
            },
        )

    private companion object {
        const val THOR_ADDRESS = "thor1self"
        const val INBOUND = "inbound-vault"
        val ONE_ETH: BigInteger = BigInteger.TEN.pow(18)
        val GAS: BigInteger = BigInteger.valueOf(21_000)
    }
}
