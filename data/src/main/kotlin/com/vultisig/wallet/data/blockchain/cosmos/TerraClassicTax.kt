package com.vultisig.wallet.data.blockchain.cosmos

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Terra Classic (columbus-5) charges a proportional **burn tax** on every `MsgSend`, paid in the
 * send denom on top of the gas fee. The rate lives in the chain's `x/tax` module (`burn_tax_rate`,
 * currently 0.5%) and is fetched live; this helper holds the conservative fallback and the pure tax
 * math so the signed fee, the validated fee, and the displayed fee stay consistent.
 *
 * Ported from vultisig-ios `TerraClassicTax`.
 */
object TerraClassicTax {

    /**
     * Conservative fallback burn-tax rate used when the live `x/tax` params can't be
     * fetched/decoded. Matches current governance (0.5%). Failing closed (taxing) rather than open
     * (0%) avoids signing a tx the chain then rejects at broadcast.
     */
    val fallbackBurnTaxRate: BigDecimal = BigDecimal("0.005")

    /**
     * Sanity ceiling for a parsed burn-tax rate. The live rate is governance-controlled and has
     * historically been ≤ 1.2%, so any value above this (e.g. a malformed `"5"` meaning 500%) is
     * treated as garbage and replaced by [fallbackBurnTaxRate] — keeping the fail-safe symmetric
     * with the lower bound so a bad endpoint can neither under- nor massively over-charge.
     */
    val maxBurnTaxRate: BigDecimal = BigDecimal("0.1")

    /**
     * Terra Classic `uluna` minimum gas price (uluna per gas unit). Paid by native LUNC, CW20
     * (`terra1…`) and IBC (`ibc/…`) tokens, whose fee the signer denominates in `uluna`. The base
     * gas is `price × gasLimit`, so it scales with a relayed gas limit (see [baseGas]).
     */
    val ULUNA_GAS_PRICE: BigDecimal = BigDecimal("28.325")

    /**
     * Terra Classic `uusd` minimum gas price (uusd per gas unit). Paid only by the USTC bank denom,
     * whose fee the signer denominates in `uusd`.
     */
    val UUSD_GAS_PRICE: BigDecimal = BigDecimal("0.75")

    /**
     * Base gas fee at the static 300k per-chain limit for an `uluna`-denominated send (300k ×
     * 28.325 uluna/gas = 8,497,500 uluna ≈ 8.5 LUNC). It is the value [baseGas] returns for the
     * static limit; `buildCosmosFee` honoring a relayed limit rescales it via [ULUNA_GAS_PRICE].
     */
    const val ULUNA_BASE_GAS: Long = 8_497_500L

    /**
     * Base gas fee at the static 300k per-chain limit for a `uusd`-denominated send (300k × 0.75
     * uusd/gas = 225,000 uusd). Paid only by the USTC bank denom.
     */
    const val UUSD_BASE_GAS: Long = 225_000L

    /**
     * The static gas limit that [ULUNA_BASE_GAS] / [UUSD_BASE_GAS] are priced at (`300k × per-gas
     * price`). Mirrors `TerraHelper`'s static per-chain limit and vultisig-ios
     * `TerraClassicTax.staticGasLimit`.
     */
    const val STATIC_GAS_LIMIT: Long = 300_000L

    /**
     * Burn tax on a send [amount] (in the denom's smallest unit) at [rate], rounded **up** so the
     * signed fee never undershoots the chain's check. Returns 0 for a non-positive amount or rate.
     */
    fun burnTax(amount: BigInteger, rate: BigDecimal): BigInteger {
        if (amount <= BigInteger.ZERO || rate <= BigDecimal.ZERO) return BigInteger.ZERO
        return BigDecimal(amount).multiply(rate).setScale(0, RoundingMode.CEILING).toBigInteger()
    }

    /**
     * Parse a decimal-string `burn_tax_rate` from the LCD into a [BigDecimal], falling back to the
     * conservative default on null/blank input, any parse failure, a negative value, or a value
     * above [maxBurnTaxRate] (a parseable-but-garbage rate like `"5"` would otherwise be applied as
     * a 500% tax).
     */
    fun parseRate(raw: String?): BigDecimal {
        if (raw == null) return fallbackBurnTaxRate
        return runCatching { BigDecimal(raw.trim()) }
            .getOrNull()
            ?.takeIf { it >= BigDecimal.ZERO && it <= maxBurnTaxRate } ?: fallbackBurnTaxRate
    }

    /**
     * Whether a Terra Classic coin is a **bank denom** (e.g. USTC's `uusd`) that pays its gas +
     * burn tax in its OWN denom, as opposed to a CW20 contract token (`terra1…`) or an IBC token
     * (`ibc/…`) that pays the fee in native LUNC (`uluna`). Mirrors the bank-denom branch selection
     * in `TerraHelper.getPreSignedInputData` so the signed fee, the validated fee, and the max-send
     * math all agree on which tokens are taxed in their own denom. The native coin (LUNC) is
     * intentionally excluded — it is handled by its own native-balance branch.
     */
    fun isBankDenom(contractAddress: String, isNativeToken: Boolean): Boolean {
        if (isNativeToken) return false
        val denom = contractAddress.lowercase()
        return !denom.contains("terra1") &&
            !denom.startsWith("ibc/") &&
            !denom.startsWith("factory/")
    }

    /**
     * Base gas number for a Terra Classic send at [gasLimit], in the SAME denom the signer uses for
     * the fee (see `TerraHelper.getPreSignedInputData`). Computed as `gasPrice × gasLimit` and
     * rounded **up** so the signed fee never undershoots the chain's `fee.amount ≥ gasPrice × gas`
     * check when a relayed gas limit is honored in `CosmosHelper.buildCosmosFee`. Bank denoms (USTC
     * / `uusd`) use [UUSD_GAS_PRICE]; everything else — native LUNC, CW20 and IBC — uses
     * [ULUNA_GAS_PRICE]. Gating both this and the signed fee denom on [isBankDenom] keeps the gas
     * number and the fee denom in lockstep.
     *
     * NOTE: among bank denoms only `uusd` is supported — it is the only non-native bank token in
     * the Terra Classic coin list. A different bank denom (e.g. `ukrw`) would be billed the `uusd`
     * gas price in its own denom and be mis-priced; add per-denom gas prices here before listing
     * one.
     */
    fun baseGas(contractAddress: String, isNativeToken: Boolean, gasLimit: Long): BigInteger {
        val price =
            if (isBankDenom(contractAddress, isNativeToken)) UUSD_GAS_PRICE else ULUNA_GAS_PRICE
        return price.multiply(BigDecimal(gasLimit)).setScale(0, RoundingMode.CEILING).toBigInteger()
    }

    /**
     * Whether the burn tax can be folded into the single fee field for this coin: only when the fee
     * is denominated in the same denom as the send (native LUNC → uluna, USTC bank denom → uusd).
     * CW20/IBC pay the fee in uluna while the send is in the token's own denom, so folding a
     * token-unit tax there would mix denoms; they are excluded.
     */
    fun taxPaidInSendDenom(contractAddress: String, isNativeToken: Boolean): Boolean =
        isNativeToken || isBankDenom(contractAddress, isNativeToken)
}
