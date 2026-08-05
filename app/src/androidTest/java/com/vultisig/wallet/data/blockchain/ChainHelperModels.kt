import com.vultisig.wallet.data.models.payload.SignDirect
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import vultisig.keysign.v1.SignTon

@Serializable
data class TransactionData(
    val name: String,
    @SerialName("keysign_payload") val keysignPayload: KeysignPayload,
    @SerialName("expected_image_hash") val expectedImageHash: List<String>,
)

@Serializable
data class KeysignPayload(
    val coin: Coin,
    @SerialName("to_address") val toAddress: String,
    @SerialName("to_amount") val toAmount: String,
    @SerialName("BlockchainSpecific") val blockchainSpecific: BlockchainSpecific,
    @SerialName("sign_data") val signData: SignData? = null,
    @SerialName("utxo_info") val utxoInfo: List<UtxoInfo>? = null,
    @SerialName("vault_public_key_ecdsa") val vaultPublicKeyEcdsa: String,
    @SerialName("lib_type") val libType: String,
    @SerialName("memo") val memo: String? = null,
    @SerialName("wasm_execute_contract_payload")
    val wasmExecuteContractPayload: WasmExecuteContractPayload? = null,
    @SerialName("SwapPayload") val swapPayload: SwapPayload? = null,
    @SerialName("erc20_approve_payload") var approvePayload: ERC20ApprovePayload? = null,
    @SerialName("trigger_smart_contract_payload")
    var triggerSmartContractPayload: TriggerSmartContractPayload? = null,
)

