package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getEcdsaSigningKey
import com.vultisig.wallet.data.models.getEddsaSigningKey
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber
import tss.KeysignResponse

/**
 * Outcome of [BroadcastKeysignUseCase], applied by the ViewModel to its UI-state flows. Keeps the
 * orchestration decoupled from the UI: the use case decides what happened, the ViewModel maps it
 * onto state, persists history, and kicks off polling.
 */
internal sealed interface KeysignBroadcastResult {

    /**
     * The ERC-20 approval transaction was broadcast but never confirmed.
     *
     * @property approveTxHash Hash of the broadcast approval transaction.
     * @property approveTxLink Explorer link for [approveTxHash].
     * @property timedOut True if confirmation timed out (surface a signing error); false if the
     *   approval reverted on-chain (land on the swap overview as failed).
     */
    data class ApprovalNotConfirmed(
        val approveTxHash: String,
        val approveTxLink: String,
        val timedOut: Boolean,
    ) : KeysignBroadcastResult

    /**
     * The transaction (and any preceding approval) was broadcast.
     *
     * @property chain Chain the transaction was broadcast to.
     * @property txHash On-chain hash, or null when the broadcast produced no hash.
     * @property txLink Explorer link for [txHash], empty when [txHash] is null.
     * @property swapProgressLink Swap-progress deep link, or null when not a swap.
     * @property approveTxHash Hash of the preceding approval, empty when not applicable.
     * @property approveTxLink Explorer link for [approveTxHash], empty when not applicable.
     */
    data class Broadcasted(
        val chain: Chain,
        val txHash: String?,
        val txLink: String,
        val swapProgressLink: String?,
        val approveTxHash: String,
        val approveTxLink: String,
    ) : KeysignBroadcastResult
}

/**
 * Orchestrates the broadcast tail of a keysign: submits and confirms an optional ERC-20 approval,
 * assembles and broadcasts the signed transaction, and invalidates the balance caches. The
 * duplicate-broadcast race between co-signers is resolved one layer down in [BroadcastTxUseCase],
 * which verifies the tx is actually on chain before treating a rejection as success.
 *
 * Extracted from `KeysignViewModel` so the broadcast/recover logic can be unit-tested in isolation.
 * The use case has no UI dependencies: it returns a [KeysignBroadcastResult] that the ViewModel
 * maps onto its state flows, then persists history and starts status polling itself.
 */
internal class BroadcastKeysignUseCase
@Inject
constructor(
    private val broadcastTx: BroadcastTxUseCase,
    private val awaitApprovalConfirmation: AwaitApprovalConfirmationUseCase,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val evmApiFactory: EvmApiFactory,
    private val balanceRepository: BalanceRepository,
) {

    /**
     * Runs the broadcast flow for [payload] using the accumulated [signatures].
     *
     * @param vault Vault whose signing keys assemble and (for swaps) approve the transaction.
     * @param payload Keysign payload describing the transaction to broadcast.
     * @param signatures Per-message signatures produced by the signing flow.
     * @param isInitiatingDevice True for the device that initiated the keysign; only joined devices
     *   fall back to the locally computed hash when their ERC-20 approval broadcast is rejected as
     *   a duplicate.
     * @return A [KeysignBroadcastResult] describing the outcome for the ViewModel to apply.
     */
    suspend operator fun invoke(
        vault: Vault,
        payload: KeysignPayload,
        signatures: Map<String, KeysignResponse>,
        isInitiatingDevice: Boolean,
    ): KeysignBroadcastResult {
        var nonceAcc = BigInteger.ZERO

        val chain = payload.coin.chain
        val approvePayload = payload.approvePayload
        var approveTxHash = ""
        if (approvePayload != null) {
            val (approveKey, approveChainCode) = vault.getEcdsaSigningKey(chain)
            val signedApproveTransaction =
                THORChainSwaps(approveKey, approveChainCode, vault.getEddsaSigningKey(chain))
                    .getSignedApproveTransaction(approvePayload, payload, signatures)

            val evmApi = evmApiFactory.createEvmApi(chain)
            approveTxHash =
                try {
                    evmApi.sendTransaction(signedApproveTransaction.rawTransaction)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A joined (losing) co-signer's approve broadcast can be rejected as a
                    // duplicate. Fall back to the locally computed hash and let
                    // awaitApprovalConfirmation verify it on-chain, so we surface a proper
                    // ApprovalNotConfirmed result instead of a generic error screen. This never
                    // fabricates success — confirmation is still gated below. The initiating
                    // device re-throws so a genuine broadcast failure is surfaced.
                    val localHash = signedApproveTransaction.transactionHash
                    if (!isInitiatingDevice && localHash.isNotBlank()) localHash else throw e
                }

            Timber.d("Approval tx broadcast: %s, awaiting confirmation", approveTxHash)

            when (awaitApprovalConfirmation(chain, approveTxHash)) {
                ApprovalConfirmationResult.Confirmed -> {
                    Timber.d("Approval tx confirmed: %s", approveTxHash)
                }
                ApprovalConfirmationResult.TimedOut -> {
                    Timber.w(
                        "Approval tx %s timed out waiting for confirmation on %s",
                        approveTxHash,
                        chain,
                    )
                    return KeysignBroadcastResult.ApprovalNotConfirmed(
                        approveTxHash = approveTxHash,
                        approveTxLink =
                            explorerLinkRepository.getTransactionLink(chain, approveTxHash),
                        timedOut = true,
                    )
                }
                ApprovalConfirmationResult.Failed -> {
                    Timber.w("Approval tx %s reverted on chain %s", approveTxHash, chain)
                    return KeysignBroadcastResult.ApprovalNotConfirmed(
                        approveTxHash = approveTxHash,
                        approveTxLink =
                            explorerLinkRepository.getTransactionLink(chain, approveTxHash),
                        timedOut = false,
                    )
                }
            }

            nonceAcc++
        }

        val signedTx =
            SigningHelper.getSignedTransaction(
                keysignPayload = payload,
                vault = vault,
                signatures = signatures,
                nonceAcc = nonceAcc,
            )

        val txHash = broadcastTx(chain = chain, tx = signedTx)

        Timber.d("transaction hash: %s", txHash)
        var txLink = ""
        var swapProgressLink: String? = null
        if (txHash != null) {
            txLink = explorerLinkRepository.getTransactionLink(chain, txHash)
            swapProgressLink =
                explorerLinkRepository.getSwapProgressLink(txHash, payload.swapPayload)
            runCatching { balanceRepository.invalidateBalance(payload.coin.address, payload.coin) }
                .onFailure { Timber.e(it, "Failed to invalidate balance cache after broadcast") }
            runCatching {
                    balanceRepository.invalidateDeFiBalance(
                        address = payload.coin.address,
                        chain = chain,
                        vaultId = vault.id,
                    )
                }
                .onFailure {
                    Timber.e(it, "Failed to invalidate DeFi balance cache after broadcast")
                }
        }

        return KeysignBroadcastResult.Broadcasted(
            chain = chain,
            txHash = txHash,
            txLink = txLink,
            swapProgressLink = swapProgressLink,
            approveTxHash = approveTxHash,
            approveTxLink =
                if (approveTxHash.isNotEmpty())
                    explorerLinkRepository.getTransactionLink(chain, approveTxHash)
                else "",
        )
    }
}
