package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.DerivationPath
import com.vultisig.wallet.data.repositories.TokenRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import timber.log.Timber
import wallet.core.jni.CoinType
import wallet.core.jni.Derivation
import wallet.core.jni.HDWallet
import java.math.BigInteger
import javax.inject.Inject

data class ChainBalanceResult(
    val chain: Chain,
    val derivationPath: DerivationPath,
    val address: String,
    val hasBalance: Boolean,
)

fun interface ScanChainBalancesUseCase {
    suspend operator fun invoke(mnemonic: String): List<ChainBalanceResult>
}

internal class ScanChainBalancesUseCaseImpl @Inject constructor(
    private val balanceRepository: BalanceRepository,
    private val tokenRepository: TokenRepository,
) : ScanChainBalancesUseCase {

    override suspend fun invoke(mnemonic: String): List<ChainBalanceResult> = coroutineScope {
        val wallet = HDWallet(mnemonic, "")

        val chainsToScan = Chain.keyImportSupportedChains

        val jobs = chainsToScan.flatMap { chain ->
            val defaultJob = async {
                scanChain(wallet, chain, DerivationPath.Default)
            }

            if (chain == Chain.Solana) {
                val phantomJob = async {
                    scanChain(wallet, chain, DerivationPath.Phantom)
                }
                listOf(defaultJob, phantomJob)
            } else {
                listOf(defaultJob)
            }
        }

        jobs.awaitAll()
    }

    private suspend fun scanChain(
        wallet: HDWallet,
        chain: Chain,
        derivationPath: DerivationPath,
    ): ChainBalanceResult {
        val address = when {
            chain == Chain.Solana && derivationPath == DerivationPath.Phantom ->
                wallet.getAddressDerivation(CoinType.SOLANA, Derivation.SOLANASOLANA)
            else ->
                wallet.getAddressForCoin(chain.coinType)
        }

        val hasBalance = try {
            val nativeToken = tokenRepository.getNativeToken(chain.id)
            val tokenValue = balanceRepository.getTokenValue(address, nativeToken).first()
            tokenValue.value > BigInteger.ZERO
        } catch (e: Exception) {
            Timber.d(e, "Failed to check balance for ${chain.id} at $address")
            false
        }

        return ChainBalanceResult(
            chain = chain,
            derivationPath = derivationPath,
            address = address,
            hasBalance = hasBalance,
        )
    }
}
