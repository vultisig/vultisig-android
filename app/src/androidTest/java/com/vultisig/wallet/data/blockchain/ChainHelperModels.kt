import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class TransactionData(
    val name: String,
    @SerialName("keysign_payload")
    val keysignPayload: KeysignPayload,
    @SerialName("expected_image_hash")
    val expectedImageHash: List<String>
)

@Serializable
data class KeysignPayload(
    val coin: Coin,
    @SerialName("to_address")
    val toAddress: String,
    @SerialName("to_amount")
    val toAmount: String,
    @SerialName("BlockchainSpecific")
    val blockchainSpecific: BlockchainSpecific,
    @SerialName("utxo_info")
    val utxoInfo: List<UtxoInfo>? = null,
    @SerialName("vault_public_key_ecdsa")
    val vaultPublicKeyEcdsa: String,
    @SerialName("lib_type")
    val libType: String,
    @SerialName("memo")
    val memo: String? = null,
    @SerialName("wasm_execute_contract_payload")
    val wasmExecuteContractPayload: WasmExecuteContractPayload? = null,
    @SerialName("SwapPayload")
    val swapPayload: SwapPayload? = null,
    @SerialName("erc20_approve_payload")
    var approvePayload: ERC20ApprovePayload? = null,
    @SerialName("trigger_smart_contract_payload")
    var triggerSmartContractPayload: TriggerSmartContractPayload? = null,
)

@Serializable
data class TriggerSmartContractPayload(
    val ownerAddress: String,
    val contractAddress: String,
    val callValue: String? = null,
    val callDataValue: String? = null,
    val data: String,
)

@Serializable
data class ERC20ApprovePayload(
    @SerialName("spender")
    val spender: String,
    @SerialName("amount")
    val amount: String,
)

@Serializable
data class SwapPayload(
    @SerialName("ThorchainSwapPayload")
    val thorchainSwapPayload: ThorchainSwapPayload? = null,
    @SerialName("MayachainSwapPayload")
    val mayachainSwapPayload: ThorchainSwapPayload? = null,
    @SerialName("OneinchSwapPayload")
    val oneinchSwapPayload: OneinchSwapPayload? = null,
)

@Serializable
data class ThorchainSwapPayload(
    @SerialName("from_address")
    val fromAddress: String,
    @SerialName("from_coin")
    val fromCoin: Coin,
    @SerialName("to_coin")
    val toCoin: Coin,
    @SerialName("vault_address")
    val vaultAddress: String,
    @SerialName("from_amount")
    val fromAmount: String,
    @SerialName("to_amount_decimal")
    val toAmountDecimal: String,
    @SerialName("to_amount_limit")
    val toAmountLimit: String,
    @SerialName("streaming_interval")
    val streamingInterval: String,
    @SerialName("streaming_quantity")
    val streamingQuantity: String,
    @SerialName("is_affiliate")
    val isAffiliate: Boolean,
    @SerialName("fee")
    val fee: String,
    @SerialName("expiration_time")
    val expirationTime: Int,
    @SerialName("router_address")
    val routerAddress: String = "",
)

@Serializable
data class WasmExecuteContractPayload(
    @SerialName("sender_address")
    val senderAddress: String,
    @SerialName("contract_address")
    val contractAddress: String,
    @SerialName("execute_msg")
    val executeMsg: String,
    @SerialName("coins")
    val coins: List<CosmosCoin>
) {
    @Serializable
    data class CosmosCoin(
        @SerialName("denom")
        val denom: String,
        @SerialName("amount")
        val amount: String,
    )
}

@Serializable
data class Coin(
    val chain: String,
    val ticker: String,
    val address: String,
    val decimals: Int,
    @SerialName("price_provider_id")
    val priceProviderId: String,
    @SerialName("is_native_token")
    val isNativeToken: Boolean,
    @SerialName("contract_address")
    val contractAddress: String? = null,
    @SerialName("hex_public_key")
    val hexPublicKey: String,
    val logo: String
)

@Serializable
data class BlockchainSpecific(
    @SerialName("EthereumSpecific")
    val ethereumSpecific: EthereumSpecific? = null,
    @SerialName("CosmosSpecific")
    val cosmosSpecific: CosmosSpecific? = null,
    @SerialName("RippleSpecific")
    val rippleSpecific: RippleSpecific? = null,
    @SerialName("TonSpecific")
    val tonSpecific: TonSpecific? = null,
    @SerialName("SolanaSpecific")
    val solanaSpecific: SolanaSpecific? = null,
    @SerialName("ThorchainSpecific")
    val thorchainSpecific: ThorchainSpecific? = null,
    @SerialName("UtxoSpecific")
    val utxoSpecific: UtxoSpecific? = null,
    @SerialName("PolkadotSpecific")
    val polkadotSpecific: PolkadotSpecific? = null,
    @SerialName("SuicheSpecific")
    val suiSpecific: SuiSpecific? = null,
    @SerialName("MayaSpecific")
    val mayachainSpecific: MayachainSpecific? = null,
    @SerialName("TronSpecific")
    val tronSpecific: TronSpecific? = null,
)

@Serializable
data class EthereumSpecific(
    @SerialName("max_fee_per_gas_wei")
    val maxFeePerGasWei: String,
    @SerialName("priority_fee")
    val priorityFee: String,
    val nonce: Int,
    @SerialName("gas_limit")
    val gasLimit: String
)

