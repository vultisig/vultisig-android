package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenBalanceWrapped
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TiersNFTRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.NO_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_DISCOUNT_BPS
import com.vultisig.wallet.data.usecases.GetDiscountBpsUseCaseImpl.Companion.SILVER_TIER_THRESHOLD
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetDiscountBpsUseCaseTest {

    private val vaultRepository = mockk<VaultRepository>()
    private val balanceRepository = mockk<BalanceRepository>()
    private val chainAccountAddressRepository = mockk<ChainAccountAddressRepository>()
    private val tiersNFTRepository = mockk<TiersNFTRepository>()

    private val useCase =
        GetDiscountBpsUseCaseImpl(
            vaultRepository = vaultRepository,
            balanceRepository = balanceRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            tiersNFTRepository = tiersNFTRepository,
        )

    private val vaultId = "vault-id"
    private val ethAddress = "0xVaultEthAddress"

    @Test
    fun `getVultBalance reads the live balance when Ethereum is not enabled`() = runTest {
        val silverBalance = SILVER_TIER_THRESHOLD
        givenVault(coins = emptyList())
        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns emptyList()
        coEvery { balanceRepository.getBalanceOrNull(ethAddress, any()) } returns silverBalance

        assertEquals(silverBalance, useCase.getVultBalance(vaultId))
        assertTrue(useCase.hasReachedSilverTier(vaultId))
    }

    @Test
    fun `getVultBalance is null when there is no cache and the live read fails`() = runTest {
        givenVault(coins = emptyList())
        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns emptyList()
        coEvery { balanceRepository.getBalanceOrNull(ethAddress, any()) } returns null

        assertNull(useCase.getVultBalance(vaultId))
        assertFalse(useCase.hasReachedSilverTier(vaultId))
        assertEquals(NO_DISCOUNT_BPS, useCase.invoke(vaultId, SwapProvider.THORCHAIN))
    }

    @Test
    fun `getVultBalance prefers the live balance over an untimestamped cached row`() = runTest {
        // The Room row has no timestamp, so a co-signer device opened once months ago would keep
        // quoting the tier it held then. A vault that has since sold its VULT must not be
        // discounted on the transaction the initiator is signing undiscounted.
        val vultCoin = Coins.Ethereum.VULT.copy(address = ethAddress)
        givenVault(coins = listOf(vultCoin))
        givenCachedBalance(vultCoin, SILVER_TIER_THRESHOLD)
        coEvery { balanceRepository.getBalanceOrNull(ethAddress, any()) } returns BigInteger.ZERO
        coEvery { tiersNFTRepository.hasTierNFT(vaultId) } returns false

        assertEquals(BigInteger.ZERO, useCase.getVultBalance(vaultId))
        assertFalse(useCase.hasReachedSilverTier(vaultId))
        assertEquals(NO_DISCOUNT_BPS, useCase.invoke(vaultId, SwapProvider.THORCHAIN))
    }

    @Test
    fun `getVultBalance sees a tier the cached row predates`() = runTest {
        val vultCoin = Coins.Ethereum.VULT.copy(address = ethAddress)
        givenVault(coins = listOf(vultCoin))
        givenCachedBalance(vultCoin, BigInteger.ZERO)
        coEvery { balanceRepository.getBalanceOrNull(ethAddress, any()) } returns
            SILVER_TIER_THRESHOLD
        coEvery { tiersNFTRepository.hasTierNFT(vaultId) } returns false

        assertEquals(SILVER_TIER_THRESHOLD, useCase.getVultBalance(vaultId))
        assertEquals(SILVER_DISCOUNT_BPS, useCase.invoke(vaultId, SwapProvider.THORCHAIN))
    }

    @Test
    fun `getVultBalance falls back to the cached row when the live read fails`() = runTest {
        // A stale tier still beats fabricating "no discount" for a holder who has one.
        val vultCoin = Coins.Ethereum.VULT.copy(address = ethAddress)
        givenVault(coins = listOf(vultCoin))
        givenCachedBalance(vultCoin, SILVER_TIER_THRESHOLD)
        coEvery { balanceRepository.getBalanceOrNull(ethAddress, any()) } returns null
        coEvery { tiersNFTRepository.hasTierNFT(vaultId) } returns false

        assertEquals(SILVER_TIER_THRESHOLD, useCase.getVultBalance(vaultId))
        assertEquals(SILVER_DISCOUNT_BPS, useCase.invoke(vaultId, SwapProvider.THORCHAIN))
    }

    @Test
    fun `getVultBalance reads the chain once per vault within the TTL`() = runTest {
        // Reading live on every call is only affordable because one quote fetch's provider
        // candidates share a single read.
        val vultCoin = Coins.Ethereum.VULT.copy(address = ethAddress)
        givenVault(coins = listOf(vultCoin))
        coEvery { balanceRepository.getBalanceOrNull(ethAddress, any()) } returns
            SILVER_TIER_THRESHOLD
        coEvery { tiersNFTRepository.hasTierNFT(vaultId) } returns false

        repeat(3) { useCase.invoke(vaultId, SwapProvider.THORCHAIN) }

        coVerify(exactly = 1) { balanceRepository.getBalanceOrNull(ethAddress, any()) }
        coVerify(exactly = 0) { balanceRepository.getCachedTokenBalances(any(), any()) }
    }

    private fun givenCachedBalance(vultCoin: Coin, balance: BigInteger) {
        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns
            listOf(
                TokenBalanceWrapped(
                    tokenBalance =
                        TokenBalance(tokenValue = TokenValue(balance, vultCoin), fiatValue = null),
                    address = ethAddress,
                    coinId = Coins.Ethereum.VULT.id,
                )
            )
    }

    private fun givenVault(coins: List<Coin>) {
        val vault = Vault(id = vaultId, name = "vault", coins = coins)
        coEvery { vaultRepository.get(vaultId) } returns vault
        coEvery { chainAccountAddressRepository.getAddress(Chain.Ethereum, vault) } returns
            (ethAddress to "pubkey")
    }
}
