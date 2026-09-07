package com.vultisig.wallet.data.blockchain.thorchain

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.transaction_decoding.CorroboratedContent
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAmount
import com.vultisig.wallet.data.models.transaction_decoding.DecodedAsset
import com.vultisig.wallet.data.models.transaction_decoding.DecodedCounterparty
import com.vultisig.wallet.data.models.transaction_decoding.DecodedEvidence
import com.vultisig.wallet.data.models.transaction_decoding.DecodedOperation
import com.vultisig.wallet.data.models.transaction_decoding.DecodedTransaction
import com.vultisig.wallet.data.models.transaction_decoding.MemoPrecedence
import com.vultisig.wallet.data.models.transaction_decoding.SignedAmount
import com.vultisig.wallet.data.models.transaction_decoding.SignedTransactionContent
import com.vultisig.wallet.data.models.transaction_decoding.TransactionContentDecoder
import java.util.Base64
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import vultisig.keysign.v1.TransactionType
import vultisig.keysign.v1.WasmExecuteContractPayload

/**
 * Reads THORChain memos and Rujira wasm messages, but only where signed content proves the native
 * or inbound route. A user-typed lookalike memo on an unrelated chain stays unknown.
 *
 * Mirrors the iOS `THORChainTransactionDecoder`. It declares no chain scope: an inbound deposit
 * leaves Bitcoin or Ethereum, so nothing about the chain a transaction is on says THORChain, and
 * the reader has to establish provenance from the transaction itself.
 */
