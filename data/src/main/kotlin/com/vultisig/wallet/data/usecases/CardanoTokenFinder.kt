package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.cardanoAssetId
import com.vultisig.wallet.data.models.parseCardanoAssetId
import com.vultisig.wallet.data.utils.NetworkException
import java.math.BigInteger
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Auto-discovers Cardano native tokens (CNTs) held at an address.
 *
 * Companion to [EvmCoinFinder], [CosmosBankCoinFinder] and [RippleTokenFinder]: the chain-detail
 * screen and the background token-refresh worker both route through
 * `TokenRepository.getTokensWithBalance`, which delegates here for [Chain.Cardano]. Every
 * `address_assets` row becomes a [Coin] keyed on its `<policy_id>.<asset_name_hex>` id, preferring
 * the curated [Coins] catalog entry when one matches so the shipped ten keep their icon, casing and
 * `priceProviderID`.
 *
 * Discovery is deliberately *not* gated to the catalog, unlike [RippleTokenFinder]. An XRPL trust
 * line is opened by the holder, so an arbitrary one is not evidence of anything; a Cardano asset is
 * minted by its policy and simply lands in the wallet, and the whole point of this finder is the
 * long tail the curated ten miss. What the catalog gate bought on XRPL is bought here instead by
 * contract-qualifying `Coin.id` for Cardano tokens, so an asset whose name decodes to `USDM` cannot
 * take the real Mehen USDM's identity.
 *
 * Mirrors iOS `CardanoNativeTokensService.discoverTokens` and the SDK's `findCardanoCoins`, with
 * the pagination the SDK has and iOS lacks — every NFT is its own native asset, so an NFT-heavy
 * wallet runs past Koios' 1000-row page easily.
 *
 * Network failures are logged and yield an empty list, matching the sibling finders: a transient
 * blip must not wipe tokens the vault already holds, and the next refresh retries.
 */
interface CardanoTokenFinder {
    suspend fun find(address: String): List<Coin>
}

internal class CardanoTokenFinderImpl @Inject constructor(private val cardanoApi: CardanoApi) :
    CardanoTokenFinder {

    override suspend fun find(address: String): List<Coin> {
        val assets =
            try {
                cardanoApi.getAddressAssets(address)
            } catch (e: SocketTimeoutException) {
                Timber.e(e, "Koios address_assets timed out")
                return emptyList()
            } catch (e: NetworkException) {
                Timber.e(e, "Koios address_assets failed: status=%d", e.httpStatusCode)
                return emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Koios address_assets failed")
                return emptyList()
            }

        return assets
            .mapNotNull { row ->
                // An id that is not well-formed hex of the right length would push malformed bytes
                // into a signing input's TokenAmount, so it is rejected here rather than at
                // broadcast — the same contract CardanoApi applies to a UTxO's asset rows.
                val assetId =
                    parseCardanoAssetId(
                        cardanoAssetId(row.policyId ?: return@mapNotNull null, row.assetName ?: "")
                    ) ?: return@mapNotNull null
                val quantity = row.quantity?.toBigIntegerOrNull() ?: return@mapNotNull null
                DiscoveredAsset(
                    assetId = cardanoAssetId(assetId.policyId, assetId.assetNameHex),
                    policyId = assetId.policyId,
                    assetNameHex = assetId.assetNameHex,
                    quantity = quantity,
                    decimals = row.decimals,
                )
            }
            // Koios reports one row per asset per UTxO group, so a wallet holding the same asset in
            // several UTxOs sees it more than once. Fold them together before the positive-quantity
            // gate, or an asset split across a positive and a zero row could be judged on the wrong
            // one and the surviving row would still collide on Coin.id downstream.
            .groupBy { it.assetId }
            .values
            .mapNotNull { rows ->
                val total = rows.fold(BigInteger.ZERO) { sum, row -> sum + row.quantity }
                if (total <= BigInteger.ZERO) return@mapNotNull null
                rows.first().toCoin()
            }
    }

    private fun DiscoveredAsset.toCoin(): Coin =
        // The catalog entry is what keeps the shipped ten on their bundled icon, their
        // hand-checked casing (`iUSD`, not `IUSD`) and a working `priceProviderID` — none of which
        // the chain itself can supply. Both sides are the lowercase `<policy_id>.<asset_name_hex>`
        // form, so they match without normalization.
        Coins.findCuratedByContract(Chain.Cardano, assetId)
            ?: Coin(
                chain = Chain.Cardano,
                ticker = deriveTicker(assetNameHex, policyId),
                // No bundled drawable and no registry logo on this endpoint. An empty logo falls
                // back to the chain icon in the UI, the same as any other discovered token; the
                // ticker must NOT be reused as the logo key, or a lookalike asset named "MIN"
                // would borrow Minswap's icon.
                logo = "",
                address = "",
                // Koios sources `decimals` from the Cardano token registry and reports 0 for an
                // asset the registry does not list, so this endpoint cannot distinguish "the
                // registry says zero" from "unknown" — verified live against an unregistered
                // asset, which returns `"decimals": 0` with a null `token_registry_metadata`.
                // Refusing every 0 would drop NFTs, which genuinely have none and which the ticket
                // wants surfaced. So take the registry's word like iOS and the SDK do; if it later
                // publishes a decimal, TokenRefreshWorker.needsIdentityCorrection rewrites the
                // persisted row on the next refresh because the contract address still matches.
                decimal = decimals ?: 0,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = assetId,
                isNativeToken = false,
            )

    private data class DiscoveredAsset(
        val assetId: String,
        val policyId: String,
        val assetNameHex: String,
        val quantity: BigInteger,
        val decimals: Int?,
    )

    private companion object {
        /** Enough of a policy hash to tell two unnamed assets apart in a list. */
        const val POLICY_TICKER_LENGTH = 8

        /**
         * A displayable ticker for an asset the curated catalog does not know.
         *
         * A Cardano asset name is raw bytes, not text: CIP-67 label prefixes (`0014df10` for a
         * fungible token) are unprintable, and nothing stops a minter putting arbitrary bytes
         * there. Keeping only printable ASCII turns USDM's `0014df105553444d` into `USDM` — iOS
         * masks each byte with `0x7F` first, which invents a `_` out of the `0xdf` checksum byte
         * and is why it renders `_USDM`. An asset with no printable name at all (an unnamed asset,
         * or one named entirely out of range) falls back to a policy-id prefix, matching iOS and
         * the SDK, so it is still distinguishable in a list.
         */
        fun deriveTicker(assetNameHex: String, policyId: String): String {
            val name =
                assetNameHex
                    .chunked(2)
                    .mapNotNull { byte ->
                        byte.toIntOrNull(radix = 16)?.takeIf { it in 0x20..0x7E }
                    }
                    .map { it.toChar() }
                    .joinToString(separator = "")
                    .trim()
            return name.ifEmpty { policyId.take(POLICY_TICKER_LENGTH).uppercase() }
        }
    }
}
