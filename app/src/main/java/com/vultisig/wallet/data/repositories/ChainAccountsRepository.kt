package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.PublicKeyHelper
import com.vultisig.wallet.data.mappers.ChainAddressValue
import com.vultisig.wallet.data.mappers.CoinToChainAccountMapper
import com.vultisig.wallet.data.models.ChainAccount
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.on_board.db.VaultDB
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.math.RoundingMode
import javax.inject.Inject

internal interface ChainAccountsRepository {

    fun loadChainAccounts(
        vaultId: String,
    ): Flow<List<ChainAccount>>

}

internal class ChainAccountsRepositoryImpl @Inject constructor(
    private val vaultDb: VaultDB,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val balanceRepository: BalanceRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val coinToChainAccountMapper: CoinToChainAccountMapper,
) : ChainAccountsRepository {

    override fun loadChainAccounts(vaultId: String): Flow<List<ChainAccount>> = flow {
        val vault = checkNotNull(vaultDb.select(vaultId)) {
            "No vault with id $vaultId"
        }

        val coins = vault.coins.filter { it.isNativeToken }

        val accounts = coins.mapTo(mutableListOf()) {
            val address = chainAccountAddressRepository.getAddress(
                it.coinType,
                PublicKeyHelper.getPublicKey(vault.pubKeyECDSA, vault.hexChainCode, it.coinType)
            )
            coinToChainAccountMapper.map(
                ChainAddressValue(
                    it.chain, address,
                    null, null
                )
            )
        }

        emit(accounts)

        tokenPriceRepository.refresh(coins.map { it.priceProviderID })

        val appCurrency = appCurrencyRepository
            .currency
            .first()

        coins.forEachIndexed { index, coin ->
            val priceRate = tokenPriceRepository
                .getPrice(coin.priceProviderID, appCurrency)
                .first()

            val balance = balanceRepository.getBalance(coin)
                .first()

            val account = coinToChainAccountMapper.map(
                ChainAddressValue(
                    chain = coin.chain,
                    address = accounts[index].address,
                    tokenValue = balance,
                    fiatValue = FiatValue(
                        value = balance.balance
                            .multiply(priceRate)
                            .setScale(2, RoundingMode.HALF_UP),
                        currency = appCurrency.ticker,
                    )
                )
            )

            accounts[index] = account
            emit(accounts)
        }
    }

}