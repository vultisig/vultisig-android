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
    val utxoInfo: List<String>? = null,
    @SerialName("SwapPayload")
    val swapPayload: String? = null,
    @SerialName("vault_public_key_ecdsa")
    val vaultPublicKeyEcdsa: String,
    @SerialName("lib_type")
    val libType: String
)

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
    val cosmosSpecific: CosmosSpecific? = null
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
data class IbcDenomTrace(
    @SerialName("base_denom")
    val baseDenom: String,
    val path: String,
    val height: String
)