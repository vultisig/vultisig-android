package com.vultisig.wallet.data.blockchain.solana.kamino

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins

/**
 * Relative risk of a launch vault, used to order and label the Earn list.
 *
 * Kamino exposes no risk or curator field anywhere in its API, so this is curation and travels with
 * the allow-list rather than with a response.
 */
enum class KaminoRiskTier {
    /** Over-collateralised lending against liquid crypto collateral. */
    CONSERVATIVE,
    /**
     * Lending against tokenized private credit — reinsurance, receivables and corporate bonds. A
     * materially different risk from [CONSERVATIVE], and must never be presented as
     * government-backed.
     */
    PRIVATE_CREDIT,
}

/**
 * Who runs a launch vault's strategy.
 *
 * An enum rather than a free string because the card pairs each one with its mark: a new curator
 * has to be given a logo, and a `when` over this fails to compile until it is.
 */
enum class KaminoCurator(val displayName: String) {
    STEAKHOUSE_FINANCIAL("Steakhouse Financial"),
    ROCKAWAYX("RockawayX"),
    ALLEZ_LABS("Allez Labs"),
}

/**
 * A curated Kamino Earn vault the app offers.
 *
 * Every field here is an immutable property of the vault — its mints, their scales and its farm are
 * fixed when the vault is created. They are pinned locally because a transaction built by Kamino
 * cannot be validated against values fetched from Kamino: the check would be circular, and a single
 * compromised response could supply a matching pair. Everything that legitimately moves — name,
 * minimums, APY, share price, lookup tables — stays live.
 */
data class KaminoVault(
    val address: String,
    /** The vault's underlying token mint: a deposit's source and a withdrawal's destination. */
    val tokenMint: String,
    /**
     * Pinned because it scales every amount; a wrong scale mis-sizes a transfer by a power of ten.
     */
    val tokenDecimals: Int,
    val sharesMint: String,
    /** Not necessarily equal to [tokenDecimals] — see [ALLEZ_SOL]. */
    val sharesDecimals: Int,
    /** The farm a deposit stakes its shares into. */
    val farm: String,
    /** Shown until live vault state arrives, and as the fallback if it never does. */
    val fallbackName: String,
    val curator: KaminoCurator,
    val riskTier: KaminoRiskTier,
)

/** The curated set of Kamino Earn vaults the app exposes on the Solana DeFi tab. */
object KaminoVaultRegistry {

    /** The kVaults program every deposit and withdraw invokes. */
    const val PROGRAM_ID = "KvauGMspG5k6rtzrqqn7WNn3oZdyKqLKwK2XWQ8FLjd"

    /**
     * Kamino's farms program. Every launch vault has a farm, so a deposit ends by staking into it
     * and the shares never land in the user's wallet as a token balance.
     */
    const val FARMS_PROGRAM_ID = "FarmsPZpWu9i7Kky8tPN37rs2TpmMrAZrC7S7vJa91Hr"

    /** Circle's USDC — the underlying token of both dollar vaults. */
    const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    /**
     * Wrapped SOL. The SOL vault's underlying token is this mint rather than native SOL, which is
     * why its deposits have to wrap first.
     */
    const val WRAPPED_SOL_MINT = "So11111111111111111111111111111111111111112"

    val STEAKHOUSE_USDC =
        KaminoVault(
            address = "HDsayqAsDWy3QvANGqh2yNraqcD8Fnjgh73Mhb3WRS5E",
            tokenMint = USDC_MINT,
            tokenDecimals = 6,
            sharesMint = "7D8C5pDFxug58L9zkwK7bCiDg4kD4AygzbcZUmf5usHS",
            sharesDecimals = 6,
            farm = "9FVjHqduhDPMVqvu3cXiEBjU6nvxvGdCCLRwd9WpVRZj",
            fallbackName = "Steakhouse USDC",
            curator = KaminoCurator.STEAKHOUSE_FINANCIAL,
            riskTier = KaminoRiskTier.CONSERVATIVE,
        )

    val RWA_USDC =
        KaminoVault(
            address = "DWSXb18xZApz29vnQpgR2m6MynCT7PznaXt7Ut7M7KaP",
            tokenMint = USDC_MINT,
            tokenDecimals = 6,
            sharesMint = "DgHN3q3dSYAchNX7V3D4aYiTWMx8RHTgHbfPiwiqBkE9",
            sharesDecimals = 6,
            farm = "ArwyAHmnFmbKbUxC2fnK5VUEpspHrnoFtJ22bvEyriKk",
            fallbackName = "RWA USDC",
            curator = KaminoCurator.ROCKAWAYX,
            riskTier = KaminoRiskTier.PRIVATE_CREDIT,
        )

    /**
     * The share scale differs from the token scale here — 6 against 9. Nothing may assume they
     * match.
     */
    val ALLEZ_SOL =
        KaminoVault(
            address = "A1so1bPD3W1TfeFwboDh8yfAAVaVtcdAYBYCjhg2mJQ",
            tokenMint = WRAPPED_SOL_MINT,
            tokenDecimals = 9,
            sharesMint = "FiM4VQdXXnTXL7GgChryf9zHNG9cmvKECwf34L2y3CkN",
            sharesDecimals = 6,
            farm = "H6kauPaHmNqpdKtD5U2zw3Eb28ZB7iMeBdHVfLq1i4Kh",
            fallbackName = "Allez SOL",
            curator = KaminoCurator.ALLEZ_LABS,
            riskTier = KaminoRiskTier.CONSERVATIVE,
        )

    /**
     * Kamino runs well over 160 vaults and `GET /kvaults/vaults` returns every one of them in a
     * single ~340 KB payload. The allow-list both curates and keeps the fetch small: these three
     * are hydrated with three small per-vault requests instead.
     *
     * Ordered conservative-first so the riskiest tier is never the first thing offered.
     */
    val ALLOW_LIST: List<KaminoVault> = listOf(STEAKHOUSE_USDC, ALLEZ_SOL, RWA_USDC)

    fun vaultFor(address: String): KaminoVault? = ALLOW_LIST.firstOrNull { it.address == address }

    /** Whether the app is willing to transact against [address] at all. */
    fun isAllowed(address: String): Boolean = vaultFor(address) != null

    /**
     * The share mints of every curated vault, which must not be counted as wallet token balances.
     */
    val SHARES_MINTS: Set<String> = ALLOW_LIST.map { it.sharesMint }.toSet()
}

/**
 * The wallet coin a vault's underlying token corresponds to — the logo, ticker and price feed its
 * position is displayed and valued with.
 *
 * The SOL vault's underlying is wrapped SOL, which maps to native SOL here: the two are
 * interchangeable one-for-one, and native SOL is what the user recognises and what the app already
 * has a price feed for.
 */
val KaminoVault.coin: Coin?
    get() =
        when (tokenMint) {
            KaminoVaultRegistry.USDC_MINT -> Coins.Solana.USDC
            KaminoVaultRegistry.WRAPPED_SOL_MINT -> Coins.Solana.SOL
            else -> null
        }