class THORChainTransactionDecoder
@Inject
constructor(private val inboundVaults: InboundVaultCorroborating) : TransactionContentDecoder {

    /** Inbound routes may originate on any chain, so provenance gates decoding. */
    override val handles: Set<Chain>? = null

    override fun decode(tx: SignedTransactionContent): DecodedTransaction? {
        // Flat fields beside an opaque signed artifact are untrusted sidecars.
        val content = tx.corroborated ?: return null

        return when (provenance(tx, content)) {
            Provenance.Unrelated -> null

            Provenance.Native -> {
                content.wasmPayload?.let { wasm ->
                    decodeWasm(wasm)?.let {
                        return it
                    }
                }
                content.memo(MEMO_PRECEDENCE)?.let { memo ->
                    decodeMemo(memo, content)?.let {
                        return it
                    }
                }
                decodeWireType(content)
            }

            // Foreign-chain wasm payloads cannot be THORChain contract calls.
            Provenance.Inbound -> content.memo(MEMO_PRECEDENCE)?.let { decodeMemo(it, content) }
        }
    }

    private enum class Provenance {
        /** The transaction is on THORChain itself. */
        Native,

        /** Signed content names a THORChain vault as the destination. */
        Inbound,

        /** Nothing corroborates THORChain, so its grammar does not apply. */
        Unrelated,
    }

    private fun provenance(tx: SignedTransactionContent, content: CorroboratedContent): Provenance {
        if (tx.chain in NATIVE_CHAINS) return Provenance.Native

        return when {
            content.swap is SwapPayload.ThorChain -> Provenance.Inbound

            // An active approve or non-THOR swap outranks flat destination data.
            content.swap != null || content.approve != null -> Provenance.Unrelated

            // A recorded inbound vault can corroborate signed LP and secured-asset destinations.
            // A cold snapshot refuses rather than blocking a signing screen on a fetch.
            inboundVaults.corroborates(
                destination = content.toAddress,
                chain = tx.chain,
                isNative = tx.isNativeCoin,
            ) -> Provenance.Inbound

            else -> Provenance.Unrelated
        }
    }

    // MARK: - Contract calls

    private fun decodeWasm(wasm: WasmExecuteContractPayload): DecodedTransaction? {
        val counterparty = DecodedCounterparty.Contract(wasm.contractAddress)
        val operation =
            operation(wasm.executeMsg)
                ?: return DecodedTransaction(
                    operation = DecodedOperation.ContractCall,
                    amount = DecodedAmount.Unstated,
                    counterparty = counterparty,
                    evidence = DecodedEvidence.WasmExecuteMsg,
                )

        return DecodedTransaction(
            operation = operation,
            amount = amount(operation, wasm),
            counterparty = counterparty,
            evidence = DecodedEvidence.WasmExecuteMsg,
        )
    }

    /**
     * What the operation moves, in the units the signed content states them.
     *
     * The attached funds are the only quantity a wasm execute proves. There is deliberately no
     * fallback to the transaction's memo: a memo beside a wasm payload is already withheld by
     * [SignedTransactionContent.memoIsOutranked], and a memo beside a contract call is decoration
     * the contract itself never reads. Where an operation states its own amount inside the execute
     * message instead — Rujira's `account.withdraw` — the amount names no denom, so nothing here
     * could say what asset it counts.
     */
    private fun amount(
        operation: DecodedOperation,
        wasm: WasmExecuteContractPayload,
    ): DecodedAmount {
        // Mint output is execution-set; attached funds are the input, not the output.
        if (operation == DecodedOperation.Mint) return DecodedAmount.Unstated

        // Several attached denoms are ambiguous; never present only the first.
        val funds = wasm.coins.filterNotNull().singleOrNull() ?: return DecodedAmount.Unstated
        val units = funds.amount.toBigIntegerOrNull() ?: return DecodedAmount.Unstated
        if (units.signum() <= 0) return DecodedAmount.Unstated

        return DecodedAmount.Units(units, DecodedAsset.Denom(funds.denom))
    }

    /**
     * Parses direct Rujira JSON and base64-wrapped yVault JSON. Ambiguous action sets are refused
     * rather than resolved by whichever key happened to come first.
     */
    private fun operation(executeMsg: String): DecodedOperation? {
        val root =
            runCatching { Json.parseToJsonElement(executeMsg).jsonObject }.getOrNull()
                ?: return null

        vaultEnvelopeOperation(root)?.let {
            return it
        }
        yVaultRedeemOperation(root)?.let {
            return it
        }
        return rujiraOperation(root, depth = 0)
    }

    /**
     * The yVault envelope: an outer `execute` naming the target contract, with the real instruction
     * base64-encoded inside it.
     */
    private fun vaultEnvelopeOperation(root: JsonObject): DecodedOperation? {
        val encoded =
            (root[KEY_EXECUTE] as? JsonObject)?.get(KEY_MSG)?.asStringOrNull() ?: return null
        val decoded =
            runCatching { String(Base64.getDecoder().decode(encoded)) }.getOrNull() ?: return null
        val inner =
            runCatching { Json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return null

        return inner.keys.mapNotNull(VAULT_ACTIONS::get).singleOrNull()
    }

    /**
     * ⚠️ **An Android departure, and the one shape iOS cannot produce.** This app sends a yVault
     * redemption straight to the token contract as a bare `{"withdraw":{"slippage":"…"}}`, where
     * iOS wraps the same instruction in the `execute` envelope above. Read by [RUJIRA_ACTIONS]
     * alone, that bare `withdraw` would name the redemption an unstake — the wrong verb over the
     * right figure. The slippage is what tells the two apart: a Rujira `account.withdraw` states an
     * amount and never a slippage, and it is namespaced rather than bare.
     */
    private fun yVaultRedeemOperation(root: JsonObject): DecodedOperation? {
        val withdraw = (root[KEY_WITHDRAW] as? JsonObject) ?: return null
        return DecodedOperation.Redeem.takeIf { withdraw.containsKey(KEY_SLIPPAGE) }
    }

    /** Searches one namespace level; deeper keys are the action's own parameters. */
    private fun rujiraOperation(node: JsonObject, depth: Int): DecodedOperation? {
        val actions = node.keys.mapNotNull(RUJIRA_ACTIONS::get)
        if (actions.size == 1) return actions.single()
        if (actions.size > 1) return null

        if (depth > 0) return null

        // Sorting keeps a malformed multi-namespace message deterministic.
        val nested = node.keys.sorted().mapNotNull { node[it] as? JsonObject }
        return nested.mapNotNull { rujiraOperation(it, depth + 1) }.singleOrNull()
    }

    // MARK: - Memo grammar

    /**
     * Memo heads are case-folded the way THORNode folds them. Verbs THORChain and Rujira spell the
     * same are told apart by shape: a Rujira memo names a `thor1…` contract and a raw amount where
     * a node memo names a node address.
     */
    private fun decodeMemo(memo: String, content: CorroboratedContent): DecodedTransaction? {
        val fields = memo.split(":")
        val head = fields.firstOrNull() ?: return null

        // The `thor1…` contract plus a numeric third field is what separates a Rujira account
        // operation from the THORChain node memo sharing its verb.
        val isRujiraForm =
            fields.size > RUJIRA_AMOUNT_FIELD &&
                fields[RUJIRA_CONTRACT_FIELD].startsWith(THOR_ADDRESS_PREFIX) &&
                fields[RUJIRA_AMOUNT_FIELD].toBigIntegerOrNull() != null

        return when (head.lowercase()) {
            "m=<" ->
                if (fields.size > 2)
                    DecodedTransaction(
                        operation = DecodedOperation.LimitOrderCancel,
                        amount = DecodedAmount.Unstated,
                        evidence = DecodedEvidence.Memo,
                    )
                else null

            "=<" ->
                if (fields.size > 2)
                    DecodedTransaction(
                        operation = DecodedOperation.LimitOrderPlacement,
                        amount = carried(content.amount),
                        evidence = DecodedEvidence.Memo,
                    )
                else null

            "bond" ->
                if (isRujiraForm) rujira(DecodedOperation.Stake, fields)
                else node(DecodedOperation.Bond, fields, carried(content.amount))

            "withdraw" -> if (isRujiraForm) rujira(DecodedOperation.Unstake, fields) else null

            "claim" -> if (isRujiraForm) rujira(DecodedOperation.ClaimRewards, fields) else null

            // `UNBOND:<node>:<units>` carries positive base units of the transaction's own coin.
            "unbond" -> {
                val units = fields.getOrNull(2)?.toBigIntegerOrNull()
                if (units != null && units.signum() > 0)
                    node(
                        DecodedOperation.Unbond,
                        fields,
                        DecodedAmount.Units(units, DecodedAsset.TransactionCoin),
                    )
                else null
            }

            "rebond" ->
                if (fields.size > 2) node(DecodedOperation.Rebond, fields, DecodedAmount.Unstated)
                else null

            "leave" -> node(DecodedOperation.Leave, fields, DecodedAmount.Unstated)

            "tcy+" ->
                DecodedTransaction(
                    operation = DecodedOperation.Stake,
                    amount = carried(content.amount),
                    evidence = DecodedEvidence.Memo,
                )

            // `TCY-:<bps>` commits to a SHARE of whatever is staked when THORChain executes it, so
            // what the transaction carries — a literal zero — is not the amount.
            "tcy-" ->
                fraction(fields.getOrNull(1))?.let {
                    DecodedTransaction(
                        operation = DecodedOperation.Unstake,
                        amount = it,
                        evidence = DecodedEvidence.Memo,
                    )
                }

            "secure+" -> named(DecodedOperation.SecuredAssetDeposit, fields, content)

            "secure-" -> named(DecodedOperation.SecuredAssetWithdraw, fields, content)

            "merge" -> named(DecodedOperation.Merge, fields, content)

            // `unmerge:<token>:<shares>` states its own share count, in the shares' own units.
            "unmerge" -> {
                val token = fields.getOrNull(1)?.takeIf { it.isNotEmpty() }
                val shares = fields.getOrNull(2)?.toBigIntegerOrNull()
                if (token != null && shares != null && shares.signum() > 0)
                    DecodedTransaction(
                        operation = DecodedOperation.Unmerge,
                        amount = DecodedAmount.Units(shares, DecodedAsset.Denom(token)),
                        evidence = DecodedEvidence.Memo,
                    )
                else null
            }

            "+" ->
                fields
                    .getOrNull(1)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { pool ->
                        DecodedTransaction(
                            operation = DecodedOperation.AddLiquidity,
                            amount = carried(content.amount),
                            counterparty = DecodedCounterparty.Pool(pool),
                            evidence = DecodedEvidence.Memo,
                        )
                    }

            // `-:<pool>:<bps>`; a single-sided withdrawal appends its asset after the basis points.
            "-" -> {
                val pool = fields.getOrNull(1)?.takeIf { it.isNotEmpty() }
                val fraction = fraction(fields.getOrNull(2))
                if (pool != null && fraction != null)
                    DecodedTransaction(
                        operation = DecodedOperation.RemoveLiquidity,
                        amount = fraction,
                        counterparty = DecodedCounterparty.Pool(pool),
                        evidence = DecodedEvidence.Memo,
                    )
                else null
            }

            else -> null
        }
    }

    /** A node memo: the verb, the node it names, and whatever figure the transaction carries. */
    private fun node(
        operation: DecodedOperation,
        fields: List<String>,
        amount: DecodedAmount,
    ): DecodedTransaction? {
        val node = fields.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return DecodedTransaction(
            operation = operation,
            amount = amount,
            counterparty = DecodedCounterparty.Node(node),
            evidence = DecodedEvidence.Memo,
        )
    }

    /**
     * A memo whose second field names the asset or address the operation is about — required for
     * the memo to be that operation at all, but not a counterparty this renders.
     */
    private fun named(
        operation: DecodedOperation,
        fields: List<String>,
        content: CorroboratedContent,
    ): DecodedTransaction? {
        if (fields.getOrNull(1).isNullOrEmpty()) return null
        return DecodedTransaction(
            operation = operation,
            amount = carried(content.amount),
            evidence = DecodedEvidence.Memo,
        )
    }

    /** A Rujira account operation: `<verb>:<thor1 contract>:<raw amount>`. */
    private fun rujira(operation: DecodedOperation, fields: List<String>): DecodedTransaction? {
        val contract = fields[RUJIRA_CONTRACT_FIELD]
        val raw = fields[RUJIRA_AMOUNT_FIELD].toBigIntegerOrNull() ?: return null
        if (raw.signum() <= 0) return null

        return DecodedTransaction(
            operation = operation,
            amount = DecodedAmount.Units(raw, DecodedAsset.Denom(contract)),
            counterparty = DecodedCounterparty.Contract(contract),
            evidence = DecodedEvidence.Memo,
        )
    }

    /** Refuses out-of-range basis points rather than clamping signed intent. */
    private fun fraction(field: String?): DecodedAmount? {
        val bps = field?.toIntOrNull() ?: return null
        if (bps !in 1..MAX_BASIS_POINTS) return null
        return DecodedAmount.Fraction(bps, DecodedAsset.TransactionCoin)
    }

    // MARK: - Wire type

    private fun decodeWireType(content: CorroboratedContent): DecodedTransaction? =
        when (content.transactionType) {
            TransactionType.TRANSACTION_TYPE_THOR_MERGE ->
                DecodedTransaction(
                    operation = DecodedOperation.Merge,
                    amount = carried(content.amount),
                    evidence = DecodedEvidence.WireTransactionType,
                )

            TransactionType.TRANSACTION_TYPE_THOR_UNMERGE ->
                DecodedTransaction(
                    operation = DecodedOperation.Unmerge,
                    amount = DecodedAmount.Unstated,
                    evidence = DecodedEvidence.WireTransactionType,
                )

            else -> null
        }

    private companion object {
        /**
         * Only the one THORChain network this app builds for. iOS carries chainnet and stagenet
         * variants beside it; there is no [Chain] entry for either here.
         */
        val NATIVE_CHAINS = setOf(Chain.ThorChain)

        /**
         * An inbound memo travels with the earlier THORChain route rather than being an unsigned
         * sidecar beside it: a swap out of Bitcoin carries its memo into the transaction the source
         * chain signs.
         */
        val MEMO_PRECEDENCE = MemoPrecedence.MemoTravelsWithTheEarlierRoute

        const val THOR_ADDRESS_PREFIX = "thor1"
        const val RUJIRA_CONTRACT_FIELD = 1
        const val RUJIRA_AMOUNT_FIELD = 2
        const val MAX_BASIS_POINTS = 10_000

        const val KEY_EXECUTE = "execute"
        const val KEY_MSG = "msg"
        const val KEY_WITHDRAW = "withdraw"
        const val KEY_SLIPPAGE = "slippage"

        /** Actions the yVault envelope carries, in the vault's own vocabulary. */
        val VAULT_ACTIONS =
            mapOf("deposit" to DecodedOperation.Mint, "withdraw" to DecodedOperation.Redeem)

        /** Actions a Rujira execute message names, under `account` or `liquid`. */
        val RUJIRA_ACTIONS =
            mapOf(
                "unbond" to DecodedOperation.Unstake,
                "withdraw" to DecodedOperation.Unstake,
                "bond" to DecodedOperation.Stake,
                "deposit" to DecodedOperation.Stake,
                "claim" to DecodedOperation.ClaimRewards,
                "withdraw_rewards" to DecodedOperation.ClaimRewards,
            )

        /** Max sends expose no committed amount because signing computes it later. */
        fun carried(signed: SignedAmount): DecodedAmount =
            when (signed) {
                is SignedAmount.Committed ->
                    if (signed.value.signum() > 0)
                        DecodedAmount.Units(signed.value, DecodedAsset.TransactionCoin)
                    else DecodedAmount.Unstated

                SignedAmount.ComputedAtSigning -> DecodedAmount.Unstated
            }
    }
}

/** The element's string content, or null when it is absent or not a JSON string. */
private fun JsonElement.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
