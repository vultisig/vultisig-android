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
import kotlin.coroutines.cancellation.CancellationException
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
        val chainsToScan = Chain.keyImportSupportedChains

        // Scan all chains in parallel. Solana needs two checks because Phantom
        // uses a different derivation path than the standard Solana one.
        val jobs = chainsToScan.flatMap { chain ->
            val defaultJob = async {
                scanChain(mnemonic, chain, DerivationPath.Default)
            }

            if (chain == Chain.Solana) {
                val phantomJob = async {
                    scanChain(mnemonic, chain, DerivationPath.Phantom)
                }
                listOf(defaultJob, phantomJob)
            } else {
                listOf(defaultJob)
            }
        }

        jobs.awaitAll()
    }

    private suspend fun scanChain(
        mnemonic: String,
        chain: Chain,
        derivationPath: DerivationPath,
    ): ChainBalanceResult {
        // Create a fresh HDWallet per task — HDWallet (JNI) is not thread-safe
        val wallet = HDWallet(mnemonic, "")
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Failed to check balance for ${chain.id}")
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
