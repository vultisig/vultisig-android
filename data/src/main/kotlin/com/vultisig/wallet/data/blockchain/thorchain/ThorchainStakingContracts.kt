package com.vultisig.wallet.data.blockchain.thorchain

/**
 * The Rujira wasm contracts this app transacts with, and the one place their addresses are spelled.
 *
 * These began as constants beside the single builder that spent each one.
 * [THORChainTransactionDecoder] made them shared: it reads a signed execute message back, and
 * Rujira's verbs — `bond`, `withdraw`, `deposit`, `claim` — are ordinary CosmWasm keys any contract
 * on THORChain may spell. What separates a staking operation from an unrelated contract that
 * happens to use the same word is the address it is addressed to, so the reader has to know that
 * set exactly.
 *
 * That makes a duplicate address a correctness bug rather than untidiness: a builder pointed at one
 * spelling and a reader holding another would name the same signed transaction two different
 * things. The UI and pricing layers alias these rather than restating them.
 */
object ThorchainStakingContracts {
    /**
     * Rujira's RUJI staking contract: `account.bond`/`account.withdraw` and the `liquid` sibling.
     */
    const val STAKING_RUJI = "thor13g83nn5ef4qzqeafp0508dnvkvm0zqr3sj7eefcn5umu65gqluusrml5cr"

    /** The auto-compounding TCY position, funded with `x/staking-tcy`. */
    const val STAKING_TCY_COMPOUND =
        "thor1z7ejlk5wk2pxh9nfwjzkkdnrq4p2f5rjcpudltv0gh282dwfz6nq9g2cr0"

    /** The bRUNE liquid bond, which mints the ybRUNE receipt and whose NAV prices it. */
    const val BRUNE_LIQUID_BOND = "thor179fex2rxd45caedmz4hxsnu42sw20lu0djyh4yukyh965sq8muuqptru2g"

    /** The yRUNE vault token: minted through [YVAULT_AFFILIATE], redeemed against directly. */
    const val YRUNE = "thor1mlphkryw5g54yfkrp6xpqzlpv4f8wh6hyw27yyg4z2els8a9gxpqhfhekt"

    /** The yTCY vault token, on the same two routes as [YRUNE]. */
    const val YTCY = "thor1h0hr0rm3dawkedh44hlrmgvya6plsryehcr46yda2vj0wfwgq5xqrs86px"

    /** Fronts a yVault mint, forwarding the base64 instruction on to the token contract. */
    const val YVAULT_AFFILIATE = "thor1v3f7h384r8hw6r3dtcgfq6d5fq842u6cjzeuu8nr0cp93j7zfxyquyrfl8"

    /**
     * Where `account.*` and `liquid.*` mean staking. [THORChainTransactionDecoder] reads those
     * namespaces only here: elsewhere they are just JSON keys a contract chose.
     */
    val STAKING_CONTRACTS = setOf(STAKING_RUJI, STAKING_TCY_COMPOUND, BRUNE_LIQUID_BOND)

    /**
     * The vault tokens themselves. A bare `withdraw` carrying a slippage is a redemption only at
     * one of these, and they are the only targets a mint envelope may forward to.
     */
    val VAULT_TOKEN_CONTRACTS = setOf(YRUNE, YTCY)

    /**
     * What may front a yVault envelope. The envelope forwards, so this says who carries the call
     * and [VAULT_TOKEN_CONTRACTS] says where it may land — both have to hold.
     */
    val VAULT_MINT_ROUTERS = setOf(YVAULT_AFFILIATE)
}
