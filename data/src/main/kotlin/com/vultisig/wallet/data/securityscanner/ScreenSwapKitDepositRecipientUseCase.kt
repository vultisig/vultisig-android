package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.api.swapAggregators.swapKitDepositRecipient
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Screens the recipient decoded from a SwapKit ERC-20 deposit's signed calldata with the security
 * scanner, as a zero-value transfer to that address (the shape of
 * [SecurityScannerContract.createRecipientSecurityScannerTransaction]). Only a confirmed Malicious
 * verdict refuses: a Warning, a chain the scanner does not cover and a failed, timed-out or
 * rate-limited scan come back as the advisory the review shows, so signing never depends on the
 * scanner being up. It runs whether or not the user turned the scanner off.
 */
class ScreenSwapKitDepositRecipientUseCase
@Inject
constructor(private val securityScanner: SecurityScannerContract) {

    /**
     * @return null when [payload] is not a SwapKit ERC-20 deposit, else what the scan said.
     * @throws SwapKitDepositRecipientException when the recipient is confirmed Malicious.
     * @throws IllegalArgumentException when [payload] has the deposit shape but is not exactly the
     *   deposit.
     */
    suspend operator fun invoke(payload: KeysignPayload): SwapKitDepositScreen? {
        val swap = (payload.swapPayload as? SwapPayload.EVM)?.data ?: return null
        val chain = payload.coin.chain
        val recipient = swap.swapKitDepositRecipient(chain) ?: return null
        val result =
            try {
                securityScanner.scanTransaction(
                    SecurityScannerTransaction(
                        chain = chain,
                        type = SecurityTransactionType.COIN_TRANSFER,
                        from = payload.coin.address,
                        to = recipient,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "SwapKit deposit recipient %s not scanned on %s", recipient, chain.raw)
                return SwapKitDepositScreen.NotScanned(e.message ?: "Security Scanner Failed")
            }
        if (result.riskLevel == SecurityRiskLevel.CRITICAL) {
            throw SwapKitDepositRecipientException(
                "SwapKit deposit recipient $recipient is flagged Malicious on ${chain.raw} " +
                    "(${result.warnings.joinToString { it.message }})"
            )
        }
        return SwapKitDepositScreen.Scanned(result)
    }
}

/** What [ScreenSwapKitDepositRecipientUseCase] found for a recipient it did not refuse. */
sealed interface SwapKitDepositScreen {

    /** The scanner answered with a verdict below Malicious. */
    data class Scanned(val result: SecurityScannerResult) : SwapKitDepositScreen

    /** The scanner gave no verdict, so the recipient is not scanned. */
    data class NotScanned(val reason: String) : SwapKitDepositScreen
}

/**
 * A SwapKit deposit recipient the security scanner confirmed Malicious. The message is log text;
 * the app shows its own string.
 */
class SwapKitDepositRecipientException(message: String) : IllegalStateException(message)
