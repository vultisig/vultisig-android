package com.vultisig.wallet.data.repositories

import android.content.Context
import com.vultisig.wallet.chains.thorchainHelper
import com.vultisig.wallet.chains.utxoHelper
import com.vultisig.wallet.data.mappers.CoinToChainAccountMapper
import com.vultisig.wallet.data.models.ChainAccount
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.service.BalanceService
import com.vultisig.wallet.service.CryptoPriceService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import wallet.core.jni.CoinType
import javax.inject.Inject

internal interface ChainAccountsRepository {

    fun loadChainAccounts(
        vaultId: String,
    ): Flow<List<ChainAccount>>

}

internal class ChainAccountsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val priceService: CryptoPriceService,
    private val balanceService: BalanceService,
    private val vaultDb: VaultDB,
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
                        val thorHelper = thorchainHelper(vault.pubKeyECDSA, vault.hexChainCode)
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

        val chainListMap: Map<String, List<Coin>> = vault.coins.groupBy { it.chain.raw }

        val accounts: MutableList<ChainAccount> = vault.coins.distinct().mapTo(mutableListOf()) { coinToChainAccountMapper.map(it).also { chainAccount: ChainAccount ->
            chainAccount.coins.addAll(chainListMap[chainAccount.chainName]?.toList()?: emptyList())
        } }

        emit(accounts)

        priceService.updatePriceProviderIDs(vault.coins.map { it.priceProviderID })

        vault.coins.forEachIndexed { index, coin ->
            val currency = priceService.getSettingCurrency()
            val priceRate = priceService.getPrice(coin.priceProviderID)
            val coinRawBalance = balanceService.getBalance(coin)
                .rawBalance
                .toBigInteger()

            val account = coinToChainAccountMapper.map(
                coin.copy(
                    rawBalance = coinRawBalance,
                    priceRate = priceRate,
                    currency = currency
                )
            )

            accounts[index] = account
            emit(accounts)
        }
    }

    private fun applyDefaultChains(vault: Vault) {
        val btcHelper = utxoHelper(CoinType.BITCOIN, vault.pubKeyECDSA, vault.hexChainCode)
        btcHelper.getCoin()?.let(vault.coins::add)

        val thorHelper = thorchainHelper(vault.pubKeyECDSA, vault.hexChainCode)
        thorHelper.getCoin()?.let(vault.coins::add)
    }

}