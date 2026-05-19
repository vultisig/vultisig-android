package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.KnownEvmContracts
import com.vultisig.wallet.data.repositories.TokenMetadataResolver
import com.vultisig.wallet.data.repositories.UniversalRouterDecoder
import kotlinx.serialization.json.Json

/**
 * Aggregated outputs of [enrichDecodedCall] — the per-screen ViewModel writes each field into the
 * corresponding `TransactionDetailsUiModel` slot.
 */
internal data class DecodedCallExtras(
    val decodedFunctionParams: List<DecodedFunctionParam>?,
    val dstContractLabel: String?,
    val approvalTokenTicker: String?,
)

/**
 * Symbol + decimals pair resolved for an approval target. Internal to the enrichment helper — the
 * UI model only persists [symbol] today, but [decimals] flows into [decodedFunctionParams] so the
 * amount row can be scaled into human-readable units.
 */
private data class ApprovalToken(val symbol: String, val decimals: Int)

/**
 * Computes the extra dApp-display fields that the verify and join-keysign screens need on top of
 * the already-decoded 4byte signature and argument array.
 *
 * Centralises three lookups that previously didn't exist or only existed in fragmented form:
 * - Token symbol + decimals for ERC-20 approvals — first reuses the existing vault-coin lookup so
 *   users see the ticker for tokens they already hold, then falls back to a live on-chain
 *   `symbol()` / `decimals()` call via [TokenMetadataResolver] for tokens not held by any installed
 *   vault. The resolver caches responses for 24h and coalesces concurrent lookups so the verify and
 *   done screens share one RPC round-trip. Decimals carry through into [decodedFunctionParams] so
 *   the amount row scales `1_000_000` into `1` for a 6-decimal token instead of displaying the raw
 *   on-chain integer.
 * - Friendly label for the destination contract — for major DEX routers and Permit2,
 *   [KnownEvmContracts.lookup] returns e.g. `Uniswap V3 Router` so the `To` row no longer reads as
 *   a bare `0x…`.
 * - Per-parameter labelled rows for the expandable details section — replaces the raw JSON blob
 *   with semantic rows (`Spender`, `Recipient`, `Amount`) for known function shapes, falling back
 *   to positional `#N (type)` rows for everything else.
 *
 * The function returns blank fields when [functionInfo] is null (non-EVM or non-contract call),
 * when [dstAddress] is blank, or when the parser fails to interpret the signature.
 */
internal suspend fun enrichDecodedCall(
    chain: Chain,
    dstAddress: String,
    functionInfo: FunctionInfo?,
    allVaults: List<Vault>,
    isUnlimitedApproval: Boolean,
    json: Json,
    tokenMetadataResolver: TokenMetadataResolver,
    nativeTokenLookup: suspend (Chain) -> Coin? = { null },
): DecodedCallExtras {
    if (functionInfo == null) return EMPTY

    val signature = functionInfo.signature

    val approvalToken =
        if (isTokenContractApproval(signature)) {
            resolveApprovalToken(
                chain = chain,
                contractAddress = dstAddress,
                allVaults = allVaults,
                tokenMetadataResolver = tokenMetadataResolver,
            )
        } else null

    val dstContractLabel = KnownEvmContracts.lookup(chain, dstAddress)

    // Universal Router execute(...) goes through a dedicated swap-intent decoder so the labelled
    // rows render real swap context (from/to token + amounts) instead of the opaque positional
    // fallback `decodedFunctionParams` would produce for `(bytes, bytes[], uint256)`. Gated on
    // the destination being on the known-router allowlist so an unrelated `execute` overload on
    // another contract never coincidentally gets framed as a swap.
    val urRows =
        if (
            UniversalRouterDecoder.isUniversalRouterExecuteSignature(signature) &&
                dstContractLabel != null &&
                isUniversalRouterLabel(dstContractLabel)
        ) {
            universalRouterSwapRows(
                chain = chain,
                intent = UniversalRouterDecoder.decode(functionInfo.inputs, json),
                allVaults = allVaults,
                tokenMetadataResolver = tokenMetadataResolver,
                nativeTokenLookup = nativeTokenLookup,
            )
        } else null

    val rows =
        urRows
            ?: decodedFunctionParams(
                signature = signature,
                inputsJson = functionInfo.inputs,
                json = json,
                tokenSymbol = approvalToken?.symbol,
                tokenDecimals = approvalToken?.decimals,
                contractLabel = { address -> KnownEvmContracts.lookup(chain, address) },
                isUnlimitedApproval = isUnlimitedApproval,
            )

    return DecodedCallExtras(
        decodedFunctionParams = rows,
        dstContractLabel = dstContractLabel,
        approvalTokenTicker = approvalToken?.symbol,
    )
}

/**
 * `true` when [label] points at a Universal Router entry in
 * [com.vultisig.wallet.data.repositories.KnownEvmContracts]. Matching on the label rather than the
 * full address keeps the allowlist in one place — adding a new UR deployment to the registry
 * automatically enables decoding without touching this gate.
 */
private fun isUniversalRouterLabel(label: String): Boolean =
    label.startsWith("Uniswap Universal Router", ignoreCase = true)

private suspend fun resolveApprovalToken(
    chain: Chain,
    contractAddress: String,
    allVaults: List<Vault>,
    tokenMetadataResolver: TokenMetadataResolver,
): ApprovalToken? {
    if (contractAddress.isBlank()) return null
    val vaultCoin =
        allVaults
            .asSequence()
            .flatMap { it.coins.asSequence() }
            .firstOrNull { coin ->
                coin.chain == chain &&
                    coin.contractAddress.equals(contractAddress, ignoreCase = true)
            }
    if (vaultCoin != null && vaultCoin.ticker.isNotBlank()) {
        return ApprovalToken(symbol = vaultCoin.ticker, decimals = vaultCoin.decimal)
    }
    val resolved = tokenMetadataResolver.resolve(chain, contractAddress) ?: return null
    return ApprovalToken(symbol = resolved.symbol, decimals = resolved.decimals)
}

private val EMPTY = DecodedCallExtras(null, null, null)
