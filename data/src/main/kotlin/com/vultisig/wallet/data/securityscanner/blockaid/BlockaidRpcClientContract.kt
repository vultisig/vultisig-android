package com.vultisig.wallet.data.securityscanner.blockaid

import com.vultisig.wallet.data.models.Chain

/**
 * Blockaid RPC contract.
 *
 * Existing `scan*` methods request the validation endpoint (risk classification only). The
 * `simulate*` methods, added for the dApp hero, request balance change diffs in addition to
 * validation, so the same call powers both the security badge and the resolved hero.
 */
interface BlockaidRpcClientContract {
    suspend fun scanBitcoinTransaction(
        address: String,
        serializedTransaction: String,
    ): BlockaidTransactionScanResponseJson

    suspend fun scanEVMTransaction(
        chain: Chain,
        from: String,
        to: String,
        amount: String,
        data: String,
    ): BlockaidTransactionScanResponseJson

    suspend fun scanSolanaTransaction(
        address: String,
        serializedMessage: String,
    ): BlockaidTransactionScanResponseJson

    suspend fun scanSuiTransaction(
        address: String,
        serializedTransaction: String,
    ): BlockaidTransactionScanResponseJson

    /**
     * Simulates an EVM transaction and returns balance-change diffs alongside the standard
     * validation. Used by the dApp signing hero to display authoritative `transfer` / `swap` shapes
     * instead of front-runnable 4byte decodes.
     *
     * @param chain Blockaid-supported EVM chain (Ethereum, Polygon, BSC, …)
     * @param from From address (the signer's wallet address on this chain)
     * @param to The contract being called or transfer recipient
     * @param amount Native value, hex string with 0x prefix and even length
     * @param data Calldata, hex string with 0x prefix
     */
    suspend fun simulateEvmTransaction(
        chain: Chain,
        from: String,
        to: String,
        amount: String,
        data: String,
    ): BlockaidEvmSimulationResponseJson

    /**
     * Simulates one or more Solana transactions and returns balance-change diffs alongside the
     * standard validation. The Solana simulation endpoint expects base58-encoded transactions;
     * callers MUST pre-encode (the payload carries them as base64).
     */
    suspend fun simulateSolanaTransaction(
        address: String,
        rawTransactionsBase58: List<String>,
    ): BlockaidSolanaSimulationResponseJson
}
