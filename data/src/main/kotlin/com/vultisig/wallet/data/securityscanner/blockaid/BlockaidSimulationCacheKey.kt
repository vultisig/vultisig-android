package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import io.ktor.util.decodeBase64Bytes
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale
import wallet.core.jni.Base58

/**
 * Type-safe cache identity for a Blockaid simulation request.
 *
 * Verify → sign → done all see the same payload but go through different view models. The cache key
 * has to depend only on inputs that are stable across those screens — chain + signer + memo for
 * EVM, transaction digest scoped to the signer for Solana.
 */
sealed interface BlockaidSimulationCacheKey {

    /**
     * EVM key spans (chain, signer, recipient, memo, value).
     *
     * The signer (`from` address) is part of the key because the cache lives for the lifetime of
     * the process and the device may sign for multiple vaults; without it, two vaults whose dApp
     * call has identical calldata against the same router would alias to the same cache entry.
     *
     * The recipient (`to` address) is required because two dApp calls with identical calldata but
     * different targets must NOT share a cached verdict — Blockaid simulates the call against a
     * specific contract, and a low-entropy 4-byte selector against different contracts would
     * otherwise alias to the same cache entry.
     *
     * The native `value` (msg.value) is included because `payable` calls can return a different
     * verdict for the same calldata when the attached value differs (e.g. swap routers that route
     * differently when ETH is supplied vs. not).
     */
    data class Evm(
        val chain: Chain,
        val normalizedFrom: String,
        val normalizedTo: String,
        val normalizedMemo: String,
        val value: BigInteger,
    ) : BlockaidSimulationCacheKey

    data class Solana(val signer: String, val transactionsDigest: String) :
        BlockaidSimulationCacheKey

    companion object {
        /**
         * Builds a cache key from a [KeysignPayload], or returns null if the payload is not
         * eligible for simulation (non-EVM/non-Solana, missing memo for EVM, missing
         * rawTransactions for Solana).
         *
         * Returning null is the contract that signals "skip simulation" to callers, mirroring the
         * iOS service. The lookup MUST NOT be made for payloads that cannot be cached, otherwise
         * the failure path would always retry on every screen transition.
         */
        fun from(payload: KeysignPayload): BlockaidSimulationCacheKey? {
            return when (payload.coin.chain.standard) {
                TokenStandard.EVM -> evmKey(payload)
                TokenStandard.SOL -> solanaKey(payload)
                else -> null
            }
        }

        private fun evmKey(payload: KeysignPayload): BlockaidSimulationCacheKey? {
            // [Locale.ROOT] avoids the Turkish-i case-folding pitfall where ASCII `I` lowercases
            // to dotless `ı` on tr/az devices, producing a different key than the same input on
            // any other device.
            val memo = payload.memo?.lowercase(Locale.ROOT) ?: return null
            // 0x is the sentinel for an empty calldata; only memo-bearing calls
            // are simulation candidates so we drop it here. Comparison is on the
            // already-lowercased value.
            if (!memo.startsWith("0x") || memo.length <= 2) return null
            // Lowercasing addresses too: EIP-55 checksums differ in casing
            // between otherwise-identical addresses, so equality must be
            // case-insensitive at the cache layer.
            val from = payload.coin.address.lowercase(Locale.ROOT)
            val to = payload.toAddress.lowercase(Locale.ROOT)
            return Evm(
                chain = payload.coin.chain,
                normalizedFrom = from,
                normalizedTo = to,
                normalizedMemo = memo,
                value = payload.toAmount,
            )
        }

        private fun solanaKey(payload: KeysignPayload): BlockaidSimulationCacheKey? {
            val rawTxs = payload.solanaRawTransactionsBase64() ?: return null
            // Hash the raw transactions to a fixed-size digest. A plain [joinToString] would let
            // two different transaction lists alias to the same key if the separator could appear
            // inside an entry, AND it would hold an unbounded string in the process-lifetime
            // cache. The null byte `\u0000` cannot appear in base64 payloads so it makes the
            // separator unambiguous; SHA-256 then bounds the key to 64 hex chars regardless of
            // payload size. Mask each byte to its unsigned form before hex-formatting so values
            // 0x80..0xFF do not sign-extend into a multi-character integer (which would break the
            // 64-char invariant for ~50% of bytes).
            val md = MessageDigest.getInstance("SHA-256")
            md.update(rawTxs.joinToString(separator = "\u0000").toByteArray())
            return Solana(
                signer = payload.coin.address,
                transactionsDigest = md.digest().toHexString(),
            )
        }

        private fun ByteArray.toHexString(): String =
            joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }
    }
}

/**
 * The base64 Solana transactions this payload actually signs, or null when it signs none.
 *
 * A dApp/raw-Solana payload carries them on `signSolana.rawTransactions`; an aggregator swap
 * (Jupiter, LI.FI, SwapKit) carries its single transaction on the swap quote's `tx.data` with
 * `signSolana` unset. Read from the signed bytes only — never rebuilt from the swap's display
 * fields — so a Blockaid scan describes exactly what gets signed.
 */
internal fun KeysignPayload.solanaRawTransactionsBase64(): List<String>? {
    // The swap tx goes first: the signer hashes only `quote.tx.data` for a Solana EVM-shaped swap,
    // so a `signSolana` riding alongside it is never signed and must not supply the verdict.
    val swap = swapPayload as? SwapPayload.EVM
    if (coin.chain == Chain.Solana && swap != null) {
        return swap.data.quote.tx.data.takeIf { it.isNotBlank() }?.let(::listOf)
    }
    return signSolana?.rawTransactions?.takeIf { it.isNotEmpty() }
}

/**
 * Transcodes one base64 Solana transaction to the base58 form Blockaid's Solana endpoints take.
 * Throws when [base64] isn't valid base64.
 */
internal fun solanaBase64ToBase58(base64: String): String =
    // encodeNoCheck rather than encode: these are raw transactions, not addresses, so the 4-byte
    // SHA-256 checksum that Base58Check appends would corrupt the payload Blockaid receives.
    Base58.encodeNoCheck(base64.decodeBase64Bytes())
