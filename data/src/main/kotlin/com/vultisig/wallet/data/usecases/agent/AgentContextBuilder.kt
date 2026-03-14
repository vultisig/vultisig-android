package com.vultisig.wallet.data.usecases.agent

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.agent.AgentAddressBookEntry
import com.vultisig.wallet.data.models.agent.AgentBalanceInfo
import com.vultisig.wallet.data.models.agent.AgentCoinInfo
import com.vultisig.wallet.data.models.agent.AgentMessageContext
import com.vultisig.wallet.data.models.agent.AgentVaultInfo
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentContextBuilder
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val balanceRepository: BalanceRepository,
    private val addressBookRepository: AddressBookRepository,
) {

    suspend fun buildFullContext(vault: Vault): AgentMessageContext {
        val coins = vault.coins
        val addresses = buildAddressMap(coins)
        val coinInfos = coins.map { it.toCoinInfo() }
        val balances = buildBalances(coins)
        val addressBook = buildAddressBook()
        val allVaults = buildAllVaults()

        return AgentMessageContext(
            vaultAddress = vault.pubKeyECDSA,
            vaultName = vault.name,
            addresses = addresses,
            coins = coinInfos,
            balances = balances,
            addressBook = addressBook,
            allVaults = allVaults,
            instructions = AGENT_INSTRUCTIONS,
        )
    }

    suspend fun buildLightContext(vault: Vault): AgentMessageContext {
        val coins = vault.coins
        val addresses = buildAddressMap(coins)
        val coinInfos = coins.map { it.toCoinInfo() }
        val balances = buildBalances(coins)

        return AgentMessageContext(
            vaultAddress = vault.pubKeyECDSA,
            vaultName = vault.name,
            addresses = addresses,
            coins = coinInfos,
            balances = balances,
            instructions = AGENT_INSTRUCTIONS,
        )
    }

    private fun buildAddressMap(coins: List<Coin>): Map<String, String> {
        val addresses = mutableMapOf<String, String>()
        for (coin in coins) {
            if (coin.isNativeToken && coin.address.isNotBlank()) {
                addresses.putIfAbsent(coin.chain.raw, coin.address)
            }
        }
        return addresses
    }

    private suspend fun buildBalances(coins: List<Coin>): List<AgentBalanceInfo> =
        coins.mapNotNull { coin ->
            try {
                val balance = balanceRepository.getCachedTokenBalanceAndPrice(coin.address, coin)
                val tokenValue = balance.tokenBalance.tokenValue ?: return@mapNotNull null
                AgentBalanceInfo(
                    chain = coin.chain.raw,
                    asset = coin.ticker,
                    symbol = coin.ticker,
                    amount = tokenValue.decimal.toPlainString(),
                    decimals = coin.decimal,
                )
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun buildAddressBook(): List<AgentAddressBookEntry> =
        try {
            addressBookRepository.getEntries().map { entry ->
                AgentAddressBookEntry(
                    title = entry.title,
                    address = entry.address,
                    chain = entry.chain.raw,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

    private suspend fun buildAllVaults(): List<AgentVaultInfo> =
        try {
            vaultRepository.getAll().map { vault ->
                AgentVaultInfo(name = vault.name, publicKeyEcdsa = vault.pubKeyECDSA)
            }
        } catch (_: Exception) {
            emptyList()
        }

    private fun Coin.toCoinInfo() =
        AgentCoinInfo(
            chain = chain.raw,
            ticker = ticker,
            contractAddress = contractAddress.ifBlank { null },
            isNativeToken = isNativeToken,
            decimals = decimal,
        )

    companion object {
        private const val AGENT_INSTRUCTIONS =
            "Prefer using your knowledge and conversation context over calling tools. " +
                "Only call a tool when you are missing information that you cannot answer from context. " +
                "Use markdown formatting for readability."
    }
}
