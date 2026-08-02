package com.vultisig.wallet.data.swap.limit

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import java.math.BigInteger

/**
 * Builds THORChain's `m=<` modify-limit-swap memo in its CANCEL form.
 *
 * Wire layout, exactly three fields after the prefix:
 * ```
 * m=<:<SRC_AMOUNT><SRC_ASSET>:<TRADE_TARGET><TGT_ASSET>:0
 * ```
 *
 * Both coins are `<amount><ASSET>` with **no space** — THORNode's `getCoin` scans the leading
 * digits and splices the space back in before parsing.
 *
 * Three properties are load-bearing and each fails silently when got wrong (the cancel is accepted,
 * costs a fee, and matches no order at all):
 * - **Assets are spelled in FULL.** `ModifyLimitSwapMemo` is the one inbound memo type
 *   `processOneTxIn` does not run through `fuzzyAssetMatch`, so the placement memo's 6-character
 *   contract abbreviation is taken literally and keys a bucket nothing was indexed under. See
 *   [com.vultisig.wallet.data.swap.limit.thorchainCancelMemoAsset].
 * - **Amounts are plain decimal integers**, never scientific notation. The placement memo's LIM
 *   goes through `getUintWithScientificNotation`; these coin amounts go through
 *   `cosmos.ParseCoins`, which does not understand `544e6`.
 * - **Both amounts must be exact.** THORNode does not compare amounts directly: it builds an index
 *   key from `(sourceAmount × 1e8) / tradeTarget` and scans that bucket for a swap whose
 *   `FromAddress` matches the sender, taking the first match. One unit of drift in either lands in
 *   a different bucket.
 */
object LimitSwapCancelMemo {

    /**
     * THORChain's modify-limit-swap memo prefix. Distinct from [LimitSwapMemo.PREFIX] (`=<:`),
     * which PLACES an order — `m=<:` modifies one that is already resting.
     */
    const val PREFIX = "m=<:"

    /**
     * The `ModifiedTargetAmount` that means "cancel". THORNode branches on
     * `msg.ModifiedTargetAmount.IsZero()`; any other value re-targets the order instead, which is a
     * different action this app deliberately does not build.
     */
    private const val CANCEL_MODIFIED_TARGET_AMOUNT = "0"

    /**
     * Shortest token-identifier suffix a cancel memo will accept. An EVM contract is 42 characters
     * (`0x` + 40 hex) and the placement abbreviation is 6, so anything in between is an unambiguous
     * gap. Fixed at a length rather than an exact `0x…` pattern so a future asset flavour with a
     * differently shaped full identifier is not rejected out of hand — the job here is to catch
     * truncation, not to validate contract syntax.
     */
    private const val MIN_FULL_TOKEN_IDENTIFIER_LENGTH = 20

    /**
     * THORNode's `ratioLength` — "a value of 18 means that granularity is maxed out at 1 trillion
     * to 1 ratio". Changing it on their side is a kvstore migration, so it is safe to pin.
     */
    private const val RATIO_LENGTH = 18

    private val separators = setOf('.', '/', '~', '-')

    /** Everything the cancel memo needs, reduced to the exact integers THORChain itself holds. */
    data class Inputs(
        /** Source asset in THORChain memo notation, spelled in full (see the class docs). */
        val sourceAsset: String,
        /** The order's deposited source amount in THORChain's 1e8 fixed point. */
        val sourceAmount1e8: BigInteger,
        /** Target asset in THORChain memo notation, under the same full-spelling rule. */
        val targetAsset: String,
        /** The order's ORIGINAL trade target (the LIM the placement memo encoded), 1e8 scale. */
        val tradeTarget: BigInteger,
    )

    /**
     * True when [memo] MODIFIES a resting order — which includes, but is not limited to, cancelling
     * it. A cancel is the special case where the final field is `0`; a non-zero final field
     * re-targets the order.
     */
    fun isModifyMemo(memo: String?): Boolean = memo?.startsWith(PREFIX) == true

    /**
     * True when [memo] CANCELS a resting order rather than merely modifying one.
     *
     * The final field is compared NUMERICALLY, the way THORNode's `getUint` reads it — `"00"` is
     * zero there and a string comparison would call it a retarget. Digits only, so a sign cannot
     * smuggle `"-0"` past an unsigned field.
     */
    fun isCancelMemo(memo: String?): Boolean {
        if (!isModifyMemo(memo)) return false
        val modifiedTarget = requireNotNull(memo).split(":").last()
        if (modifiedTarget.isEmpty() || !modifiedTarget.all { it in '0'..'9' }) return false
        return BigInteger(modifiedTarget).signum() == 0
    }

    /**
     * Whether a THORChain memo asset carries a token identifier that has been truncated —
     * `ETH.USDC-06EB48` rather than `ETH.USDC-0XA0B86991…`.
     *
     * The chain prefix has to be stripped first, and cannot be assumed to end at a `.`: a SECURED
     * asset spells the whole thing with `-`, so a secured native denom is `btc-btc` and a secured
     * token is `eth-usdc-0xa0b…`. Reading the tail after the last `-` would call the first of those
     * truncated and make every secured-native order uncancellable. So: drop everything up to the
     * first separator (`.` layer-1, `/` synth, `~` trade, `-` secured) — that is the chain — and
     * look at the SYMBOL that follows. `BTC.BTC`, `THOR.RUNE` and `btc-btc` carry no identifier at
     * all and are full by construction.
     */
    fun isAbbreviated(asset: String): Boolean {
        val chainEnd = asset.indexOfFirst { it in separators }
        val symbol = if (chainEnd >= 0) asset.substring(chainEnd + 1) else asset
        // A contract carries no `-` of its own, so the first one inside the symbol is the
        // identifier's separator.
        val identifierStart = symbol.indexOf('-')
        if (identifierStart < 0) return false
        return symbol.length - identifierStart - 1 < MIN_FULL_TOKEN_IDENTIFIER_LENGTH
    }

