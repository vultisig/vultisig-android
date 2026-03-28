package com.vultisig.wallet.data.blockchain.cosmos

import com.vultisig.wallet.data.blockchain.model.GasFees
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CosmosFeeServiceTest {

    private val feeService = CosmosFeeService()

    private fun transfer(chain: Chain) =
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
                    contractAddress = "",
                    isNativeToken = true,
                ),
            vault = VaultData(vaultHexPublicKey = "pub", vaultHexChainCode = "chain"),
            amount = BigInteger("1000000"),
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
    fun `Osmosis gas limit is 300000`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.Osmosis)) as GasFees
        assertEquals(BigInteger("300000"), fee.limit)
    }

    @Test
    fun `QBTC returns GasFees type`() = runTest {
        val fee = feeService.calculateDefaultFees(transfer(Chain.Qbtc))
        assertTrue(fee is GasFees)
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
