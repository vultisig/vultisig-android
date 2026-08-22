package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import vultisig.keysign.v1.SignSolana
import vultisig.keysign.v1.WasmExecuteContractPayload

data class DepositTransaction(
    val id: TransactionId,
    val vaultId: String,
    val srcToken: Coin,
    val srcAddress: String,
    val srcTokenValue: TokenValue,
    val memo: String,
    val dstAddress: String,
    val estimatedFees: TokenValue,
    val estimateFeesFiat: String,
    val blockChainSpecific: BlockChainSpecific,
    /**
     * What the chain will actually deduct for this transaction, where that differs from the figure
     * [estimatedFees] quotes. Null wherever the quote is already the charge, which is every flow
     * but Kamino — deliberately not named for a minimum, since it can land either side of the
     * quote.
     *
     * The two differ in both directions, which is why this is a second field rather than a
     * correction to the first. A Kamino fee row carries the 1,000,000-lamport placeholder both
     * platforms pad with against a runtime charge of 5,000, so it overstates; and it leaves out the
     * rent a token deposit spends on the accounts it creates — a term an iPhone co-signer cannot
     * derive from the relayed payload, so neither device quotes it — so it understates by rather
     * more. Affordability is the one question that has to be asked of the charge: the verify screen
     * weighs the wallet against this when it is set, and against [estimatedFees] when it is not
     * (issue #5607).
     */
    val chargedFees: TokenValue? = null,
    val wasmExecuteContractPayload: WasmExecuteContractPayload? = null,
    val operation: String = "",
    val thorAddress: String = "",
    val nodeAddress: String = "",
    val pairedAddress: String = "",
    val pool: String = "",
    /**
     * Display-only destination validator name shown as a "Validator" row on the Verify screen (e.g.
     * a Solana move-stake, where the user picks a validator by name). Null/blank for flows without
     * a named destination validator. Not part of the signing payload.
     */
    val validatorName: String? = null,
    val utxos: List<UtxoInfo> = emptyList(),
    /**
     * Pre-built `signDirect` artefacts when the deposit carries an opaque, app-built SignDoc (e.g.
     * Cosmos-SDK x/staking + x/distribution msgs for Terra LUNA / LUNC). When set, the keysign
     * payload builder forwards these bytes to `CosmosHelper.buildSignDirectSigningInput` and skips
     * the default Cosmos `MsgSend` path. Null for all other deposit flows.
     */
    val signDirect: SignDirectProto? = null,
    /**
     * Structured staking intent (op type + validator addresses) used purely to render the
     * staking-specific Verify summary — "You're staking / unstaking / moving / claiming". The
     * SignDoc bytes in [signDirect] are the signing contract; this field is display-only and not
     * part of the keysign payload.
     */
    val cosmosStakingPayload:
        com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload? =
        null,
    /**
     * Solana native-staking intent (delegate / deactivate / withdraw). When set, the keysign
     * payload builder turns it into the byte-parity `SignSolana.rawTransactions` artefact via
     * [com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingSignDataResolver] and routes
     * signing through the raw-transaction path. Local-only — never relayed to the peer. Null for
     * all non-Solana-staking deposit flows.
     */
    val solanaStakingPayload:
        com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload? =
        null,
    /**
     * Pre-built byte-parity `SignSolana` artefact for a Solana native-staking op — the relayed
     * unsigned transaction bytes both co-signing devices sign. Built once at delegate/deactivate/
     * withdraw time via [BuildSolanaStakingKeysignPayloadUseCase] and forwarded to the keysign
     * payload here (mirrors [signDirect] for Cosmos). Null for all non-Solana-staking deposit
     * flows.
     */
    val signSolana: SignSolana? = null,
)

const val OPERATION_MINT = "Mint"
const val OPERATION_WITHDRAW = "Withdraw"
const val OPERATION_BOND = "Bond"
const val OPERATION_UNBOND = "Unbond"
const val OPERATION_LEAVE = "Leave"
const val OPERATION_LOAN_OPEN = "Loan Open"
const val OPERATION_LOAN_CLOSE = "Loan Close"
const val OPERATION_CIRCLE_WITHDRAW = "DepositUSDCCircle"

/**
 * Kamino Earn. Distinguished from the generic operations so the verify screen can say which
 * direction the transaction goes and label its destination as a vault rather than a validator.
 */
const val OPERATION_KAMINO_DEPOSIT = "KaminoDeposit"
const val OPERATION_KAMINO_WITHDRAW = "KaminoWithdraw"
