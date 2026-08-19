package com.vultisig.wallet.data.blockchain.solana.kamino

import java.math.BigInteger
import javax.inject.Inject
import timber.log.Timber
import wallet.core.jni.SolanaAddress

/**
 * A Kamino deposit or withdraw recovered from the bytes of a relayed transaction.
 *
 * A co-signing device receives the transaction and a handful of display fields beside it, and only
 * the bytes are the thing it will sign. Everything here is read out of them: which vault, which
 * direction, and the amount the kVault instruction itself carries.
 *
 * @property amount the kVault instruction's own `u64` argument — token base units for a deposit,
 *   vault shares for a withdraw. The two are not interchangeable, and only the deposit figure is
 *   comparable with the payload's `toAmount`.
 */
data class KaminoRelayedIntent(
    val vault: KaminoVault,
    val action: KaminoAction,
    val amount: BigInteger,
)

/**
 * Reads a decoded transaction as a Kamino deposit or withdraw, or refuses to call it one.
 *
 * Kept free of JNI so the rules are unit-testable; decoding and address derivation are the caller's
 * job — see [ResolveKaminoRelayedIntentUseCase].
 */
object KaminoRelayedTransactionReader {

    /**
     * Anchor's instruction discriminators for the two kVault entry points, the first eight bytes of
     * `sha256("global:<name>")`. Pinned as bytes rather than derived, because deriving them means
     * hard-coding the names instead, and these are what the fixtures actually carry:
     * `global:deposit` and `global:withdraw_from_available`.
     */
    private val DEPOSIT_DISCRIMINATOR = hex("f223c68952e1f2b6")

    private val WITHDRAW_DISCRIMINATOR = hex("1383709baadc2239")

    /**
     * @param vaultsByShareAccount the caller's derived share-token account for each vault it knows,
     *   keyed by address. The vault's own address is not in a transaction's static account keys —
     *   Kamino loads it from an address lookup table — so this account is what names the vault.
     * @return the intent, or null when these bytes are not a single-instruction Kamino deposit or
     *   withdraw for a vault the caller named.
     */
    fun read(
        decoded: KaminoDecodedTransaction,
        vaultsByShareAccount: Map<String, KaminoVault>,
    ): KaminoRelayedIntent? {
        // Exactly one, not the first of several: two kVault instructions in one transaction is a
        // shape this app never builds, and picking either would describe half of what gets signed.
        val kvault =
            decoded.instructions.singleOrNull { it.programId == KaminoVaultRegistry.PROGRAM_ID }
                ?: return null

        val action =
            when {
                kvault.data.startsWith(DEPOSIT_DISCRIMINATOR) -> KaminoAction.DEPOSIT
                kvault.data.startsWith(WITHDRAW_DISCRIMINATOR) -> KaminoAction.WITHDRAW
                else -> return null
            }

        val vault = kvault.accounts.firstNotNullOfOrNull(vaultsByShareAccount::get) ?: return null
        val amount = kvaultAmountArgument(kvault.data) ?: return null

        return KaminoRelayedIntent(vault = vault, action = action, amount = amount)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/**
 * The `u64` amount argument of a kVault instruction: an 8-byte Anchor discriminator followed by the
 * amount, little-endian. Null when the instruction is too short to carry one.
 */
fun kvaultAmountArgument(data: ByteArray): BigInteger? {
    if (data.size < 16) return null
    var value = BigInteger.ZERO
    for (offset in 7 downTo 0) {
        value = value.shiftLeft(8).or(BigInteger.valueOf((data[8 + offset].toLong() and 0xFF)))
    }
    return value
}

/**
 * The wallet's associated token account for [mint], or null when WalletCore cannot derive one.
 *
 * A free function rather than a lambda in the constructor below: the delegation runs before the
 * instance exists, which is not somewhere a lambda can be built.
 */
private fun deriveAssociatedTokenAccount(owner: String, mint: String): String? =
    try {
        SolanaAddress(owner).defaultTokenAddress(mint)
    } catch (_: Exception) {
        null
    }

/**
 * Recognises a relayed transaction as one of this app's own Kamino transactions.
 *
 * A joining device is handed bytes and a `toAddress`/`toAmount` pair the initiating device filled
 * in. Those fields are display material — they are not what gets signed — so the recognition runs
 * on the bytes and then holds them to the same [KaminoTransactionValidator] rules the initiating
 * device applied before relaying them. Anything that fails is simply not claimed as a Kamino
 * transaction: the co-signer keeps the generic screen it gets today rather than being blocked
 * mid-ceremony over a shape this reader does not know.
 */
class ResolveKaminoRelayedIntentUseCase
internal constructor(
    private val decode: (base64Transaction: String) -> KaminoDecodedTransaction,
    // Derives an associated token account. Backed by WalletCore JNI in production; overridable so
    // the recognition rules can be unit-tested without the native library.
    private val tokenAccount: (owner: String, mint: String) -> String?,
) {

    @Inject constructor() : this(KaminoTransactionDecoder::decode, ::deriveAssociatedTokenAccount)

    /**
     * @param rawTransactions the relayed `signSolana` batch. Kamino relays exactly one transaction;
     *   a batch of any other size is somebody else's payload.
     * @param signerAddress the vault's own Solana address — the same address on both devices, which
     *   is what makes the initiating device's derivations reproducible here.
     */
    operator fun invoke(
        rawTransactions: List<String>,
        signerAddress: String,
    ): KaminoRelayedIntent? {
        val rawTransaction = rawTransactions.singleOrNull() ?: return null
        val decoded = runCatching { decode(rawTransaction) }.getOrNull() ?: return null

        val vaultsByShareAccount =
            KaminoVaultRegistry.ALLOW_LIST.mapNotNull { vault ->
                    tokenAccount(signerAddress, vault.sharesMint)?.let { it to vault }
                }
                .toMap()

        val intent =
            KaminoRelayedTransactionReader.read(decoded, vaultsByShareAccount) ?: return null

        val wrappedSolAccount =
            if (intent.vault.tokenMint == KaminoVaultRegistry.WRAPPED_SOL_MINT) {
                tokenAccount(signerAddress, KaminoVaultRegistry.WRAPPED_SOL_MINT)
            } else {
                null
            }

        return try {
            KaminoTransactionValidator.validate(
                decoded = decoded,
                vault = intent.vault,
                action = intent.action,
                signerAddress = signerAddress,
                wrappedSolAccount = wrappedSolAccount,
            )
            intent
        } catch (e: KaminoTransactionRejected) {
            Timber.w(e, "Relayed transaction looks like Kamino but is not one this app would build")
            null
        }
    }
}