@Serializable
data class CosmosSpecific(
    @SerialName("account_number")
    val accountNumber: Long,
    val gas: Long,
    val sequence: Int,
    @SerialName("transaction_type")
    val transactionType: Int,
    @SerialName("ibc_denom_trace")
    val ibcDenomTrace: IbcDenomTrace? = null
)

@Serializable
data class RippleSpecific(
    val sequence: Long,
    val gas: Long,
    @SerialName("last_ledger_sequence")
    val lastLedgerSequence: Long,
)

@Serializable
data class TonSpecific(
    @SerialName("jettons_address")
    val jettonsAddress: String = "",
    @SerialName("is_active")
    val activeDestination: Boolean = false,
    @SerialName("send_max_amount")
    val sendMaxAmount: Boolean,
    @SerialName("sequence_number")
    val sequenceNumber: Long,
    @SerialName("expire_at")
    val expireAt: Long,
    val bounceable: Boolean,
)

@Serializable
data class SolanaSpecific(
    @SerialName("recent_block_hash")
    val recentBlockHash: String,
    @SerialName("priority_fee")
    val priorityFee: String,
    @SerialName("has_program_id")
    val hasProgramId: Boolean,
    @SerialName("from_token_associated_address")
    val fromAddressPubKey: String? = null,
    @SerialName("to_token_associated_address")
    val toAddressPubKey: String? = null,
    @SerialName("priority_limit")
    val priorityLimit: String? = null,
)

@Serializable
data class PolkadotSpecific(
    @SerialName("recent_block_hash")
    val recentBlockHash: String,
    val nonce: Long,
    @SerialName("current_block_number")
    val currentBlockNumber: String,
    @SerialName("spec_version")
    val specVersion: Int,
    @SerialName("transaction_version")
    val transactionVersion: Int,
    @SerialName("genesis_hash")
    val genesisHash: String,
    @SerialName("gas")
    val gas: Long,
)

@Serializable
data class ThorchainSpecific(
    @SerialName("account_number")
    val accountNumber: Long,
    val sequence: Long,
    val fee: Long,
    @SerialName("is_deposit")
    val isDeposit: Boolean,
    @SerialName("transaction_type")
    val transactionType: Int,
)

@Serializable
data class MayachainSpecific(
    @SerialName("account_number")
    val accountNumber: Long,
    @SerialName("sequence")
    val sequence: Long,
    @SerialName("is_deposit")
    val isDeposit: Boolean,
)


@Serializable
data class UtxoSpecific(
    @SerialName("byte_fee")
    val byteFee: String,
    @SerialName("send_max_amount")
    val sendMaxAmount: Boolean = false,
)

@Serializable
data class IbcDenomTrace(
    @SerialName("base_denom")
    val baseDenom: String,
    val path: String,
    val height: String
)

@Serializable
data class UtxoInfo(
    val hash: String,
    val index: Long,
    val amount: Long,
)

@Serializable
data class SuiCoin(
    @SerialName("coin_type")
    val coinType: String,
    @SerialName("coin_object_id")
    val coinObjectId: String,
    @SerialName("version")
    val version: String,
    @SerialName("digest")
    val digest: String,
    @SerialName("balance")
    val balance: String,
    @SerialName("previous_transaction")
    val previousTransaction: String? = null
)

@Serializable
data class SuiSpecific(
    @SerialName("reference_gas_price")
    val referenceGasPrice: String,
    @SerialName("gas_budget")
    val gasBudget: String,
    @SerialName("coins")
    val coins: List<SuiCoin>
)

@Serializable
data class TronSpecific(
    @SerialName("timestamp")
    val timestamp: Long,
    @SerialName("expiration")
    val expiration: Long,
    @SerialName("block_header_timestamp")
    val blockHeaderTimestamp: Long,
    @SerialName("block_header_number")
    val blockHeaderNumber: Long,
    @SerialName("block_header_version")
    val blockHeaderVersion: Long,
    @SerialName("block_header_tx_trie_root")
    val blockHeaderTxTrieRoot: String,
    @SerialName("block_header_parent_hash")
    val blockHeaderParentHash: String,
    @SerialName("block_header_witness_address")
    val blockHeaderWitnessAddress: String,
    @SerialName("gas_estimation")
    val gasFeeEstimation: Long
)
@Serializable
data class OneinchSwapPayload(
    @SerialName("from_coin")
    val fromCoin: Coin,
    @SerialName("to_coin")
    val toCoin: Coin,
    @SerialName("from_amount")
    val fromAmount: String,
    @SerialName("to_amount_decimal")
    val toAmountDecimal: String,
    @SerialName("to_amount_limit")
    val toAmountLimit: String = "0",
    @SerialName("quote")
    val quote: OneinchQuote,
    @SerialName("provider")
    val provider: String = "",
)
@Serializable
data class OneinchQuote(
    @SerialName("dst_amount")
    val dstAmount: String,
    @SerialName("tx")
    val tx: OneinchTransaction
)
@Serializable
data class OneinchTransaction(
    @SerialName("data")
    val data: String,
    @SerialName("from")
    val from: String,
    @SerialName("gas")
    val gas: Long,
    @SerialName("gas_price")
    val gasPrice: String,
    @SerialName("to")
    val to: String,
    @SerialName("value")
    val value: String,
)