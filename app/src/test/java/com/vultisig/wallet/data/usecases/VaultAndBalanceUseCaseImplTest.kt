package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.HomeBalanceDisplay
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.DefaultDeFiChainsRepository
import com.vultisig.wallet.data.repositories.HomeDisplayRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class VaultAndBalanceUseCaseImplTest {

    private val vault = Vault(id = "vault-id", name = "Vault")

    private fun usd(value: Int) = FiatValue(BigDecimal(value), "USD")

    private fun address(chain: Chain, token: com.vultisig.wallet.data.models.Coin, fiat: Int) =
        Address(
            chain = chain,
            address = "${chain.raw}-address",
            accounts =
                listOf(
                    Account(token = token, tokenValue = null, fiatValue = usd(fiat), price = null)
                ),
        )

    private val wallet = address(Chain.Bitcoin, Coins.Bitcoin.BTC, 100)
    private val deFi = address(Chain.ThorChain, Coins.ThorChain.RUNE, 25)

    private fun useCase(
        display: HomeBalanceDisplay,
        deFiAddresses: kotlinx.coroutines.flow.Flow<List<Address>> = flowOf(listOf(deFi)),
        selectedChains: Set<Chain> = setOf(Chain.ThorChain),
    ): VaultAndBalanceUseCaseImpl {
        val accountsRepository =
            mockk<AccountsRepository> {
                every { loadCachedAddresses(vault.id) } returns flowOf(listOf(wallet))
                coEvery { loadDeFiAddresses(vault.id, isRefresh = false) } returns deFiAddresses
            }
        return VaultAndBalanceUseCaseImpl(
            fiatValueToStringMapper = mockk(relaxed = true),
            accountsRepository = accountsRepository,
            homeDisplayRepository =
                mockk<HomeDisplayRepository> { every { balanceDisplay } returns flowOf(display) },
            defaultDeFiChainsRepository =
                mockk<DefaultDeFiChainsRepository> {
                    every { getDefaultChains(vault.id) } returns flowOf(selectedChains)
                },
            hasCircleAccount = mockk<HasCircleAccountUseCase> { coEvery { this@mockk(any()) } returns false },
        )
    }

    @Test
    fun `Separate shows the wallet total only`() = runTest {
        useCase(HomeBalanceDisplay.Separate)(vault).balanceFiatValue shouldBe usd(100)
    }

    @Test
    fun `Combined adds the cached DeFi value`() = runTest {
        useCase(HomeBalanceDisplay.Combined)(vault).balanceFiatValue shouldBe usd(125)
    }

    @Test
    fun `Combined ignores DeFi chains the vault did not select`() = runTest {
        useCase(HomeBalanceDisplay.Combined, selectedChains = emptySet())(vault)
            .balanceFiatValue shouldBe usd(100)
    }

    @Test
    fun `Combined with nothing cached falls back to the wallet total`() = runTest {
        useCase(HomeBalanceDisplay.Combined, deFiAddresses = flowOf())(vault)
            .balanceFiatValue shouldBe usd(100)
    }

    @Test
    fun `Combined falls back to the wallet total when the DeFi cache read fails`() = runTest {
        useCase(
            HomeBalanceDisplay.Combined,
            deFiAddresses = flow { throw IllegalStateException("cache read failed") },
        )(vault)
            .balanceFiatValue shouldBe usd(100)
    }
}
