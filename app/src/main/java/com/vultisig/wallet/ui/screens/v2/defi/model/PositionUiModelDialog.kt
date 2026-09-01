package com.vultisig.wallet.ui.screens.v2.defi.model

import com.vultisig.wallet.data.models.ImageModel

internal data class PositionUiModelDialog(
    val logo: ImageModel,
    val ticker: String,
    val isSelected: Boolean = true,
    val positionKey: String = ticker,
    val chainLogo: ImageModel? = null,
    /**
     * Free text the picker's search matches against. A coin position has only its ticker, which is
     * why that is the default; a curated vault adds its curator, so "rockawayx" finds RWA USDC the
     * way it does on iOS (`DefiSelectableAsset.searchTerms`).
     */
    val searchTerms: List<String> = listOf(ticker),
)

/**
 * The positions [query] matches, or all of them when nothing is being searched for.
 *
 * A free function rather than a step inside the picker so the matching rule can be tested without a
 * composition, and so every section of the picker searches the same way.
 */
internal fun List<PositionUiModelDialog>.matching(query: String): List<PositionUiModelDialog> =
    if (query.isEmpty()) this
    else filter { position -> position.searchTerms.any { it.contains(query, ignoreCase = true) } }
