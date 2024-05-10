package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.chains.THORCHainHelper
import com.vultisig.wallet.chains.utxoHelper
import com.vultisig.wallet.data.mappers.CoinToChainAccountMapper
import com.vultisig.wallet.data.mappers.CoinWithFiatValue
import com.vultisig.wallet.data.models.ChainAccount
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import wallet.core.jni.CoinType
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
    private val tokenPriceRepository: TokenPriceRepository,
    private val coinToChainAccountMapper: CoinToChainAccountMapper,
) : ChainAccountsRepository {

    override fun loadChainAccounts(vaultId: String): Flow<List<ChainAccount>> = flow {
        val vault = checkNotNull(vaultDb.select(vaultId)) {
            "No vault with id $vaultId"
        }

        applyDefaultChains(vault)

        vault.coins = vault.coins.asSequence()
            .mapNotNull {
                when (it.coinType) {
                    CoinType.THORCHAIN -> {
                        val thorHelper = THORCHainHelper(vault.pubKeyECDSA, vault.hexChainCode)
                        thorHelper.getCoin()
                    }

                    CoinType.ETHEREUM, CoinType.SOLANA ->
                        null // TODO support these chains

                    else -> {
                        val btcHelper =
                            utxoHelper(it.coinType, vault.pubKeyECDSA, vault.hexChainCode)
                        btcHelper.getCoin()
                    }
                }
            }
            .toMutableList()

        val accounts = vault.coins.mapTo(mutableListOf()) {
            coinToChainAccountMapper.map(CoinWithFiatValue(it, null, null))
        }

        emit(accounts)

        tokenPriceRepository.refresh(vault.coins.map { it.priceProviderID })

        vault.coins.forEachIndexed { index, coin ->
            val appCurrency = appCurrencyRepository
                .currency
                .first()
            val priceRate = tokenPriceRepository
                .getPrice(coin.priceProviderID, appCurrency)
                .first()

            val balance = balanceRepository.getBalance(coin)
                .first()

            val account = coinToChainAccountMapper.map(
                CoinWithFiatValue(
                    coin,
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

    private fun applyDefaultChains(vault: Vault) {
        val btcHelper = utxoHelper(CoinType.BITCOIN, vault.pubKeyECDSA, vault.hexChainCode)
        btcHelper.getCoin()?.let(vault.coins::add)

        val thorHelper = THORCHainHelper(vault.pubKeyECDSA, vault.hexChainCode)
        thorHelper.getCoin()?.let(vault.coins::add)
    }

}