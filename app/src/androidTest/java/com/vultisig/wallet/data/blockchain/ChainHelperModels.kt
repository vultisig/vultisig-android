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
    @SerialName("SwapPayload")
    val swapPayload: String? = null,
    @SerialName("vault_public_key_ecdsa")
    val vaultPublicKeyEcdsa: String,
    @SerialName("lib_type")
    val libType: String,
    @SerialName("memo")
    val memo: String? = null,
    @SerialName("wasm_execute_contract_payload")
    val wasmExecuteContractPayload: WasmExecuteContractPayload? = null,
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