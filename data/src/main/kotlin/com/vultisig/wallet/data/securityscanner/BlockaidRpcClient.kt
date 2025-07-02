package com.vultisig.wallet.data.securityscanner


import com.vultisig.wallet.data.models.Chain
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import javax.inject.Inject

class BlockaidRpcClient @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
) : BlockaidRpcClientContract {

    override suspend fun scanBitcoinTransaction(address: String, serializedTransaction: String) {
        val bitcoinRequest = BitcoinScanTransactionRequest(
            chain = Chain.Bitcoin.toName(),
            metadata = CommonMetadata(
                url = "https://vultisig.com/",
            ),
            options = listOf("validation"),
            accountAddress = address,
            transaction = serializedTransaction,
        )

        httpClient.post("$BLOCKAID_URL/bitcoin/transaction-raw/scan") {
            contentType(ContentType.Application.Json)
            setBody(bitcoinRequest)
        }
    }

    override suspend fun scanEVMTransaction(
        chain: Chain,
        from: String,
        to: String,
        amount: String,
        data: String
    ) {
        val evmRequest = EthereumScanTransactionRequest(
            chain = chain.toName(),
            metadata = EthereumScanTransactionRequest.Metadata(
                domain = "",
            ),
            options = listOf("validation"),
            accountAddress = from,
            simulatedWithEstimatedGas = false,
            data = EthereumScanTransactionRequest.Data(
                from = from,
                to = to,
                data = data,
                value = amount,
            ),
        )
        httpClient.post("$BLOCKAID_URL/evm/json-rpc/scan") {
            contentType(ContentType.Application.Json)
            setBody(evmRequest)
        }
    }

    override suspend fun scanSolanaTransaction(address: String, serializedMessage: String) {
        val solanaRequest = SolanaScanTransactionRequest(
            chain = Chain.Bitcoin.toName(),
            metadata = CommonMetadata(
                url = "https://vultisig.com/",
            ),
            options = listOf("validation"),
            accountAddress = address,
            encoding = "base64", // to check
            transactions = listOf(serializedMessage),
        )
        httpClient.post("$BLOCKAID_URL/solana/address/scan") {
            contentType(ContentType.Application.Json)
            setBody(solanaRequest)
        }
    }

    override suspend fun scanSuiTransaction(address: String, serializedTransaction: String) {
        val suiRequest = SuiScanTransactionRequest(
            chain = Chain.Bitcoin.toName(),
            metadata = CommonMetadata(
                url = "https://vultisig.com/",
            ),
            options = listOf("validation"),
            accountAddress = address,
            transaction = serializedTransaction,
        )

        httpClient.post("$BLOCKAID_URL/sui/transaction/scan") {
            contentType(ContentType.Application.Json)
            setBody(suiRequest)
        }
    }

    private companion object {
        private const val BLOCKAID_URL = "https://api.blockaid.io/v0"

        private fun Chain.toName(): String {
            return when (this) {
                Chain.Arbitrum -> "arbitrum"
                Chain.Avalanche -> "avalanche"
                Chain.Base -> "base"
                Chain.Blast -> "blast"
                Chain.BscChain -> "bsc"
                Chain.Bitcoin -> "bitcoin"
                Chain.Ethereum -> "ethereum"
                Chain.Optimism -> "optimism"
                Chain.Polygon -> "polygon"
                Chain.Sui -> "sui"
                Chain.Solana -> "solana"
                else -> error("Chain: ${this.name} not supported by Blockaid")
            }
        }
    }
}