package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.VaultDao
import com.vultisig.wallet.data.db.models.CoinEntity
import com.vultisig.wallet.data.db.models.VaultEntity
import com.vultisig.wallet.data.db.models.VaultWithKeySharesAndTokens
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class VaultRepositoryImplTest {

    private val vaultDao: VaultDao = mockk()
    private val tokenRepository: TokenRepository = mockk {
        coEvery { getToken(any()) } returns null
    }

    private val repository = VaultRepositoryImpl(vaultDao, tokenRepository)

    @Test
    fun `getEnabledTokens emits empty list when vault is missing`() = runTest {
        every { vaultDao.loadByIdAsFlow(MISSING_ID) } returns flowOf(null)

        val tokens = repository.getEnabledTokens(MISSING_ID).first()

        assertEquals(emptyList<Any>(), tokens)
    }

    @Test
    fun `getEnabledTokens emits empty list when vault has no coins`() = runTest {
        every { vaultDao.loadByIdAsFlow(VAULT_ID) } returns flowOf(vaultEntity())

        val tokens = repository.getEnabledTokens(VAULT_ID).first()

        assertEquals(emptyList<Any>(), tokens)
    }

    @Test
    fun `getEnabledTokens emits coins from vault`() = runTest {
        every { vaultDao.loadByIdAsFlow(VAULT_ID) } returns
            flowOf(vaultEntity(coin("ETH", "Ethereum"), coin("BTC", "Bitcoin")))

        val tokens = repository.getEnabledTokens(VAULT_ID).first()

        assertEquals(listOf("ETH", "BTC"), tokens.map { it.ticker })
    }

    @Test
    fun `getEnabledChains emits empty set when vault is missing`() = runTest {
        every { vaultDao.loadByIdAsFlow(MISSING_ID) } returns flowOf(null)

        val chains = repository.getEnabledChains(MISSING_ID).first()

        assertEquals(emptySet<Chain>(), chains)
    }

    @Test
    fun `getEnabledChains keeps only native token chains`() = runTest {
        every { vaultDao.loadByIdAsFlow(VAULT_ID) } returns
            flowOf(
                vaultEntity(
                    coin("ETH", "Ethereum"),
                    coin("USDC", "Ethereum", contract = "0xUsdc"),
                    coin("BTC", "Bitcoin"),
                )
            )

        val chains = repository.getEnabledChains(VAULT_ID).first()

        assertEquals(setOf(Chain.Ethereum, Chain.Bitcoin), chains)
    }

    private fun vaultEntity(vararg coins: CoinEntity) =
        VaultWithKeySharesAndTokens(
            vault =
                VaultEntity(
                    id = VAULT_ID,
                    name = "Test",
                    pubKeyEcdsa = "",
                    pubKeyEddsa = "",
                    hexChainCode = "",
                    localPartyID = "",
                    resharePrefix = "",
                    libType = SigningLibType.DKLS,
                ),
            keyShares = emptyList(),
            signers = emptyList(),
            coins = coins.toList(),
            chainPublicKeys = emptyList(),
        )

    private fun coin(ticker: String, chain: String, contract: String = ""): CoinEntity =
        CoinEntity(
            vaultId = VAULT_ID,
            id = "$ticker-$chain",
            chain = chain,
            ticker = ticker,
            address = "",
            decimals = 18,
            hexPublicKey = "",
            priceProviderID = "",
            contractAddress = contract,
            logo = "",
        )

    private companion object {
        const val VAULT_ID = "vault-1"
        const val MISSING_ID = "vault-missing"
    }
}
