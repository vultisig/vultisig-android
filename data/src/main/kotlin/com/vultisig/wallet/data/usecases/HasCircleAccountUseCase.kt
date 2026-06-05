package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Resolves whether a vault already has a Circle (USDC yield) MSCA account.
 *
 * New Circle deposits are disabled, so this is used to hide the Circle entry for vaults that never
 * opened an account. The account address is persisted locally (see [ScaCircleAccountRepository])
 * when the account is created or first discovered on the Circle positions screen, so this read is
 * cache-only and never hits the network.
 */
interface HasCircleAccountUseCase : suspend (String) -> Boolean

internal class HasCircleAccountUseCaseImpl
@Inject
constructor(private val scaCircleAccountRepository: ScaCircleAccountRepository) :
    HasCircleAccountUseCase {

    override suspend fun invoke(vaultId: String): Boolean =
        try {
            scaCircleAccountRepository.getAccount(vaultId) != null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Timber.e(t, "HasCircleAccountUseCase: failed to read Circle account for %s", vaultId)
            false
        }
}
