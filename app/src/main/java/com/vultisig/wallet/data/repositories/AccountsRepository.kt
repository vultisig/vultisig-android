package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.PublicKeyHelper
import com.vultisig.wallet.data.mappers.ChainAddressValue
import com.vultisig.wallet.data.mappers.CoinToChainAccountMapper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.models.coinType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

internal interface AccountsRepository {

    fun loadAccounts(
        vaultId: String,
    ): Flow<List<Account>>

    fun loadChainAccounts(
        vaultId: String,
        chain: Chain,
        address: String,
    ): Flow<List<Account>>

}

internal class AccountsRepositoryImpl @Inject constructor(
    private val vaultDb: VaultDB,
    private val balanceRepository: BalanceRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val coinToChainAccountMapper: CoinToChainAccountMapper,
) : AccountsRepository {

    private fun getVault(vaultId: String): Vault =
        checkNotNull(vaultDb.select(vaultId)) {
            "No vault with id $vaultId"
        }

    override fun loadAccounts(vaultId: String): Flow<List<Account>> = flow {
        val vault = getVault(vaultId)

        val coins = vault.coins.filter { it.isNativeToken }

        val accounts = coins.mapTo(mutableListOf()) { coin ->
            val chain = coin.chain
            val coinType = chain.coinType
            val address = chainAccountAddressRepository.getAddress(
                coinType,
                PublicKeyHelper.getPublicKey(vault.pubKeyECDSA, vault.hexChainCode, coinType)
            )
            coinToChainAccountMapper.map(
                ChainAddressValue(coin, chain, address, null)
            )
        }

        emit(accounts)

        tokenPriceRepository.refresh(coins.map { it.priceProviderID })

        fetchAccountBalance(accounts).collect { (index, account) ->
            accounts[index] = account
            emit(accounts)
        }
    }

    override fun loadChainAccounts(
        vaultId: String,
        chain: Chain,
        address: String,
    ): Flow<List<Account>> = flow {
        val vault = getVault(vaultId)

        val coins = vault.coins.filter { it.chain.raw == chain.raw }

        val accounts = coins.mapTo(mutableListOf()) {
            coinToChainAccountMapper.map(
                ChainAddressValue(it, chain, address, null)
            )
        }

        emit(accounts)

        tokenPriceRepository.refresh(coins.map { it.priceProviderID })

        fetchAccountBalance(accounts).collect { (index, account) ->
            accounts[index] = account
            emit(accounts)
        }
    }

    private suspend fun fetchAccountBalance(
        accounts: List<Account>,
    ): Flow<Pair<Int, Account>> = flow {
        accounts.forEachIndexed { index, account ->
            val token = account.token
            val address = account.address

            val balance = balanceRepository
                .getTokenBalance(address, token)
                .first()

            emit(
                index to coinToChainAccountMapper.map(
                    ChainAddressValue(
                        token = token,
                        chain = token.chain,
                        address = address,
                        balance = balance,
                    )
                )
            )
        }
    }
}