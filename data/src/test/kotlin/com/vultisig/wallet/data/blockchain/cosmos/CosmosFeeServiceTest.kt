package com.vultisig.wallet.data.blockchain.cosmos

import com.vultisig.wallet.data.api.CosmosApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CosmosFeeServiceTest {

    private val cosmosApi: CosmosApi = mockk(relaxed = true)
    private val cosmosApiFactory: CosmosApiFactory = mockk {
        every { createCosmosApi(any()) } returns cosmosApi
    }

    private val feeService = CosmosFeeService(cosmosApiFactory)

    init {
        // Default live burn-tax rate (0.5%) for Terra Classic taxable sends.
        coEvery { cosmosApi.getTerraClassicBurnTaxRate() } returns "0.005"
    }

    private fun transfer(
        chain: Chain,
        amount: BigInteger = BigInteger("1000000"),
        contractAddress: String = "",
        isNativeToken: Boolean = true,
    ) =
        Transfer(
            coin =
                Coin(
                    chain = chain,
                    ticker = "TEST",
                    logo = "",
                    address = "test_address",
                    decimal = 8,
                    hexPublicKey = "test_pub_key",
                    priceProviderID = "",
                    contractAddress = contractAddress,
                    isNativeToken = isNativeToken,
                ),
            vault = VaultData(vaultHexPublicKey = "pub", vaultHexChainCode = "chain"),
            amount = amount,
            to = "recipient_address",
        )

    @Test
    fun `QBTC gas limit is 300000`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.Qbtc)) as GasFees
        assertEquals(BigInteger("300000"), fee.limit)
    }

    @Test
    fun `QBTC fee amount is 7500`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.Qbtc)) as GasFees
        assertEquals(BigInteger("7500"), fee.amount)
    }

    @Test
    fun `GaiaChain gas limit is 200000`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.GaiaChain)) as GasFees
        assertEquals(BigInteger("200000"), fee.limit)
    }

    @Test
    fun `GaiaChain fee amount is 7500`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.GaiaChain)) as GasFees
        assertEquals(BigInteger("7500"), fee.amount)
    }

    @Test
    fun `Terra gas limit is 300000`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.Terra)) as GasFees
        assertEquals(BigInteger("300000"), fee.limit)
    }

    @Test
    fun `Terra fee amount is 7500 at the static 300k limit`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.Terra)) as GasFees
        assertEquals(BigInteger("7500"), fee.amount)
    }

    @Test
    fun `Terra fee amount scales with a relayed gas limit not equal to 300k`() {
        // native LUNA: 0.025 uluna/gas × 450_000 = 11_250; × 300_000 = the static 7500 uluna.
        assertEquals(BigInteger("7500"), CosmosFeeService.terraFeeAmount(300_000L))
        assertEquals(BigInteger("11250"), CosmosFeeService.terraFeeAmount(450_000L))
        // rounds up: 0.025 × 111_111 = 2_777.775 → ceil → 2_778.
        assertEquals(BigInteger("2778"), CosmosFeeService.terraFeeAmount(111_111L))
    }

    @Test
    fun `terraGasLimitForFeeAmount inverts terraFeeAmount`() {
        // floor(feeAmount / 0.025): 2877 uluna → 115_080 gas (the un-floored ~gas_used×1.3 fee).
        assertEquals(
            BigInteger("115080"),
            CosmosFeeService.terraGasLimitForFeeAmount(BigInteger("2877")),
        )
        // the flat 7500 maps back to the static 300k limit — relaying it is a no-op, still valid.
        assertEquals(
            BigInteger("300000"),
            CosmosFeeService.terraGasLimitForFeeAmount(BigInteger("7500")),
        )
    }

    @Test
    fun `relayed Terra gas limit is always covered by its fee amount`() {
        // Core safety invariant of the #5279 fix: minGasPrice × relayedLimit ≤ signed fee amount,
        // so a broadcast with the relayed (lower) gas_wanted can never be rejected "insufficient
        // fee". Holds for any simulated gas because terraGasLimitForFeeAmount floor-divides.
        val price = java.math.BigDecimal("0.025")
        for (paddedLimit in listOf(50_000L, 88_516L, 115_071L, 250_000L)) {
            val amount = CosmosFeeService.terraFeeAmount(paddedLimit)
            val relayed = CosmosFeeService.terraGasLimitForFeeAmount(amount)
            assertTrue(
                price.multiply(relayed.toBigDecimal()) <= amount.toBigDecimal(),
                "fee $amount must cover 0.025 × $relayed",
            )
        }
    }

    @Test
    fun `non-native Terra token send keeps the static fee`() = runTest {
        // CW20 / IBC Terra sends are not a native MsgSend, so they are never simulated and keep the
        // static 300k limit + 7500 uluna (the un-floored simulated path is native-only).
        val fee =
            feeService.calculateDefaultFees(
                transfer(Chain.Terra, contractAddress = "terra1token", isNativeToken = false)
            ) as GasFees
        assertEquals(BigInteger("300000"), fee.limit)
        assertEquals(BigInteger("7500"), fee.amount)
    }

    @Test
    fun `Osmosis gas limit is 300000`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.Osmosis)) as GasFees
        assertEquals(BigInteger("300000"), fee.limit)
    }

    @Test
    fun `Osmosis fee amount is 25000`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.Osmosis)) as GasFees
        assertEquals(CosmosFeeService.OSMOSIS_MIN_FEE_UOSMO.toBigInteger(), fee.amount)
    }

    @Test
    fun `Akash fee amount is floored to 25000 uakt`() = runTest {
        // Akash's chain-registry gas price computes only 7500 uakt, below the
        // network's accepted minimum. The default fee must floor to 0.025 AKT.
        val fee = feeService.calculateDefaultFees(transfer(Chain.Akash)) as GasFees
        assertEquals(CosmosFeeService.AKASH_MIN_FEE_UAKT.toBigInteger(), fee.amount)
    }

    @Test
    fun `QBTC returns GasFees type`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.Qbtc))
        assertTrue(fee is GasFees)
    }

    @Test
    fun `TerraClassic native LUNC fee is uluna base gas plus burn tax`() = runTest {
        // amount 1_000_000 × 0.5% = 5_000 tax + 8_497_500 base = 8_502_500 uluna.
        val fee =
            feeService.calculateDefaultFees(
                transfer(Chain.TerraClassic, amount = BigInteger("1000000"))
            ) as GasFees
        assertEquals(BigInteger("300000"), fee.limit)
        assertEquals(TerraClassicTax.ULUNA_BASE_GAS.toBigInteger() + BigInteger("5000"), fee.amount)
    }

    @Test
    fun `TerraClassic USTC bank denom fee is uusd base gas plus burn tax`() = runTest {
        // USTC (uusd) is a bank denom: base 225_000 uusd + 0.5% of 2_000_000 = 10_000 → 235_000.
        val fee =
            feeService.calculateDefaultFees(
                transfer(
                    Chain.TerraClassic,
                    amount = BigInteger("2000000"),
                    contractAddress = "uusd",
                    isNativeToken = false,
                )
            ) as GasFees
        assertEquals(TerraClassicTax.UUSD_BASE_GAS.toBigInteger() + BigInteger("10000"), fee.amount)
    }

    @Test
    fun `TerraClassic CW20 token gets uluna base gas with no burn tax`() = runTest {
        // CW20 (terra1…) pays its fee in uluna while the send is in the token denom, so no tax
        // fold.
        val fee =
            feeService.calculateDefaultFees(
                transfer(
                    Chain.TerraClassic,
                    amount = BigInteger("9999999999"),
                    contractAddress = "terra1abcdef",
                    isNativeToken = false,
                )
            ) as GasFees
        assertEquals(TerraClassicTax.ULUNA_BASE_GAS.toBigInteger(), fee.amount)
    }

    @Test
    fun `TerraClassic falls back to default rate when live fetch returns null`() = runTest {
        coEvery { cosmosApi.getTerraClassicBurnTaxRate() } returns null
        // Fallback 0.5%: 1_000_000 × 0.005 = 5_000 tax on top of the uluna base.
        val fee =
            feeService.calculateDefaultFees(
                transfer(Chain.TerraClassic, amount = BigInteger("1000000"))
            ) as GasFees
        assertEquals(TerraClassicTax.ULUNA_BASE_GAS.toBigInteger() + BigInteger("5000"), fee.amount)
    }

    @Test
    fun `all supported cosmos chains return fees without error`() = runTest {
        val cosmosChains =
            listOf(
                Chain.GaiaChain,
                Chain.Kujira,
                Chain.Osmosis,
                Chain.Terra,
                Chain.Akash,
                Chain.Qbtc,
                Chain.Noble,
                Chain.TerraClassic,
                Chain.Dydx,
            )
        for (chain in cosmosChains) {
            val fee = feeService.calculateDefaultFees(transfer(chain)) as GasFees
            assertTrue(
                fee.limit > BigInteger.ZERO,
                "Gas limit for ${chain.name} should be positive",
            )
            assertTrue(
                fee.amount > BigInteger.ZERO,
                "Fee amount for ${chain.name} should be positive",
            )
        }
    }
}
