package com.vultisig.wallet.data.securityscanner

import com.vultisig.wallet.data.api.swapAggregators.swapKitDepositRecipient
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidRpcClientContract
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Refuses a SwapKit ERC-20 deposit whose decoded recipient lacks a Benign Blockaid verdict. A
 * Warning or Malicious verdict, a chain Blockaid does not index, and a failed scan all refuse, as
 * vultisig-sdk's `assertSwapKitAddressReputation` does. It calls Blockaid directly rather than
 * through [SecurityScannerContract] on purpose: the user's scanner toggle does not turn it off, as
 * it does not in the SDK.
 */
class ScreenSwapKitDepositRecipientUseCase
@Inject
constructor(private val blockaid: BlockaidRpcClientContract) {

    /**
     * @throws SwapKitDepositRecipientException when the deposit recipient in [payload] is refused.
     */
    suspend operator fun invoke(payload: KeysignPayload) {
        val swap = (payload.swapPayload as? SwapPayload.EVM)?.data ?: return
        val chain = payload.coin.chain
        val recipient = swap.swapKitDepositRecipient(chain) ?: return
        val verdict =
            try {
                blockaid.scanEVMAddress(chain = chain, address = recipient)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw SwapKitDepositRecipientException.Unscreened(
                    "SwapKit deposit recipient $recipient could not be screened on ${chain.raw}",
                    e,
                )
            }
        if (verdict.resultType != BENIGN_VERDICT) {
            throw SwapKitDepositRecipientException.Refused(
                "SwapKit deposit recipient $recipient received a ${verdict.resultType} Blockaid " +
                    "verdict on ${chain.raw} (${verdict.features.joinToString()})"
            )
        }
    }

    private companion object {
        const val BENIGN_VERDICT = "Benign"
    }
}

/**
 * Why [ScreenSwapKitDepositRecipientUseCase] refused a SwapKit deposit recipient. The message is
 * log text; the app shows its own string per subtype.
 */
sealed class SwapKitDepositRecipientException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause) {

    /** Blockaid gave the recipient a verdict other than Benign. */
    class Refused(message: String) : SwapKitDepositRecipientException(message)

    /** The recipient could not be screened at all; a later attempt may succeed. */
    class Unscreened(message: String, cause: Throwable) :
        SwapKitDepositRecipientException(message, cause)
}
