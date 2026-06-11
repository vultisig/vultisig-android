package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SignedTransactionResult
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
 * assembles and broadcasts the signed transaction (recovering a joined-device duplicate-broadcast
 * race), and invalidates the balance caches.
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
     *   recover from a duplicate-broadcast rejection.
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
            approveTxHash = evmApi.sendTransaction(signedApproveTransaction.rawTransaction)

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

        val txHash = broadcastOrRecover(chain, signedTx, isInitiatingDevice)

        Timber.d("transaction hash: $txHash")
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

    /**
     * Broadcasts [signedTx] for [chain], falling back to the deterministic locally computed hash
     * when a non-initiator's duplicate broadcast is rejected.
     *
     * In a multi-device vault both peers compute the same signed extrinsic and call this path;
     * whichever device's broadcast reaches the network first wins. The losing device's broadcast is
     * then rejected — Substrate in particular surfaces this as `Transaction has a bad signature`
     * (code 1010) when the initiator's extrinsic has already advanced the nonce. The signed bytes
     * are byte-identical on both devices, so the locally computed
     * [SignedTransactionResult.transactionHash] is the canonical on-chain hash, and we use it
     * instead of failing the joined-device screen.
     *
     * iOS does the same recovery in `KeysignViewModel.handleBroadcastError` / `isAlreadyOnChain`.
     * We keep it scoped to non-initiator devices so a real broadcast failure on the initiator is
     * still surfaced as an error.
     *
     * @param chain Chain the transaction is broadcast to.
     * @param signedTx Locally signed transaction, including the precomputed transaction hash.
     * @param isInitiatingDevice True for the initiating device, which never recovers.
     */
    internal suspend fun broadcastOrRecover(
        chain: Chain,
        signedTx: SignedTransactionResult,
        isInitiatingDevice: Boolean,
    ): String? =
        try {
            broadcastTx(chain = chain, tx = signedTx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recoverJoinedDeviceBroadcast(chain, signedTx, isInitiatingDevice, e) ?: throw e
        }

    /**
     * Returns the locally computed transaction hash if this is a joined-device broadcast failure we
     * should swallow; `null` if the caller must re-throw the original error.
     */
    private fun recoverJoinedDeviceBroadcast(
        chain: Chain,
        signedTx: SignedTransactionResult,
        isInitiatingDevice: Boolean,
        error: Throwable,
    ): String? {
        if (isInitiatingDevice) return null
        return signedTx.transactionHash
            .takeUnless { it.isBlank() }
            ?.also { hash ->
                Timber.w(
                    error,
                    "Joined-device broadcast for %s failed; using locally computed hash %s",
                    chain.raw,
                    hash,
                )
            }
    }
}
