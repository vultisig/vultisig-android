package com.vultisig.wallet.ui.models.deposit

import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Eip1559
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.ui.utils.UiText
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class DepositGasFeeHelperTest {

    private val vaultRepository: VaultRepository = mockk()
    private val feeServiceComposite: FeeServiceComposite = mockk()
    private val tokenRepository: TokenRepository = mockk()
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase = mockk()
    private val blockChainSpecificRepository: BlockChainSpecificRepository = mockk()

    private val estimatorParams = slot<GasFeeParams>()

    private val helper =
        DepositGasFeeHelper(
            vaultRepository = vaultRepository,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            gasFeeToEstimatedFee = gasFeeToEstimatedFee,
            tokenPriceRepository = mockk<TokenPriceRepository>(),
            blockChainSpecificRepository = blockChainSpecificRepository,
        )

    @BeforeEach
    fun setUp() {
        // Mirrors GasFeeToEstimatedFeeUseCaseImpl: the displayed token value is fee × limit.
        coEvery { gasFeeToEstimatedFee(capture(estimatorParams)) } answers
            {
                val params = estimatorParams.captured
                val total = params.gasFee.value * params.gasLimit
                EstimatedGasFee(
                    formattedTokenValue = total.toString(),
                    formattedFiatValue = "",
                    tokenValue = params.gasFee.copy(value = total),
                    fiatValue = mockk(),
                )
            }
    }

    @Test
    fun `EVM fee total is priced with a unit limit, not the signed gas limit`() = runTest {
        val gasFee = TokenValue(value = FEE_TOTAL, token = ETH)

        helper.getFeesFiatValue(Chain.Ethereum, evmSpecific(), gasFee, ETH)

        estimatorParams.captured.gasLimit shouldBe BigInteger.ONE
        estimatorParams.captured.gasFee shouldBe gasFee
    }

    @Test
    fun `EVM fee row shows the fee-service total`() = runTest {
        coEvery { vaultRepository.get(VAULT_ID) } returns Vault(id = VAULT_ID, name = "Main")
        coEvery { feeServiceComposite.calculateFees(any()) } returns
            Eip1559(
                limit = GAS_LIMIT,
                networkPrice = MAX_FEE_PER_GAS,
                maxFeePerGas = MAX_FEE_PER_GAS,
                maxPriorityFeePerGas = BigInteger.ONE,
                amount = FEE_TOTAL,
            )
        coEvery { tokenRepository.getNativeToken(Chain.Ethereum.id) } returns ETH
        coEvery {
            blockChainSpecificRepository.getSpecific(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns evmSpecific()
        val shown = CompletableDeferred<UiText>()

        helper.loadGasFeeForDisplay(
            scope = this,
            vaultId = VAULT_ID,
            chain = Chain.Ethereum,
            address =
                Address(
                    chain = Chain.Ethereum,
                    address = ETH.address,
                    accounts =
                        listOf(
                            Account(
                                token = ETH,
                                tokenValue = null,
                                fiatValue = null,
                                price = null,
                            )
                        ),
                ),
        ) { totalGas, _ ->
            shown.complete(totalGas)
        }

        shown.await() shouldBe UiText.DynamicString(FEE_TOTAL.toString())
    }

    private fun evmSpecific() =
        BlockChainSpecificAndUtxo(
            BlockChainSpecific.Ethereum(
                maxFeePerGasWei = MAX_FEE_PER_GAS,
                priorityFeeWei = BigInteger.ONE,
                nonce = BigInteger.ZERO,
                gasLimit = GAS_LIMIT,
            )
        )

    private companion object {
        const val VAULT_ID = "vault-id"
        val ETH = Coins.Ethereum.ETH.copy(address = "0x1111111111111111111111111111111111111111")
        val GAS_LIMIT: BigInteger = BigInteger.valueOf(23_000)
        val MAX_FEE_PER_GAS: BigInteger = BigInteger.valueOf(2_000_000_000)
        val FEE_TOTAL: BigInteger = MAX_FEE_PER_GAS * GAS_LIMIT
    }
}