/** Snake-case aliases mirror `TronTriggerSmartContractPayload` in commondata and the iOS corpus. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TriggerSmartContractPayload(
    @JsonNames("owner_address") val ownerAddress: String,
    @JsonNames("contract_address") val contractAddress: String,
    @JsonNames("call_value") val callValue: String? = null,
    @JsonNames("call_token_value") val callDataValue: String? = null,
    val data: String,
)

@Serializable
data class ERC20ApprovePayload(
    @SerialName("spender") val spender: String,
    @SerialName("amount") val amount: String,
)

@Serializable
data class SwapPayload(
    @SerialName("ThorchainSwapPayload") val thorchainSwapPayload: ThorchainSwapPayload? = null,
    @SerialName("MayachainSwapPayload") val mayachainSwapPayload: ThorchainSwapPayload? = null,
    @SerialName("OneinchSwapPayload") val oneinchSwapPayload: OneinchSwapPayload? = null,
)

@Serializable
data class ThorchainSwapPayload(
    @SerialName("from_address") val fromAddress: String,
    @SerialName("from_coin") val fromCoin: Coin,
    @SerialName("to_coin") val toCoin: Coin,
    @SerialName("vault_address") val vaultAddress: String,
    @SerialName("from_amount") val fromAmount: String,
    @SerialName("to_amount_decimal") val toAmountDecimal: String,
    @SerialName("to_amount_limit") val toAmountLimit: String,
    @SerialName("streaming_interval") val streamingInterval: String,
    @SerialName("streaming_quantity") val streamingQuantity: String,
    @SerialName("is_affiliate") val isAffiliate: Boolean,
    @SerialName("fee") val fee: String,
    @SerialName("expiration_time") val expirationTime: Int,
    @SerialName("router_address") val routerAddress: String = "",
)

@Serializable
data class WasmExecuteContractPayload(
    @SerialName("sender_address") val senderAddress: String,
    @SerialName("contract_address") val contractAddress: String,
    @SerialName("execute_msg") val executeMsg: String,
    @SerialName("coins") val coins: List<CosmosCoin>,
) {
    @Serializable
    data class CosmosCoin(
        @SerialName("denom") val denom: String,
        @SerialName("amount") val amount: String,
    )
}

@Serializable
data class Coin(
    val chain: String,
    val ticker: String,
    val address: String,
    val decimals: Int,
    @SerialName("price_provider_id") val priceProviderId: String,
    @SerialName("is_native_token") val isNativeToken: Boolean,
    @SerialName("contract_address") val contractAddress: String? = null,
    @SerialName("hex_public_key") val hexPublicKey: String,
    val logo: String,
)

@Serializable
data class BlockchainSpecific(
    @SerialName("EthereumSpecific") val ethereumSpecific: EthereumSpecific? = null,
    @SerialName("CosmosSpecific") val cosmosSpecific: CosmosSpecific? = null,
    @SerialName("RippleSpecific") val rippleSpecific: RippleSpecific? = null,
    @SerialName("TonSpecific") val tonSpecific: TonSpecific? = null,
    @SerialName("SolanaSpecific") val solanaSpecific: SolanaSpecific? = null,
    @SerialName("ThorchainSpecific") val thorchainSpecific: ThorchainSpecific? = null,
    @SerialName("UtxoSpecific") val utxoSpecific: UtxoSpecific? = null,
    @SerialName("PolkadotSpecific") val polkadotSpecific: PolkadotSpecific? = null,
    @SerialName("SuicheSpecific") val suiSpecific: SuiSpecific? = null,
    @SerialName("MayaSpecific") val mayachainSpecific: MayachainSpecific? = null,
    @SerialName("TronSpecific") val tronSpecific: TronSpecific? = null,
    @SerialName("CardanoSpecific") val cardanoSpecific: CardanoSpecific? = null,
)

@Serializable
data class EthereumSpecific(
    @SerialName("max_fee_per_gas_wei") val maxFeePerGasWei: String,
    @SerialName("priority_fee") val priorityFee: String,
    val nonce: Int,
    @SerialName("gas_limit") val gasLimit: String,
)

@Serializable
data class CosmosSpecific(
    @SerialName("account_number") val accountNumber: Long,
    val gas: Long,
    val sequence: Int,
    @SerialName("transaction_type") val transactionType: Int,
    @SerialName("ibc_denom_trace") val ibcDenomTrace: IbcDenomTrace? = null,
)

@Serializable
data class RippleSpecific(
    val sequence: Long,
    val gas: Long,
    @SerialName("last_ledger_sequence") val lastLedgerSequence: Long,
)

@Serializable
data class TonSpecific(
    @SerialName("jettons_address") val jettonsAddress: String = "",
    @SerialName("is_active") val activeDestination: Boolean = false,
    @SerialName("send_max_amount") val sendMaxAmount: Boolean,
    @SerialName("sequence_number") val sequenceNumber: Long,
    @SerialName("expire_at") val expireAt: Long,
    val bounceable: Boolean,
)

/**
 * `program_id` / `compute_limit` are the field names in `SolanaSpecific` (commondata
 * `blockchain_specific.proto`); this corpus historically spelled them `has_program_id` /
 * `priority_limit`. Both spellings are accepted so a fixture can be copied verbatim between repos
 * without a field silently deserializing to its default — which would change the signed message
 * while the test still passed.
 *
 * Note this is a defensive accommodation, not proof of cross-platform correctness: iOS's own
 * `KeysignPayloadCodable` only ever decodes `priority_limit`, so its `solana-sign-data.json`
 * fixture — which spells the field `compute_limit` — silently loses that value on iOS too.
 * Accepting `compute_limit` here means Android won't drop it the way iOS does, not that the value
 * is verified identical across platforms.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SolanaSpecific(
    @SerialName("recent_block_hash") val recentBlockHash: String,
    @SerialName("priority_fee") val priorityFee: String,
    @SerialName("has_program_id") @JsonNames("program_id") val hasProgramId: Boolean? = false,
    @SerialName("from_token_associated_address") val fromAddressPubKey: String? = null,
    @SerialName("to_token_associated_address") val toAddressPubKey: String? = null,
    @SerialName("priority_limit") @JsonNames("compute_limit") val priorityLimit: String? = null,
)

@Serializable
data class PolkadotSpecific(
    @SerialName("recent_block_hash") val recentBlockHash: String,
    val nonce: Long,
    @SerialName("current_block_number") val currentBlockNumber: String,
    @SerialName("spec_version") val specVersion: Int,
    @SerialName("transaction_version") val transactionVersion: Int,
    @SerialName("genesis_hash") val genesisHash: String,
    @SerialName("gas") val gas: Long,
)

@Serializable
data class ThorchainSpecific(
    @SerialName("account_number") val accountNumber: Long,
    val sequence: Long,
    val fee: Long,
    @SerialName("is_deposit") val isDeposit: Boolean,
    @SerialName("transaction_type") val transactionType: Int,
)

@Serializable
data class MayachainSpecific(
    @SerialName("account_number") val accountNumber: Long,
    @SerialName("sequence") val sequence: Long,
    @SerialName("is_deposit") val isDeposit: Boolean,
)

@Serializable
data class UtxoSpecific(
    @SerialName("byte_fee") val byteFee: String,
    @SerialName("send_max_amount") val sendMaxAmount: Boolean = false,
    @SerialName("zcash_branch_id") val zcashBranchId: String? = null,
)

@Serializable
data class IbcDenomTrace(
    @SerialName("base_denom") val baseDenom: String,
    val path: String,
    val height: String,
)

@Serializable data class UtxoInfo(val hash: String, val index: Long, val amount: Long)

@Serializable
data class SignData(
    @SerialName("sign_solana") val signSolana: SignSolana? = null,
    @SerialName("sign_ton") val signTon: SignTon? = null,
    @SerialName("sign_direct") val signDirect: SignDirect? = null,
    @SerialName("sign_amino") val signAmino: SignAmino? = null,
)

@Serializable
data class SignAmino(
    @SerialName("fee") val fee: Fee? = null,
    @SerialName("msgs") val msgs: List<Msgs> = emptyList(),
)

@Serializable
data class SignSolana(
    @SerialName("raw_transactions") val rawTransactions: List<String> = emptyList()
)

@Serializable
data class Fee(
    @SerialName("amount") val amount: List<WasmExecuteContractPayload.CosmosCoin> = emptyList(),
    @SerialName("gas") val gas: String = "",
)

@Serializable
data class Msgs(@SerialName("type") val type: String = "", @SerialName("value") val value: Value)

@Serializable
data class Value(
    @SerialName("amount") val amount: List<WasmExecuteContractPayload.CosmosCoin?> = emptyList(),
    @SerialName("from_address") val fromAddress: String = "",
    @SerialName("to_address") val toAddress: String = "",
)

@Serializable
data class SuiCoin(
    @SerialName("coin_type") val coinType: String,
    @SerialName("coin_object_id") val coinObjectId: String,
    @SerialName("version") val version: String,
    @SerialName("digest") val digest: String,
    @SerialName("balance") val balance: String,
    @SerialName("previous_transaction") val previousTransaction: String? = null,
)

@Serializable
data class SuiSpecific(
    @SerialName("reference_gas_price") val referenceGasPrice: String,
    @SerialName("gas_budget") val gasBudget: String,
    @SerialName("coins") val coins: List<SuiCoin>,
)

/** Cardano blockchain-specific parameters for test fixtures. */
@Serializable
data class CardanoSpecific(
    /** Fee per byte in lovelace. */
    @SerialName("byte_fee") val byteFee: Long,
    /** Whether to send the maximum available amount. */
    @SerialName("send_max_amount") val sendMaxAmount: Boolean = false,
    /** Transaction time-to-live (slot number). */
    val ttl: Long,
)

