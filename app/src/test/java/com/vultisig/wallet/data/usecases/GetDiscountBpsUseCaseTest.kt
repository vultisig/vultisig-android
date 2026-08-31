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
    fun `getVultBalance keeps using the cached balance of an enabled VULT coin`() = runTest {
        val cachedBalance = SILVER_TIER_THRESHOLD
        val vultCoin = Coins.Ethereum.VULT.copy(address = ethAddress)
        givenVault(coins = listOf(vultCoin))
        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns
            listOf(
                TokenBalanceWrapped(
                    tokenBalance =
                        TokenBalance(
                            tokenValue = TokenValue(cachedBalance, vultCoin),
                            fiatValue = null,
                        ),
                    address = ethAddress,
                    coinId = Coins.Ethereum.VULT.id,
                )
            )
        coEvery { tiersNFTRepository.hasTierNFT(vaultId) } returns false

        assertEquals(cachedBalance, useCase.getVultBalance(vaultId))
        assertEquals(SILVER_DISCOUNT_BPS, useCase.invoke(vaultId, SwapProvider.THORCHAIN))
    }

    @Test
    fun `getVultBalance returns a cached zero without a live read`() = runTest {
        val vultCoin = Coins.Ethereum.VULT.copy(address = ethAddress)
        givenVault(coins = listOf(vultCoin))
        coEvery { balanceRepository.getCachedTokenBalances(any(), any()) } returns
            listOf(
                TokenBalanceWrapped(
                    tokenBalance =
                        TokenBalance(
                            tokenValue = TokenValue(BigInteger.ZERO, vultCoin),
                            fiatValue = null,
                        ),
                    address = ethAddress,
                    coinId = Coins.Ethereum.VULT.id,
                )
            )

        assertEquals(BigInteger.ZERO, useCase.getVultBalance(vaultId))
        assertFalse(useCase.hasReachedSilverTier(vaultId))
    }

    private fun givenVault(coins: List<Coin>) {
        val vault = Vault(id = vaultId, name = "vault", coins = coins)
        coEvery { vaultRepository.get(vaultId) } returns vault
        coEvery { chainAccountAddressRepository.getAddress(Chain.Ethereum, vault) } returns
            (ethAddress to "pubkey")
    }
}
