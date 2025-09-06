package com.vultisig.wallet.data.usecases.chaintokens

import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.swapAggregators.OneInchApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.SplTokenRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.OneInchToCoinsUseCase
import com.vultisig.wallet.data.usecases.cosmos.CosmosToCoinsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject


interface GetChainTokensUseCase:(Chain, Vault) ->  Flow<List<Coin>>

internal class GetChainTokensUseCaseImpl @Inject constructor(
    private val tokenRepository: TokenRepository,
    private val splTokenRepository: SplTokenRepository,
    private val oneInchApi: OneInchApi,
    private val oneInchToCoins: OneInchToCoinsUseCase,
    private val cosmosApiFactory: CosmosApiFactory,
    private val cosmosToCoins: CosmosToCoinsUseCase,
) : GetChainTokensUseCase {


    override fun invoke(
        chain: Chain,
        vault: Vault,
    ): Flow<List<Coin>> = flow {

        val builtInTokens = tokenRepository.builtInTokens.first().filter { it.chain == chain }
        emitUniqueTokens(builtInTokens)

        val refreshedTokens = tokenRepository.getRefreshTokens(chain, vault)
        emitUniqueTokens(
            builtInTokens,
            refreshedTokens
        )

        when (chain.standard) {
            TokenStandard.EVM -> {
                emitEvmTokens(chain, refreshedTokens, builtInTokens)
            }
            TokenStandard.SOL -> {
                emitSolTokens(vault, chain, refreshedTokens, builtInTokens)
            }

            else -> Unit
        }
    }

    private suspend fun FlowCollector<List<Coin>>.emitEvmTokens(
        chain: Chain,
        refreshedTokens: List<Coin>,
        builtInTokens: List<Coin>,
    ) {
        runCatching { oneInchApi.getTokens(chain) }
            .onSuccess { oneInchTokens ->
                emitUniqueTokens(
                    refreshedTokens,
                    builtInTokens,
                    oneInchToCoins(
                        oneInchTokens.tokens,
                        chain
                    ),
                )
            }
            .onFailure {
                emitUniqueTokens(
                    refreshedTokens,
                    builtInTokens,
                )
            }
    }

    private suspend fun FlowCollector<List<Coin>>.emitSolTokens(
        vault: Vault,
        chain: Chain,
        refreshedTokens: List<Coin>,
        builtInTokens: List<Coin>,
    ) {
        val address = vault.coins.firstOrNull { it.chain == chain }?.address ?: run {
            emitUniqueTokens(
                refreshedTokens,
                builtInTokens,
            )
            return
        }
        val tokens = runCatching { splTokenRepository.getTokens(address) }
            .getOrElse { emptyList() }
        emitUniqueTokens(
            refreshedTokens,
            builtInTokens,
            tokens,
        )

        val jupiterTokens = runCatching { splTokenRepository.getJupiterTokens() }
            .getOrElse { emptyList() }
        emitUniqueTokens(
            refreshedTokens,
            builtInTokens,
            tokens,
            jupiterTokens
        )
    }

    private suspend fun FlowCollector<List<Coin>>.emitUniqueTokens(vararg items: List<Coin>) {
        val coins = items.toList()
            .flatten()
            .asSequence()
            .distinctBy { it.contractAddress to it.chain.id }
            .toList()
            .modifyIfNeeded()
        emit(coins)
    }

    private fun List<Coin>.modifyIfNeeded() = this.map {
        val isLinkToken = it.ticker == LINK_TICKER
        when {
            isLinkToken -> LinkCoinStrategy.modify(it)
            else -> it
        }
    }


}
