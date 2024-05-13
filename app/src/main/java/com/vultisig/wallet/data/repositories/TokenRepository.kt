package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal interface TokenRepository {

    fun getEnabledTokens(vaultId: String): Flow<List<Coin>>

    fun getEnabledChains(vaultId: String): Flow<Set<Chain>>

    fun getChainTokens(vaultId: String, chainId: String): Flow<List<Coin>>

    suspend fun getNativeToken(chainId: String): Flow<Coin>

    val allTokens: Flow<List<Coin>>

    val nativeTokens: Flow<List<Coin>>

}

internal class TokenRepositoryImpl @Inject constructor(
    private val vaultDB: VaultDB,
) : TokenRepository {

    override fun getEnabledTokens(vaultId: String): Flow<List<Coin>> = flow {
        emit(requireNotNull(vaultDB.select(vaultId)?.coins))
    }

    override fun getEnabledChains(vaultId: String): Flow<Set<Chain>> =
        getEnabledTokens(vaultId)
            .map { enabledTokens ->
                enabledTokens.asSequence()
                    .filter { it.isNativeToken }
                    .map { it.chain }
                    .toSet()
            }

    override fun getChainTokens(vaultId: String, chainId: String): Flow<List<Coin>> = allTokens
        .map { allTokens ->
            allTokens.filter { it.chain.id == chainId }
        }

    override suspend fun getNativeToken(chainId: String): Flow<Coin> =
        nativeTokens.map { it.first { it.chain.id == chainId } }

    override val allTokens: Flow<List<Coin>> = flowOf(Coins.SupportedCoins)

    override val nativeTokens: Flow<List<Coin>> = allTokens
        .map { it.filterNatives() }

    private fun Iterable<Coin>.filterNatives() =
        filter { it.isNativeToken }

}