    /**
     * Build the `m=<` memo that cancels a resting limit order.
     *
     * Throws rather than returning a best-effort memo: every failure here would otherwise become a
     * signed transaction that spends a fee and cancels nothing.
     */
    fun build(inputs: Inputs): String {
        require(inputs.sourceAsset.isNotEmpty() && inputs.targetAsset.isNotEmpty()) {
            "limit-swap cancel memo is missing an asset"
        }
        require(inputs.sourceAmount1e8.signum() > 0 && inputs.tradeTarget.signum() > 0) {
            "limit-swap cancel memo amounts must both be positive: the ratio they define is what " +
                "addresses the order on-chain"
        }
        require(!isAbbreviated(inputs.sourceAsset) && !isAbbreviated(inputs.targetAsset)) {
            "limit-swap cancel memo assets must carry their full token identifier; a cancel is not " +
                "fuzzy-matched, so an abbreviation keys a bucket no order was indexed under"
        }
        val source = "${inputs.sourceAmount1e8}${inputs.sourceAsset}"
        val target = "${inputs.tradeTarget}${inputs.targetAsset}"
        return "$PREFIX$source:$target:$CANCEL_MODIFIED_TARGET_AMOUNT"
    }

    /**
     * Whether the cancel memo fits the source chain's per-transaction memo budget.
     *
     * A yes/no gate, not a fitting routine: a cancel memo has no slack to give. The placement memo
     * can be squeezed by rounding its LIM up, because a higher minimum output is still a safe
     * order. A cancel carries two exact `<amount><ASSET>` coins whose values must reproduce
     * THORChain's ratio bucket bit-for-bit, and the assets cannot be shortened either. In practice
     * gas-asset pairs land around 37–44 bytes and fit anywhere; an ERC20 target from a UTXO source
     * reaches 83–91 bytes and cannot fit the 80-byte `OP_RETURN` cap.
     */
    fun memoFits(memo: String, sourceChain: Chain): Boolean {
        val limit =
            if (sourceChain.standard == TokenStandard.UTXO) LimitSwapMemo.UTXO_BYTE_LIMIT
            else LimitSwapMemo.OTHER_BYTE_LIMIT
        return memo.toByteArray(Charsets.UTF_8).size <= limit
    }

    /**
     * Reproduces THORNode's adv-swap-queue index key — the tuple that decides which orders are
     * mutually indistinguishable to a cancel.
     *
     * Mirrors `getAdvSwapQueueIndexKey` + `getRatio` + `rewriteRatio`:
     * ```
     * ratio = (sourceAmount × 1e8) / tradeTarget      // integer division
     * key   = "<source>><target>/<ratio padded or truncated to 18 chars>/"
     * ```
     *
     * The 18-character normalization is not cosmetic: THORNode zero-pads short ratios so keys sort
     * numerically, and TRUNCATES longer ones from the right, deliberately collapsing very large
     * ratios into one bucket. Both behaviours have to be reproduced or the duplicate warning
     * disagrees with the chain at exactly the extremes where it matters.
     *
     * Used only for the duplicate WARNING, never to build a memo — THORNode addresses orders by
     * this key plus the sender and takes the FIRST match, so two orders that reduce to the same key
     * are not independently cancellable and the user has to be told.
     */
    fun bucketKey(inputs: Inputs): String {
        val ratio =
            inputs.sourceAmount1e8
                .multiply(BigInteger.TEN.pow(THORCHAIN_FIXED_POINT_DECIMALS))
                .divide(inputs.tradeTarget)
        val source = layer1MemoAsset(inputs.sourceAsset)
        val target = layer1MemoAsset(inputs.targetAsset)
        return "$source>$target/${rewriteRatio(ratio.toString())}/"
    }

    /**
     * Collapse a memo asset string to its layer-1 form, the way THORNode's `Asset.GetLayer1Asset()`
     * does when it builds the queue index key: the FIRST separator becomes `.`, upper-cased.
     *
     * An asset already in L1 form is left alone — its first separator is the `.` itself, and the
     * `-` of a contract-suffixed asset comes after it. An APPROXIMATION, tuned to over-report
     * rather than under-report: telling a user two orders might be confused when they wouldn't be
     * is a mild annoyance, while missing a real collision means the wrong order closes with no
     * warning.
     */
    private fun layer1MemoAsset(asset: String): String {
        val index = asset.indexOfFirst { it in separators }
        if (index < 0 || asset[index] == '.') return asset.uppercase()
        return (asset.substring(0, index) + "." + asset.substring(index + 1)).uppercase()
    }

    private fun rewriteRatio(ratio: String): String =
        when {
            ratio.length < RATIO_LENGTH -> ratio.padStart(RATIO_LENGTH, '0')
            ratio.length > RATIO_LENGTH -> ratio.take(RATIO_LENGTH)
            else -> ratio
        }
}