@Serializable
data class TronSpecific(
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("expiration") val expiration: Long,
    @SerialName("block_header_timestamp") val blockHeaderTimestamp: Long,
    @SerialName("block_header_number") val blockHeaderNumber: Long,
    @SerialName("block_header_version") val blockHeaderVersion: Long,
    @SerialName("block_header_tx_trie_root") val blockHeaderTxTrieRoot: String,
    @SerialName("block_header_parent_hash") val blockHeaderParentHash: String,
    @SerialName("block_header_witness_address") val blockHeaderWitnessAddress: String,
    @SerialName("gas_estimation") val gasFeeEstimation: Long,
)

@Serializable
data class OneinchSwapPayload(
    @SerialName("from_coin") val fromCoin: Coin,
    @SerialName("to_coin") val toCoin: Coin,
    @SerialName("from_amount") val fromAmount: String,
    @SerialName("to_amount_decimal") val toAmountDecimal: String,
    @SerialName("to_amount_limit") val toAmountLimit: String = "0",
    @SerialName("quote") val quote: OneinchQuote,
    @SerialName("provider") val provider: String = "",
)

@Serializable
data class OneinchQuote(
    @SerialName("dst_amount") val dstAmount: String,
    @SerialName("tx") val tx: OneinchTransaction,
)

@Serializable
data class OneinchTransaction(
    @SerialName("data") val data: String,
    @SerialName("from") val from: String,
    @SerialName("gas") val gas: Long,
    @SerialName("gas_price") val gasPrice: String,
    @SerialName("to") val to: String,
    @SerialName("value") val value: String,
)
